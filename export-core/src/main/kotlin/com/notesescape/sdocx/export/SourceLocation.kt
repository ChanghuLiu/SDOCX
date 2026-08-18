package com.notesescape.sdocx.export

/** Filesystem-relative provenance from SAF. URI strings are deliberately not part of this model. */
data class SourceLocation(val relativeDirectory: List<String> = emptyList(), val filename: String) {
    val relativePath: String get() = (relativeDirectory + filename).joinToString("/")
}

object PathSanitizer {
    private const val MAX_SEGMENT_LENGTH = 120
    private val control = Regex("[\\u0000-\\u001f\\u007f]")

    fun segment(value: String?, fallback: String = "Untitled"): String {
        val cleaned = (value ?: "").replace('\\', '_').replace('/', '_').replace(control, "_")
            .trim().trimEnd('.')
        if (cleaned == "." || cleaned == "..") return fallback
        return cleaned.ifBlank { fallback }.take(MAX_SEGMENT_LENGTH).trimEnd('.').ifBlank { fallback }
    }

    fun directory(values: List<String>): List<String> = values.map { segment(it, "Folder") }
    fun location(location: SourceLocation): SourceLocation = SourceLocation(directory(location.relativeDirectory), segment(location.filename, "Untitled.sdocx"))
}

data class FolderSummary(val noteCount: Int, val folderCount: Int, val topFolders: Map<String, Int>, val omittedFolderCount: Int)

object SourceSummary {
    fun folderSummary(locations: Iterable<SourceLocation>, maxRows: Int = 8): FolderSummary {
        val normalized = locations.map(PathSanitizer::location).toList()
        val paths = normalized.flatMap { location ->
            location.relativeDirectory.runningFold(emptyList<String>()) { acc, segment -> acc + segment }.drop(1).map { it.joinToString("/") }
        }.toSet()
        val top = normalized.filter { it.relativeDirectory.isNotEmpty() }.groupingBy { it.relativeDirectory.first() }.eachCount().toSortedMap()
        val rows = top.entries.take(maxRows).associate { it.key to it.value }
        return FolderSummary(normalized.size, paths.size, rows, (top.size - rows.size).coerceAtLeast(0))
    }
}
