package com.notesescape.sdocx.export

import com.notesescape.sdocx.core.*

enum class ExportFormat { CLEAN_MARKDOWN, OBSIDIAN_RICH }
object SafeNames {
    fun file(value: String, fallback: String = "Untitled"): String { val s = value.replace(Regex("[\\x00-\\x1f<>:\"/\\\\|?*]"), "_").trim().trimEnd('.'); return s.ifBlank { fallback }.take(180) }
    fun unique(base: String, used: MutableSet<String>): String { val stem = base.substringBeforeLast('.', base); val ext = base.substringAfterLast('.', ""); var result = base; var i = 2; while (!used.add(result)) result = "$stem ($i)${if (ext.isEmpty()) "" else "." + ext}".also { i++ }; return result }
}
object MarkdownExporter {
    fun render(result: ParseResult, format: ExportFormat = ExportFormat.CLEAN_MARKDOWN): String = buildString {
        appendLine("---"); appendLine("title: \"${yaml(result.metadata.title)}\"")
        result.metadata.created?.let { appendLine("created: \"${yaml(it)}\"") }; result.metadata.modified?.let { appendLine("modified: \"${yaml(it)}\"") }
        appendLine("source: \"Samsung Notes\""); appendLine("source_format: \"sdocx\""); result.metadata.appVersion?.let { appendLine("source_app_version: \"${yaml(it)}\"") }
        result.metadata.formatVersion?.let { appendLine("source_format_version: $it") }; appendLine("direction: \"${result.metadata.direction}\""); appendLine("---"); appendLine()
        val top = result.topLevelText?.trim().takeUnless { it.isNullOrBlank() }
        top?.let { appendLine(it); appendLine() }
        result.pages.forEach { page -> page.elements.sortedWith(compareBy<PageElement> { it.y }.thenBy { it.x }.thenBy { it.sourceOrder }).forEach { element ->
            if (element is RichTextElement && top != null && normalize(element.text) == normalize(top)) return@forEach
            when (element) {
                is RichTextElement -> appendLine(formatText(element, format))
                is ImageElement -> appendLine("![[assets/${SafeNames.file(result.metadata.title)}/${SafeNames.file(element.bindId)}]]")
                is AttachmentElement -> appendLine("[Attachment](assets/attachments/${SafeNames.file(element.bindId)})")
                is HandwritingElement -> appendLine("![[assets/${SafeNames.file(result.metadata.title)}/handwriting_page_${page.number.toString().padStart(2, '0')}.svg]]")
                is UnknownElement -> appendLine("<!-- Unsupported object ${element.kind}; see migration report -->")
            }
        }; appendLine() }
    }
    private fun formatText(e: RichTextElement, format: ExportFormat): String {
        val content = if (e.spans.isEmpty()) escapePlain(e.text) else e.spans.joinToString("") { renderSpan(it, format) }
        val listed = when (e.paragraph.listKind) { "bullet" -> "- "; "number" -> "1. "; else -> "" }
        val checkbox = e.paragraph.checked?.let { if (it) "- [x] " else "- [ ] " } ?: listed
        return checkbox + content
    }
    private fun renderSpan(span: RichTextSpan, format: ExportFormat): String {
        var value = escapePlain(span.text)
        if (span.bold) value = "**$value**"; if (span.italic) value = "*$value*"; if (span.strike) value = "~~$value~~"
        if (span.underline && format == ExportFormat.OBSIDIAN_RICH) value = "<u>$value</u>"
        return span.link?.let { "[${value.replace("]", "\\]")}](${escapeUrl(it)})" } ?: value
    }
    private fun escapePlain(value: String): String = value.replace("\\", "\\\\").replace("`", "\\`").replace("\r\n", "\n")
    private fun escapeUrl(value: String): String = value.replace("\\", "%5C").replace("(", "%28").replace(")", "%29").replace(" ", "%20")
    private fun yaml(value: String) = value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r")
    private fun normalize(value: String) = value.replace("\r\n", "\n").trim()
    fun svg(element: HandwritingElement): String = buildString {
        val width = if (element.width.isFinite() && element.width > 0f) element.width else 1f; val height = if (element.height.isFinite() && element.height > 0f) element.height else 1f
        append("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"$width\" height=\"$height\" viewBox=\"0 0 $width $height\" fill=\"none\">")
        element.strokes.take(SdocxLimits.MAX_OBJECTS_PER_PAGE).forEach { stroke -> val points = stroke.points.take(SdocxLimits.MAX_STROKE_POINTS).filter { it.x.isFinite() && it.y.isFinite() }; if (points.size >= 2) { append("<path d=\""); points.forEachIndexed { i, p -> append(if (i == 0) "M" else " L").append(p.x).append(',').append(p.y) }; val color = stroke.color.toUInt().toString(16).padStart(8, '0').takeLast(6); append("\" stroke=\"#$color\" stroke-width=\"${if (stroke.width.isFinite() && stroke.width > 0) stroke.width else 2f}\" stroke-linecap=\"round\" stroke-linejoin=\"round\"/>") } }; append("</svg>")
    }
}
