package com.notesescape.sdocx.export

data class ExportOptions(
    val preset: ExportFormat = ExportFormat.PORTABLE_MARKDOWN,
    val includeAttachments: Boolean = true,
    val preserveHandwriting: Boolean = true,
    val includeOriginals: Boolean = true,
    val includeMetadata: Boolean = true
)

data class ArchiveSummary(
    val notesConverted: Int,
    val foldersPreserved: Int,
    val imagesMediaPreserved: Int,
    val handwritingPagesPreserved: Int,
    val attachmentsPreserved: Int,
    val partial: Int,
    val failed: Int
)
