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
