package com.notesescape.sdocx.export

import com.notesescape.sdocx.core.*
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** A reopenable source. Implementations may back this with a cache file rather than RAM. */
interface ConversionSource : AutoCloseable {
    val displayName: String
    val location: SourceLocation get() = SourceLocation(filename = displayName)
    fun openStream(): InputStream
    override fun close() = Unit
}

/** In-memory source retained for JVM tests and small programmatic conversions. */
data class SourceNote(val filename: String, val bytes: ByteArray, val relativeDirectory: List<String> = emptyList()) : ConversionSource {
    override val displayName: String get() = filename
    override val location: SourceLocation get() = SourceLocation(relativeDirectory, filename)
    override fun openStream(): InputStream = ByteArrayInputStream(bytes)
}

data class ExportedArchive(val reports: List<NoteReport>)
object MediaType {
    fun detect(filename: String, bytes: ByteArray): String? = when {
        bytes.startsWith(byteArrayOf(0xff.toByte(), 0xd8.toByte(), 0xff.toByte())) -> "image/jpeg"
        bytes.startsWith(byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47)) -> "image/png"
        bytes.startsWith("RIFF".toByteArray()) && bytes.size > 11 && String(bytes, 8, 4, Charsets.US_ASCII) == "WEBP" -> "image/webp"
        bytes.startsWith("%PDF".toByteArray()) -> "application/pdf"
        bytes.startsWith("ftyp".toByteArray(), 4) -> "video/mp4"
        filename.substringAfterLast('.', "").lowercase() in setOf("jpg", "jpeg") -> "image/jpeg"
        filename.substringAfterLast('.', "").lowercase() == "png" -> "image/png"
        filename.substringAfterLast('.', "").lowercase() == "webp" -> "image/webp"
        filename.substringAfterLast('.', "").lowercase() == "pdf" -> "application/pdf"
        filename.substringAfterLast('.', "").lowercase() in setOf("m4a", "mp3", "wav", "mp4") -> "application/octet-stream"
        else -> null
    }
    fun detect(filename: String, input: InputStream): String? {
        val prefix = ByteArray(16)
        var length = 0
        while (length < prefix.size) {
            val count = input.read(prefix, length, prefix.size - length)
            if (count < 0) break
            length += count
        }
        return detect(filename, prefix.copyOf(length))
    }
    private fun ByteArray.startsWith(prefix: ByteArray, offset: Int = 0): Boolean = offset >= 0 && size >= offset + prefix.size && copyOfRange(offset, offset + prefix.size).contentEquals(prefix)
}
object ArchiveExporter {
    fun export(
        sources: Sequence<ConversionSource>,
        output: OutputStream,
        format: ExportFormat = ExportFormat.PORTABLE_MARKDOWN,
        includeAttachments: Boolean = true,
        includeOriginals: Boolean = true,
        preserveHandwriting: Boolean = true,
        onProgress: (index: Int, total: Int?, report: NoteReport) -> Unit = { _, _, _ -> }
    ): ExportedArchive {
        val reports = mutableListOf<NoteReport>(); val usedNotes = mutableSetOf<String>(); val usedEntries = mutableSetOf<String>()
        ZipOutputStream(output).use { zip -> sources.forEachIndexed { index, source ->
            val resultAndWarnings = try {
                source.openStream().use(SdocxParser::parse) to emptyList<String>()
            } catch (error: java.util.concurrent.CancellationException) {
                throw error
            } catch (error: Exception) {
                ParseResult(DocumentMetadata(title = SafeNames.file(source.displayName)), emptyList(), emptyList(), listOf(ParseWarning(error.message ?: "Unable to read source")), ParseStatus.FAILED) to listOf(error.message ?: "Unable to read source")
            }
            val result = resultAndWarnings.first
            val extraWarnings = resultAndWarnings.second.toMutableList()
            val sourceLocation = PathSanitizer.location(source.location)
            val root = if (format.isObsidian) "Notes" else "notes"
            val relativeDirs = if (format.isObsidian) sourceLocation.relativeDirectory else emptyList()
            val noteParent = (listOf(root) + relativeDirs).joinToString("/")
            val noteName = SafeNames.unique(SafeNames.file(result.metadata.title) + ".md", usedNotes, noteParent)
            val notePath = "$noteParent/$noteName"
            val attachmentDirectory = if (format.isObsidian) (listOf("Attachments") + relativeDirs + noteName.removeSuffix(".md")).joinToString("/") else null
            put(zip, usedEntries, notePath, MarkdownExporter.render(result, format, attachmentDirectory).toByteArray())
            try {
                if (includeAttachments) result.media.forEachIndexed { mediaIndex, media ->
                    val dir = SafeNames.file(noteName.removeSuffix(".md"))
                    val filename = SafeNames.file(media.filename, "asset_$mediaIndex")
                    val kind = media.openStream().use { MediaType.detect(filename, it) }
                    val folder = attachmentDirectory ?: if (kind == null) "assets/attachments" else "assets/$dir"
                    media.openStream().use { input -> put(zip, usedEntries, "$folder/${SafeNames.uniquePath(filename, usedEntries, folder)}", input) }
                }
            } finally {
                result.media.forEach { it.close() }
            }
            if (preserveHandwriting) result.pages.forEach { page ->
                val handwriting = page.elements.filterIsInstance<HandwritingElement>()
                if (handwriting.isNotEmpty()) {
                    val merged = handwriting.first().copy(
                        strokes = handwriting.flatMap { it.strokes },
                        width = handwriting.maxOf { it.width },
                        height = handwriting.maxOf { it.height }
                    )
                    val folder = attachmentDirectory ?: "assets/${SafeNames.file(result.metadata.title)}"
                    put(zip, usedEntries, "$folder/handwriting_page_${page.number.toString().padStart(2, '0')}.svg", MarkdownExporter.svg(merged).toByteArray())
                }
            }
            if (includeOriginals && result.status != ParseStatus.FAILED) runCatching {
                source.openStream().use { input -> put(zip, usedEntries, "originals/${SafeNames.file(source.displayName)}", input) }
            }.onFailure { extraWarnings += it.message ?: "Unable to preserve original" }
            val noteReport = report(sourceLocation, notePath, attachmentDirectory, format, result, extraWarnings)
            reports += noteReport
            onProgress(index + 1, null, noteReport)
            runCatching { source.close() }
        }; val reportRoot = if (format.isObsidian) "_Notes Escape/" else ""; put(zip, usedEntries, "${reportRoot}migration-report.md", MigrationReport.markdown(reports, format).toByteArray()); put(zip, usedEntries, "${reportRoot}migration-report.json", MigrationReport.json(reports).toByteArray()) }
        return ExportedArchive(reports)
    }
    private fun put(zip: ZipOutputStream, used: MutableSet<String>, path: String, bytes: ByteArray) { val safe = path.replace('\\', '/'); require(!safe.startsWith('/') && safe.split('/').none { it == ".." }); if (!used.add(safe)) return; zip.putNextEntry(ZipEntry(safe)); zip.write(bytes); zip.closeEntry() }
    private fun put(zip: ZipOutputStream, used: MutableSet<String>, path: String, input: InputStream) { val safe = path.replace('\\', '/'); require(!safe.startsWith('/') && safe.split('/').none { it == ".." }); if (!used.add(safe)) return; zip.putNextEntry(ZipEntry(safe)); val buffer = ByteArray(64 * 1024); while (true) { val read = input.read(buffer); if (read < 0) break; zip.write(buffer, 0, read) }; zip.closeEntry() }
    fun report(location: SourceLocation, outputNotePath: String, attachmentDirectory: String?, preset: ExportFormat, result: ParseResult, extraWarnings: List<String> = emptyList()): NoteReport {
        val status = if (result.status == ParseStatus.SUCCESS && extraWarnings.isNotEmpty()) ParseStatus.PARTIAL else result.status
        return NoteReport(
            result.metadata.title, location.filename, location.relativePath, outputNotePath, attachmentDirectory, preset,
            status,
            result.pages.size,
            result.topLevelElements.size + result.pages.sumOf { p -> p.elements.count { it is RichTextElement } },
            result.pages.sumOf { p -> p.elements.count { it is ImageElement } } + result.media.count { isImageFilename(it.filename) },
            result.pages.withIndex().flatMap { (index, page) -> page.elements.filterIsInstance<HandwritingElement>().map { index + 1 } }.toSet().size,
            result.pages.sumOf { p -> p.elements.count { it is AttachmentElement } } + result.media.count { !isImageFilename(it.filename) && (it.attachment || !isKnownMediaFilename(it.filename)) },
            result.warnings.map { it.message } + extraWarnings
        )
    }

    private fun isImageFilename(filename: String): Boolean = filename.substringAfterLast('.', "").lowercase() in setOf("jpg", "jpeg", "png", "webp")
    private fun isKnownMediaFilename(filename: String): Boolean = filename.substringAfterLast('.', "").lowercase() in setOf("jpg", "jpeg", "png", "webp", "pdf", "m4a", "mp3", "wav", "mp4")
}
