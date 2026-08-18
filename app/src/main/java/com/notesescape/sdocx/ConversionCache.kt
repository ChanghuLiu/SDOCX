package com.notesescape.sdocx

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import com.notesescape.sdocx.export.ConversionSource
import com.notesescape.sdocx.export.SourceLocation
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.util.concurrent.CancellationException

private const val CACHE_DIRECTORY = "sdocx-conversion"
private const val COPY_BUFFER_BYTES = 64 * 1024
private const val MAX_SOURCE_BYTES = 512L * 1024 * 1024

/** Reopenable, one-note-at-a-time SAF source backed by a bounded cache file. */
class CachedSafSource(
    private val resolver: ContentResolver,
    private val uri: Uri,
    override val displayName: String,
    private val cacheRoot: File,
    private val cancelled: () -> Boolean = { false },
    private val relativeDirectory: List<String> = emptyList()
) : ConversionSource {
    override val location get() = SourceLocation(relativeDirectory, displayName)
    private var cachedFile: File? = null

    override fun openStream(): InputStream {
        val file = cachedFile ?: copyToCache().also { cachedFile = it }
        return BufferedInputStream(FileInputStream(file), COPY_BUFFER_BYTES)
    }

    private fun copyToCache(): File {
        if (cancelled()) throw CancellationException("conversion cancelled")
        cacheRoot.mkdirs()
        val destination = File.createTempFile("source-", ".sdocx", cacheRoot)
        try {
            val input = resolver.openInputStream(uri) ?: error("Unable to open source")
            BufferedInputStream(input, COPY_BUFFER_BYTES).use { source ->
                BufferedOutputStream(FileOutputStream(destination), COPY_BUFFER_BYTES).use { target ->
                    val buffer = ByteArray(COPY_BUFFER_BYTES)
                    var total = 0L
                    while (true) {
                        if (cancelled()) throw CancellationException("conversion cancelled")
                        val count = source.read(buffer)
                        if (count < 0) break
                        total += count
                        if (total > MAX_SOURCE_BYTES) error("Source file exceeds the safe import limit")
                        target.write(buffer, 0, count)
                    }
                }
            }
            return destination
        } catch (error: Throwable) {
            destination.delete()
            throw error
        }
    }

    override fun close() {
        cachedFile?.delete()
        cachedFile = null
    }
}

fun conversionCacheDirectory(context: Context): File = File(context.cacheDir, CACHE_DIRECTORY)

fun cleanConversionCache(context: Context) {
    val directory = conversionCacheDirectory(context)
    directory.listFiles()?.forEach { it.deleteRecursively() }
    directory.mkdirs()
}
