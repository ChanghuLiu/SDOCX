package com.notesescape.sdocx.export

import com.notesescape.sdocx.core.ParseStatus

data class NoteReport(
    val title: String,
    val sourceFilename: String,
    val sourceRelativePath: String,
    val outputNotePath: String,
    val attachmentDirectory: String?,
    val preset: ExportFormat,
    val status: ParseStatus,
    val pageCount: Int,
    val textBlocks: Int,
    val images: Int,
    val handwritingPages: Int,
    val attachments: Int,
    val warnings: List<String>
)

object MigrationReport {
    fun markdown(reports: List<NoteReport>, preset: ExportFormat = ExportFormat.PORTABLE_MARKDOWN): String = buildString {
        appendLine("# Migration report")
        appendLine()
        appendLine("Preset: ${if (preset.isObsidian) "OBSIDIAN_VAULT" else "PORTABLE_MARKDOWN"}")
        appendLine("${reports.size} notes selected")
        appendLine("Folders preserved: ${reports.count { it.sourceRelativePath.contains('/') && it.preset.isObsidian }}")
        appendLine("Folders flattened: ${reports.count { it.sourceRelativePath == it.sourceFilename }}")
        appendLine("Filename collisions: ${reports.count { it.outputNotePath.contains(" (") }}")
        appendLine("${reports.count { it.status == ParseStatus.PARTIAL }} partial notes")
        appendLine("${reports.count { it.status == ParseStatus.CORRUPT || it.status == ParseStatus.FAILED }} failed notes")
        appendLine()
        reports.forEach {
            appendLine("## ${it.title}")
            appendLine("- Source: ${it.sourceRelativePath}")
            appendLine("- Output: ${it.outputNotePath}")
            appendLine("- Status: ${it.status}")
            appendLine("- Pages: ${it.pageCount}; text blocks: ${it.textBlocks}; images: ${it.images}; handwriting pages: ${it.handwritingPages}; attachments: ${it.attachments}")
            it.warnings.forEach { warning -> appendLine("- Warning: $warning") }
            appendLine()
        }
    }

    fun json(reports: List<NoteReport>): String = reports.joinToString(",\n", "[", "]") { report ->
        """{"title":"${escape(report.title)}","sourceRelativePath":"${escape(report.sourceRelativePath)}","outputNotePath":"${escape(report.outputNotePath)}","attachmentDirectory":${report.attachmentDirectory?.let { "\"${escape(it)}\"" } ?: "null"},"preset":"${if (report.preset.isObsidian) "OBSIDIAN_VAULT" else "PORTABLE_MARKDOWN"}","status":"${report.status}","warnings":[${report.warnings.joinToString(",") { "\"${escape(it)}\"" }}]}"""
    }

    private fun escape(value: String): String = value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r")
}
