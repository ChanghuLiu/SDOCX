package com.notesescape.sdocx.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class CoreTest {
    @Test fun littleEndianAndBounds() { val r = LittleEndianReader(byteArrayOf(1,2,3,4)); assertEquals(513, r.i16()); assertEquals(0x04030201, LittleEndianReader(byteArrayOf(1,2,3,4)).i32()); assertFailsWith<BoundsException> { r.i32() } }
    @Test fun unicodeIsPreserved() { val s = "français العربية 中文 한국어 😀"; assertEquals(s, String(s.toByteArray(Charsets.UTF_16LE), Charsets.UTF_16LE)) }
    @Test fun malformedZipReturnsNoEntries() { assertEquals(emptyMap(), SdocxArchiveReader.read(java.io.ByteArrayInputStream(byteArrayOf(1,2,3)))) }
    @Test fun unknownPageWarns() { val result = SdocxParser.parseEntries(mapOf("x.page" to byteArrayOf(0,1,2), "note.note" to "Title".toByteArray())); assertEquals(1, result.pages.size); assert(result.warnings.isNotEmpty()) }
    @Test fun publicMitFixturesRecoverTitlesPagesMediaAndStrokes() { val dir = java.io.File("../test-fixtures/bxff-samsung-notes-format/sdocxFiles"); val files = dir.listFiles()?.filter { it.extension == "sdocx" }.orEmpty(); assertEquals(5, files.size); val expected = mapOf("Eg-walker.sdocx" to ("Eg walker O(n^2) case" to 47), "Mako-minimal.sdocx" to ("Mako OT N problem still exists. Minimal dub" to 6), "Mako.sdocx" to ("Mako OT N problem still exists." to 29), "ThisIsTheTitle-1.sdocx" to ("ThisIsTheTitle" to 1), "ThisIsTheTitle-2.sdocx" to ("ThisIsTheTitle" to 3)); files.forEach { file -> val result = file.inputStream().use(SdocxParser::parse); assertEquals(expected[file.name]?.first, result.metadata.title); assertEquals(4000, result.metadata.formatVersion); assertEquals(1, result.pages.size); assertEquals(1, result.media.size); assertTrue(result.metadata.created?.startsWith("2025-") == true || result.metadata.created?.startsWith("2026-") == true); val strokes = result.pages.flatMap { it.elements }.filterIsInstance<HandwritingElement>().sumOf { it.strokes.size }; assertEquals(expected[file.name]?.second, strokes); assert(result.pages.flatMap { it.elements }.filterIsInstance<HandwritingElement>().flatMap { it.strokes }.flatMap { it.points }.all { it.x.isFinite() && it.y.isFinite() }) } }

    @Test fun mediaRegistrySupportsEofxAndBounds() { val result = testFixture("ThisIsTheTitle-1.sdocx"); assertEquals("0", result.media.single().bindId); assert(result.media.single().bytes.isNotEmpty()); assert(result.media.single().archivePath!!.contains("media/")) }
    private fun testFixture(name: String) = java.io.File("../test-fixtures/bxff-samsung-notes-format/sdocxFiles/$name").inputStream().use(SdocxParser::parse)

    @Test fun mediaRegistrySupportsBothEndings() { listOf(false, true).forEach { newer -> val hash = "a".repeat(64); val entry = java.io.ByteArrayOutputStream().apply { writeIntLe(0); writeShortLe(4); write("file".toByteArray(Charsets.UTF_16LE)); write(hash.toByteArray()); writeShortLe(0); writeLongLe(0L); if (newer) write(1) }.toByteArray(); val bytes = java.io.ByteArrayOutputStream().apply { if (newer) writeIntLe(4000); writeShortLe(1); if (newer) writeIntLe(entry.size); write(entry); write(if (newer) "EOFX".toByteArray() else "EOF".toByteArray()) }.toByteArray(); val warnings = mutableListOf<ParseWarning>(); val result = MediaRegistryParser.parse(bytes, warnings); assertEquals(1, result.size); assertEquals("file", result.single().filename); assertTrue(warnings.isEmpty()) } }

    private fun java.io.ByteArrayOutputStream.writeShortLe(value: Int) { write(value and 255); write(value ushr 8 and 255) }
    private fun java.io.ByteArrayOutputStream.writeIntLe(value: Int) { write(value and 255); write(value ushr 8 and 255); write(value ushr 16 and 255); write(value ushr 24 and 255) }
    private fun java.io.ByteArrayOutputStream.writeLongLe(value: Long) { repeat(8) { write((value ushr (it * 8)).toInt() and 255) } }
    @Test fun sourceEnumerationRuleIsCaseInsensitiveAndDeterministic() { assertTrue(SourceNameRules.isSdocx("note.SDOCX")); assertTrue(!SourceNameRules.isSdocx("note.zip")); assertTrue(SourceNameRules.sortKey("a.sdocx", "z") < SourceNameRules.sortKey("b.sdocx", "a")) }
    @Test fun zipSlipIsRejected() { val bytes = java.io.ByteArrayOutputStream(); ZipOutputStream(bytes).use { zip -> zip.putNextEntry(ZipEntry("../escape")); zip.write(byteArrayOf(1)); zip.closeEntry() }; assertFailsWith<BoundsException> { SdocxArchiveReader.read(java.io.ByteArrayInputStream(bytes.toByteArray())) } }
}
