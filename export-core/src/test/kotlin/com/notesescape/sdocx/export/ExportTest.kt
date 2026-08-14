package com.notesescape.sdocx.export

import com.notesescape.sdocx.core.*
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ExportTest {
    @Test fun markdownSemanticsAndUnicodeRoundTrip() {
        val unicode = "Hello world\nÉté à Montréal — déjà vu\n这是一个三星笔记测试\n삼성 노트 테스트입니다\nهذه ملاحظة اختبار\nMeeting ✅ 🔒 📱"
        val spans = listOf(RichTextSpan("bold", bold = true), RichTextSpan("italic", italic = true), RichTextSpan("strike", strike = true), RichTextSpan("link", link = "https://example.com/a (b)"), RichTextSpan("under", underline = true))
        val result = ParseResult(DocumentMetadata("A \"note\""), listOf(Page(1, elements = listOf(RichTextElement(unicode), RichTextElement("item", paragraph = ParagraphStyle(listKind = "bullet")), RichTextElement("done", paragraph = ParagraphStyle(checked = true)), RichTextElement("semantics", spans = spans)))), emptyList(), emptyList(), ParseStatus.SUCCESS)
        val clean = MarkdownExporter.render(result, ExportFormat.CLEAN_MARKDOWN); val rich = MarkdownExporter.render(result, ExportFormat.OBSIDIAN_RICH)
        assertTrue(clean.contains(unicode)); assertTrue(clean.contains("**bold**")); assertTrue(clean.contains("*italic*")); assertTrue(clean.contains("~~strike~~")); assertTrue(clean.contains("[link](https://example.com/a%20%28b%29)")); assertTrue(clean.contains("- item")); assertTrue(clean.contains("- [x] done")); assertFalse(clean.contains("<u>under</u>")); assertTrue(rich.contains("<u>under</u>")); assertTrue(clean.contains("A \\\"note\\\""))
    }
    @Test fun topLevelRichTextPreservesStructuredListNumbersAndCheckboxStrike() {
        val result = ParseResult(
            DocumentMetadata("Windows sample"), emptyList(), emptyList(), emptyList(), ParseStatus.SUCCESS,
            topLevelText = "one\ntwo\ndone",
            topLevelElements = listOf(
                RichTextElement("one", paragraph = ParagraphStyle(listKind = "number", listNumber = 1)),
                RichTextElement("two", paragraph = ParagraphStyle(listKind = "number", listNumber = 2)),
                RichTextElement("done", spans = listOf(RichTextSpan("done", strike = true)), paragraph = ParagraphStyle(listKind = "checkbox", checked = true))
            )
        )
        val markdown = MarkdownExporter.render(result)
        assertTrue(markdown.contains("1. one"))
        assertTrue(markdown.contains("2. two"))
        assertTrue(markdown.contains("- [x] ~~done~~"))
    }
    @Test fun svgIsValidXmlWithVisiblePaths() { val svg = MarkdownExporter.svg(HandwritingElement(listOf(Stroke(listOf(StrokePoint(1f, 2f), StrokePoint(3f, 4f))), Stroke(listOf(StrokePoint(5f, 6f), StrokePoint(7f, 8f)))), width = 10f, height = 10f)); val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(ByteArrayInputStream(svg.toByteArray())); assertEquals("svg", doc.documentElement.nodeName); assertEquals(2, Regex("<path ").findAll(svg).count()); assertFalse(svg.contains("NaN")); assertFalse(svg.contains("Infinity")) }
    @Test fun embeddedMediaGetsMarkdownLinksWhenNoPageObjectIsExposed() {
        val result = ParseResult(
            DocumentMetadata("Media note"),
            emptyList(),
            listOf(
                MediaAsset("0", "photo.jpg", InMemoryMediaContent(byteArrayOf(0xff.toByte(), 0xd8.toByte(), 0xff.toByte()))),
                MediaAsset("1", "document.pdf", InMemoryMediaContent("%PDF-1.7".toByteArray()))
            ),
            emptyList(),
            ParseStatus.SUCCESS,
            topLevelText = "Text before media"
        )
        val markdown = MarkdownExporter.render(result)
        assertTrue(markdown.contains("![[assets/Media note/photo.jpg]]"))
        assertTrue(markdown.contains("[Attachment](assets/Media note/document.pdf)"))
    }
    @Test fun duplicateNamesAreStable() { val used = mutableSetOf<String>(); assertTrue(SafeNames.unique("Meeting Notes.md", used).contains("Meeting Notes")); assertTrue(SafeNames.unique("Meeting Notes.md", used).contains("(2)")) }
    @Test fun publicFixtureBatchProducesMarkdownSvgReportsAndOriginals() { val dir = java.io.File("../test-fixtures/bxff-samsung-notes-format/sdocxFiles"); val sources = dir.listFiles()!!.filter { it.extension == "sdocx" }.sortedBy { it.name }.map { SourceNote(it.name, it.readBytes()) }; val out = ByteArrayOutputStream(); val archive = ArchiveExporter.export(sources.asSequence(), out, preserveHandwriting = true); assertEquals(5, archive.reports.size); assertTrue(archive.reports.all { it.pageCount == 1 && it.handwritingPages > 0 }); val names = mutableSetOf<String>(); val contents = mutableMapOf<String, ByteArray>(); ZipInputStream(ByteArrayInputStream(out.toByteArray())).use { zip -> while (true) { val entry = zip.nextEntry ?: break; assertTrue(names.add(entry.name)); assertFalse(entry.name.startsWith("/")); assertFalse(entry.name.split('/').contains("..")); contents[entry.name] = zip.readBytes(); assertTrue(contents[entry.name]!!.isNotEmpty()) } }; assertTrue(names.contains("migration-report.md")); assertTrue(names.contains("migration-report.json")); assertEquals(5, names.count { it.startsWith("originals/") }); assertTrue(names.any { it.endsWith("handwriting_page_01.svg") }); assertTrue(names.any { it.endsWith(".md") && it.startsWith("notes/") }); assertTrue(names.any { it.startsWith("assets/") && !it.endsWith(".svg") }); contents.filterKeys { it.endsWith(".svg") }.forEach { (name, bytes) -> val xml = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(ByteArrayInputStream(bytes)); assertEquals("svg", xml.documentElement.nodeName); assertTrue(String(bytes).contains("<path")); assertTrue(name.startsWith("assets/")) } }
    @Test fun corruptFileIsolatedInBatch() { val sources = sequenceOf(SourceNote("valid.sdocx", java.io.File("../test-fixtures/bxff-samsung-notes-format/sdocxFiles/ThisIsTheTitle-1.sdocx").readBytes()), SourceNote("corrupt.sdocx", byteArrayOf(1, 2, 3)), SourceNote("valid2.sdocx", java.io.File("../test-fixtures/bxff-samsung-notes-format/sdocxFiles/ThisIsTheTitle-2.sdocx").readBytes())); val out = ByteArrayOutputStream(); val archive = ArchiveExporter.export(sources, out); assertEquals(3, archive.reports.size); assertEquals(ParseStatus.CORRUPT, archive.reports[1].status); assertTrue(archive.reports[0].status != ParseStatus.CORRUPT); assertTrue(archive.reports[2].status != ParseStatus.CORRUPT); assertTrue(out.size() > 0) }
    @Test fun mediaMagicValidation() { assertEquals("image/png", MediaType.detect("wrong.bin", byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47))); assertEquals(null, MediaType.detect("file.unknown", byteArrayOf(1, 2, 3))) }
    @Test fun originalIsReopenedAndStreamedFromConversionSource() {
        val original = java.io.File("../test-fixtures/bxff-samsung-notes-format/sdocxFiles/ThisIsTheTitle-1.sdocx").readBytes()
        val source = object : ConversionSource {
            override val displayName = "streamed.sdocx"
            var opens = 0
            var closed = false
            override fun openStream(): InputStream { opens++; return ByteArrayInputStream(original) }
            override fun close() { closed = true }
        }
        val output = ByteArrayOutputStream()
        ArchiveExporter.export(sequenceOf(source), output, includeAttachments = false, includeOriginals = true)
        assertEquals(2, source.opens)
        assertTrue(source.closed)
        ZipInputStream(ByteArrayInputStream(output.toByteArray())).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.name == "originals/streamed.sdocx") assertTrue(zip.readBytes().contentEquals(original))
            }
        }
    }
}
