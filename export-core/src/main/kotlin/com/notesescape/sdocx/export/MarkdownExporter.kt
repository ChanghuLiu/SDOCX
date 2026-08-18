package com.notesescape.sdocx.export

import com.notesescape.sdocx.core.*

enum class ExportFormat {
    PORTABLE_MARKDOWN, OBSIDIAN_VAULT,
    @Deprecated("Use PORTABLE_MARKDOWN") CLEAN_MARKDOWN,
    @Deprecated("Use OBSIDIAN_VAULT") OBSIDIAN_RICH;
    val isObsidian get() = this == OBSIDIAN_VAULT || this == OBSIDIAN_RICH
    val isPortable get() = !isObsidian
}
object SafeNames {
    fun file(value: String, fallback: String = "Untitled"): String = PathSanitizer.segment(value, fallback)
    fun unique(base: String, used: MutableSet<String>): String = unique(base, used, "")
    fun unique(base: String, used: MutableSet<String>, scope: String): String {
        val key = { name: String -> if (scope.isEmpty()) name else "$scope/$name" }
        val stem = base.substringBeforeLast('.', base); val ext = base.substringAfterLast('.', "")
        var result = base; var i = 2
        while (!used.add(key(result))) result = "$stem ($i)${if (ext.isEmpty()) "" else "." + ext}".also { i++ }
        return result
    }
    fun uniquePath(base: String, used: MutableSet<String>, scope: String): String = unique(base, used, scope)
}
object MarkdownExporter {
    fun render(result: ParseResult, format: ExportFormat = ExportFormat.PORTABLE_MARKDOWN, attachmentDirectory: String? = null, includeMetadata: Boolean = true): String = buildString {
        if (format.isObsidian && includeMetadata) {
            appendLine("---"); appendLine("title: \"${yaml(result.metadata.title)}\"")
            result.metadata.created?.takeUnless { it.isBlank() }?.let { appendLine("created: \"${yaml(it)}\"") }
            result.metadata.modified?.takeUnless { it.isBlank() }?.let { appendLine("modified: \"${yaml(it)}\"") }
            appendLine("source: samsung-notes"); appendLine("source_format: sdocx"); appendLine("migration_status: complete"); appendLine("---"); appendLine()
        } else if (format.isPortable) {
            // Retain the established Portable Markdown header for compatibility.
            appendLine("---"); appendLine("title: \"${yaml(result.metadata.title)}\"")
            result.metadata.created?.takeUnless { it.isBlank() }?.let { appendLine("created: \"${yaml(it)}\"") }
            result.metadata.modified?.takeUnless { it.isBlank() }?.let { appendLine("modified: \"${yaml(it)}\"") }
            appendLine("source: \"Samsung Notes\""); appendLine("source_format: \"sdocx\""); appendLine("direction: \"${result.metadata.direction}\""); appendLine("---"); appendLine()
        }
        val top = result.topLevelText?.trim().takeUnless { it.isNullOrBlank() }
        if (result.topLevelElements.isNotEmpty()) {
            result.topLevelElements.forEach { appendLine(formatText(it, format)) }
            appendLine()
        } else {
            top?.let { appendLine(it); appendLine() }
        }
        val emittedHandwritingPages = mutableSetOf<Int>()
        val representedMedia = mutableSetOf<String>()
        result.pages.forEach { page -> page.elements.sortedWith(compareBy<PageElement> { it.y }.thenBy { it.x }.thenBy { it.sourceOrder }).forEach { element ->
            if (element is RichTextElement && top != null && normalize(element.text) == normalize(top)) return@forEach
            when (element) {
                is RichTextElement -> appendLine(formatText(element, format))
                is ImageElement -> result.media.firstOrNull { it.bindId == element.bindId }?.let { media ->
                    representedMedia += media.bindId
                    appendLine(mediaMarkdown(result.metadata.title, media, format, attachmentDirectory))
                } ?: appendLine("<!-- Image attachment unavailable: ${SafeNames.file(element.bindId)} -->")
                is AttachmentElement -> result.media.firstOrNull { it.bindId == element.bindId }?.let { media ->
                    representedMedia += media.bindId
                    appendLine(mediaMarkdown(result.metadata.title, media, format, attachmentDirectory))
                } ?: appendLine("<!-- Attachment unavailable: ${SafeNames.file(element.bindId)} -->")
                is HandwritingElement -> if (emittedHandwritingPages.add(page.number)) appendLine(attachmentLink("${attachmentDirectory ?: "assets/${SafeNames.file(result.metadata.title)}"}/handwriting_page_${page.number.toString().padStart(2, '0')}.svg", format, media = true))
                is UnknownElement -> appendLine("<!-- Unsupported object ${element.kind}; see migration report -->")
            }
        }; appendLine() }
        result.media.filter { it.bindId !in representedMedia }.forEach { appendLine(mediaMarkdown(result.metadata.title, it, format, attachmentDirectory)) }
        if (result.media.any { it.bindId !in representedMedia }) appendLine()
    }
    private fun formatText(e: RichTextElement, format: ExportFormat): String {
        val content = if (e.spans.isEmpty()) escapePlain(e.text) else e.spans.joinToString("") { renderSpan(it, format) }
        val indent = "  ".repeat(e.paragraph.indent.coerceIn(0, 32))
        val listed = when (e.paragraph.listKind) {
            "bullet" -> "- "
            "number" -> "${e.paragraph.listNumber ?: 1}. "
            else -> ""
        }
        val checkbox = e.paragraph.checked?.let { if (it) "- [x] " else "- [ ] " } ?: listed
        return indent + checkbox + content
    }
    private fun renderSpan(span: RichTextSpan, format: ExportFormat): String {
        var value = escapePlain(span.text)
        if (span.bold) value = "**$value**"; if (span.italic) value = "*$value*"; if (span.strike) value = "~~$value~~"
        if (span.underline && format.isObsidian) value = "<u>$value</u>"
        return span.link?.let { "[${value.replace("]", "\\]")}](${escapeUrl(it)})" } ?: value
    }
    private fun escapePlain(value: String): String = value.replace("\\", "\\\\").replace("`", "\\`").replace("\r\n", "\n")
    private fun escapeUrl(value: String): String = value.replace("\\", "%5C").replace("(", "%28").replace(")", "%29").replace(" ", "%20")
    private fun yaml(value: String) = value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r")
    private fun normalize(value: String) = value.replace("\r\n", "\n").trim()
    private fun mediaMarkdown(title: String, media: MediaAsset, format: ExportFormat, attachmentDirectory: String?): String {
        val filename = SafeNames.file(media.filename)
        val kind = media.openStream().use { MediaType.detect(filename, it) }
        val folder = attachmentDirectory ?: if (kind == null) "assets/attachments" else "assets/${SafeNames.file(title)}"
        if (format.isObsidian && kind == null) return "<!-- Attachment unavailable: $filename -->"
        return attachmentLink("$folder/$filename", format, kind?.startsWith("image/") == true)
    }
    private fun attachmentLink(path: String, format: ExportFormat, media: Boolean): String = if (format.isObsidian) {
        "![[${path}]]".let { if (media) it else "[[${path}]]" }
    } else if (media) "![image]($path)" else "[Attachment]($path)"
    fun svg(element: HandwritingElement): String = buildString {
        val width = if (element.width.isFinite() && element.width > 0f) element.width else 1f; val height = if (element.height.isFinite() && element.height > 0f) element.height else 1f
        append("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"$width\" height=\"$height\" viewBox=\"0 0 $width $height\" fill=\"none\">")
        element.strokes.take(SdocxLimits.MAX_OBJECTS_PER_PAGE).forEach { stroke -> val points = stroke.points.take(SdocxLimits.MAX_STROKE_POINTS).filter { it.x.isFinite() && it.y.isFinite() }; if (points.size >= 2) { append("<path d=\""); points.forEachIndexed { i, p -> append(if (i == 0) "M" else " L").append(p.x).append(',').append(p.y) }; val color = stroke.color.toUInt().toString(16).padStart(8, '0').takeLast(6); append("\" stroke=\"#$color\" stroke-width=\"${if (stroke.width.isFinite() && stroke.width > 0) stroke.width else 2f}\" stroke-linecap=\"round\" stroke-linejoin=\"round\"/>") } }; append("</svg>")
    }
}
