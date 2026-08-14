package com.notesescape.sdocx.export

import com.notesescape.sdocx.core.*

data class NoteReport(val title: String, val sourceFilename: String, val status: ParseStatus, val pageCount: Int, val textBlocks: Int, val images: Int, val handwritingPages: Int, val attachments: Int, val warnings: List<String>)
object MigrationReport {
    fun markdown(reports: List<NoteReport>): String = buildString { appendLine("# Migration report"); appendLine(); appendLine("${reports.size} notes selected"); appendLine("${reports.count { it.status == ParseStatus.SUCCESS }} complete"); appendLine("${reports.count { it.status == ParseStatus.PARTIAL }} partial"); appendLine("${reports.count { it.status == ParseStatus.LOCKED }} locked"); appendLine("${reports.count { it.status == ParseStatus.CORRUPT || it.status == ParseStatus.FAILED }} failed"); appendLine(); reports.forEach { appendLine("## ${it.title}"); appendLine("- Source: ${it.sourceFilename}"); appendLine("- Status: ${it.status}"); appendLine("- Pages: ${it.pageCount}; text blocks: ${it.textBlocks}; images: ${it.images}; handwriting pages: ${it.handwritingPages}; attachments: ${it.attachments}"); it.warnings.forEach { w -> appendLine("- Warning: $w") }; appendLine() } }
    fun json(reports: List<NoteReport>): String = "[" + reports.joinToString(",\n") { "{\"title\":\"${escape(it.title)}\",\"source_filename\":\"${escape(it.sourceFilename)}\",\"status\":\"${it.status}\",\"page_count\":${it.pageCount},\"text_blocks\":${it.textBlocks},\"images\":${it.images},\"handwriting_pages\":${it.handwritingPages},\"attachments\":${it.attachments},\"warnings\":[${it.warnings.joinToString(",") { w -> "\"${escape(w)}\"" }}]}" } + "]"
    private fun escape(s: String) = s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
}
