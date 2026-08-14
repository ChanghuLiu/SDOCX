package com.notesescape.sdocx.export

import com.notesescape.sdocx.core.*
import java.io.ByteArrayInputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

data class SourceNote(val filename: String, val bytes: ByteArray)
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
    private fun ByteArray.startsWith(prefix: ByteArray, offset: Int = 0): Boolean = offset >= 0 && size >= offset + prefix.size && copyOfRange(offset, offset + prefix.size).contentEquals(prefix)
}
object ArchiveExporter {
    fun export(sources: Sequence<SourceNote>, output: OutputStream, format: ExportFormat = ExportFormat.CLEAN_MARKDOWN, includeAttachments: Boolean = true, includeOriginals: Boolean = true, preserveHandwriting: Boolean = true): ExportedArchive {
        val reports = mutableListOf<NoteReport>(); val usedNotes = mutableSetOf<String>(); val usedEntries = mutableSetOf<String>()
        ZipOutputStream(output).use { zip -> sources.forEach { source ->
            val result = ByteArrayInputStream(source.bytes).use(SdocxParser::parse); val noteName = SafeNames.unique(SafeNames.file(result.metadata.title) + ".md", usedNotes); put(zip, usedEntries, "notes/$noteName", MarkdownExporter.render(result, format).toByteArray())
            if (includeAttachments) result.media.forEachIndexed { index, media -> val dir = SafeNames.file(result.metadata.title); val filename = SafeNames.file(media.filename, "asset_$index"); val folder = if (MediaType.detect(filename, media.bytes) == null) "assets/attachments" else "assets/$dir"; put(zip, usedEntries, "$folder/${SafeNames.unique(filename, usedEntries)}", media.bytes) }
            if (preserveHandwriting) result.pages.forEach { page -> page.elements.filterIsInstance<HandwritingElement>().forEach { handwriting -> put(zip, usedEntries, "assets/${SafeNames.file(result.metadata.title)}/handwriting_page_${page.number.toString().padStart(2, '0')}.svg", MarkdownExporter.svg(handwriting).toByteArray()) } }
            if (includeOriginals) put(zip, usedEntries, "originals/${SafeNames.file(source.filename)}", source.bytes)
            reports += report(source.filename, result)
        }; put(zip, usedEntries, "migration-report.md", MigrationReport.markdown(reports).toByteArray()); put(zip, usedEntries, "migration-report.json", MigrationReport.json(reports).toByteArray()) }
        return ExportedArchive(reports)
    }
    private fun put(zip: ZipOutputStream, used: MutableSet<String>, path: String, bytes: ByteArray) { val safe = path.replace('\\', '/'); require(!safe.startsWith('/') && safe.split('/').none { it == ".." }); if (!used.add(safe)) return; zip.putNextEntry(ZipEntry(safe)); zip.write(bytes); zip.closeEntry() }
    fun report(source: String, result: ParseResult): NoteReport = NoteReport(result.metadata.title, source, result.status, result.pages.size, result.pages.sumOf { p -> p.elements.count { it is RichTextElement } }, result.pages.sumOf { p -> p.elements.count { it is ImageElement } }, result.pages.sumOf { p -> p.elements.count { it is HandwritingElement } }, result.pages.sumOf { p -> p.elements.count { it is AttachmentElement } }, result.warnings.map { it.message })
}
