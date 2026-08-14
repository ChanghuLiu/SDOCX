package com.notesescape.sdocx.core

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import kotlin.math.max

/** Conservative limits applied before allocating or iterating over untrusted SDOCX data. */
object SdocxLimits {
    const val MAX_ARCHIVE_ENTRIES = 4096
    const val MAX_ENTRY_UNCOMPRESSED_BYTES = 100L * 1024 * 1024
    const val MAX_TOTAL_UNCOMPRESSED_BYTES = 512L * 1024 * 1024
    const val MAX_NOTE_PAGES = 2048
    const val MAX_OBJECTS_PER_PAGE = 100_000
    const val MAX_STROKE_POINTS = 500_000
    const val MAX_OBJECT_BYTES = 8 * 1024 * 1024
}
object SourceNameRules {
    fun isSdocx(name: String?): Boolean = name?.endsWith(".sdocx", ignoreCase = true) == true
    fun sortKey(name: String, uri: String): String = "${name.lowercase()}\u0000$uri"
}

data class DocumentMetadata(
    val title: String = "Untitled", val created: String? = null, val modified: String? = null,
    val appVersion: String? = null, val formatVersion: Int? = null, val direction: String = "ltr",
    val locked: Boolean = false, val width: Int? = null, val height: Int? = null, val noteUuid: String? = null
)
data class Page(val number: Int, val width: Float = 1f, val height: Float = 1f, val elements: List<PageElement> = emptyList())
sealed interface PageElement { val x: Float; val y: Float; val sourceOrder: Int }
data class RichTextElement(val text: String, override val x: Float = 0f, override val y: Float = 0f, override val sourceOrder: Int = 0,
    val spans: List<RichTextSpan> = emptyList(), val paragraph: ParagraphStyle = ParagraphStyle()) : PageElement
data class ImageElement(val bindId: String, override val x: Float = 0f, override val y: Float = 0f, override val sourceOrder: Int = 0) : PageElement
data class HandwritingElement(val strokes: List<Stroke>, override val x: Float = 0f, override val y: Float = 0f, override val sourceOrder: Int = 0,
    val width: Float = 1f, val height: Float = 1f) : PageElement
data class AttachmentElement(val bindId: String, override val x: Float = 0f, override val y: Float = 0f, override val sourceOrder: Int = 0) : PageElement
data class UnknownElement(val kind: Int, override val x: Float = 0f, override val y: Float = 0f, override val sourceOrder: Int = 0) : PageElement
data class RichText(val spans: List<RichTextSpan>)
data class RichTextSpan(val text: String, val bold: Boolean = false, val italic: Boolean = false, val underline: Boolean = false, val strike: Boolean = false, val link: String? = null)
data class ParagraphStyle(val alignment: String? = null, val indent: Int = 0, val listKind: String? = null, val checked: Boolean? = null)
data class Stroke(val points: List<StrokePoint>, val color: Int = 0xff000000.toInt(), val width: Float = 2f)
data class StrokePoint(val x: Float, val y: Float, val pressure: Float = 1f)
data class MediaAsset(val bindId: String, val filename: String, val bytes: ByteArray, val hash: String? = null, val attachment: Boolean = false, val archivePath: String? = null)
data class MediaBinding(val bindId: String, val archivePath: String, val filename: String, val hash: String? = null, val attachment: Boolean? = null)
data class ParseWarning(val message: String, val entry: String? = null)
enum class ParseStatus { SUCCESS, PARTIAL, LOCKED, UNSUPPORTED, CORRUPT, FAILED }
data class ParseResult(val metadata: DocumentMetadata, val pages: List<Page>, val media: List<MediaAsset>, val warnings: List<ParseWarning>, val status: ParseStatus, val topLevelText: String? = null)

class BoundsException(message: String): Exception(message)
class LittleEndianReader(private val bytes: ByteArray) {
    var position = 0
    val remaining get() = bytes.size - position
    private fun need(n: Int) { if (n < 0 || n > remaining) throw BoundsException("truncated record at $position (need $n, have $remaining)") }
    fun u8(): Int { need(1); return bytes[position++].toInt() and 255 }
    fun i16(): Int { need(2); return u8() or (u8() shl 8); }
    fun u16(): Int = i16()
    fun i32(): Int { need(4); return i16() or (i16() shl 16) }
    fun u32(): Long = i32().toLong() and 0xffffffffL
    fun i64(): Long { need(8); var result = 0L; repeat(8) { result = result or (u8().toLong() shl (it * 8)) }; return result }
    fun f32(): Float = Float.fromBits(i32())
    fun f64(): Double { need(8); return ByteBuffer.wrap(bytes, position, 8).order(ByteOrder.LITTLE_ENDIAN).double.also { position += 8 } }
    fun bytes(n: Int): ByteArray { need(n); return bytes.copyOfRange(position, position + n).also { position += n } }
    fun utf16(nBytes: Int): String = bytes(nBytes).toString(Charsets.UTF_16LE)
    fun shortUtf16(): String { val count = i16(); if (count < 0 || count > remaining / 2) throw BoundsException("invalid UTF-16 string length $count"); return utf16(count * 2) }
    fun shortUtf8(): String { val count = i16(); if (count < 0 || count > remaining) throw BoundsException("invalid UTF-8 string length $count"); return bytes(count).toString(Charsets.UTF_8) }
    fun longUtf16(): String { val count = i32(); if (count < 0 || count > remaining / 2) throw BoundsException("invalid long UTF-16 string length $count"); return utf16(count * 2) }
    fun skip(n: Int) { need(n); position += n }
}

object SdocxArchiveReader {
    fun read(input: InputStream, warnings: MutableList<ParseWarning> = mutableListOf()): Map<String, ByteArray> {
        val result = linkedMapOf<String, ByteArray>(); var total = 0L
        ZipInputStream(input).use { zip ->
            var count = 0
            while (true) {
                val entry = try { zip.nextEntry } catch (e: Exception) { throw BoundsException("malformed ZIP: ${e.message}") } ?: break
                if (++count > SdocxLimits.MAX_ARCHIVE_ENTRIES) throw BoundsException("archive entry count exceeds ${SdocxLimits.MAX_ARCHIVE_ENTRIES}")
                val name = entry.name.replace('\\','/')
                if (name.startsWith("/") || name.split('/').any { it == ".." } || name.contains('\u0000')) throw BoundsException("unsafe ZIP path: $name")
                if (result.containsKey(name)) warnings += ParseWarning("duplicate ZIP entry ignored: $name", name)
                val out = ByteArrayOutputStream(); val buf = ByteArray(8192); var entryTotal = 0L
                while (true) {
                    val n = try { zip.read(buf) } catch (e: Exception) { throw BoundsException("truncated ZIP entry $name") }
                    if (n < 0) break
                    entryTotal += n; total += n
                    if (entryTotal > SdocxLimits.MAX_ENTRY_UNCOMPRESSED_BYTES) throw BoundsException("ZIP entry too large: $name")
                    if (total > SdocxLimits.MAX_TOTAL_UNCOMPRESSED_BYTES) throw BoundsException("total ZIP content too large")
                    out.write(buf, 0, n)
                }
                result.putIfAbsent(name, out.toByteArray()); zip.closeEntry()
            }
        }
        return result
    }
}

object SdocxParser {
    fun parse(input: InputStream): ParseResult = try { val warnings = mutableListOf<ParseWarning>(); val entries = SdocxArchiveReader.read(input, warnings); if (entries.isEmpty()) throw BoundsException("empty SDOCX archive"); parseEntries(entries, warnings) }
    catch (e: Exception) { ParseResult(DocumentMetadata(), emptyList(), emptyList(), listOf(ParseWarning(e.message ?: "corrupt archive")), ParseStatus.CORRUPT) }

    fun parseEntries(entries: Map<String, ByteArray>, initialWarnings: MutableList<ParseWarning> = mutableListOf()): ParseResult {
        val warnings = initialWarnings; val end = entries.entries.firstOrNull { it.key.endsWith("end_tag.bin") }?.value?.let { EndTagParser.parse(it, warnings) }
        val registry = entries.entries.firstOrNull { it.key.endsWith("media/mediaInfo.dat") }?.value?.let { MediaRegistryParser.parse(it, warnings) } ?: emptyList()
        val pageIds = entries.entries.firstOrNull { it.key.endsWith("pageIdInfo.dat") }?.value?.let { PageIdParser.parse(it, warnings) } ?: emptyList()
        val noteData = entries.entries.firstOrNull { it.key.endsWith("note.note") }?.value?.let { NoteDocParser.parse(it, warnings) }
        val metadata = mergeMetadata(noteData?.metadata ?: DocumentMetadata(), end)
        if (metadata.locked) return ParseResult(metadata, emptyList(), emptyList(), warnings + ParseWarning("This note is locked. Unlock it in Samsung Notes and export it again."), ParseStatus.LOCKED, noteData?.bodyText)
        val media = registry.mapNotNull { binding -> val actual = entries[binding.archivePath]?.let { binding.archivePath to it } ?: entries.entries.firstOrNull { it.key.startsWith("media/") && it.key.substringAfterLast('/').endsWith(binding.filename) }?.let { it.key to it.value }; actual?.let { (path, data) -> MediaAsset(binding.bindId, binding.filename, data, binding.hash, binding.attachment == true, path) } ?: run { warnings += ParseWarning("Registered media is missing: ${binding.archivePath}", binding.archivePath); null } }
        val pageEntries = entries.filterKeys { it.endsWith(".page") }.toList().sortedWith(compareBy<Pair<String, ByteArray>> { pair -> pageIds.indexOfFirst { id -> pair.first.contains(id) }.let { if (it < 0) Int.MAX_VALUE else it } }.thenBy { pair -> pair.first })
        val pages = pageEntries.take(SdocxLimits.MAX_NOTE_PAGES).mapIndexed { index, (path, bytes) -> PageParser.parse(index + 1, path, bytes, warnings) }
        if (pageEntries.size > SdocxLimits.MAX_NOTE_PAGES) warnings += ParseWarning("page count exceeds ${SdocxLimits.MAX_NOTE_PAGES}; remaining pages omitted")
        if (pages.isEmpty()) warnings += ParseWarning("No page records were recognized")
        val status = if (warnings.isEmpty()) ParseStatus.SUCCESS else ParseStatus.PARTIAL
        return ParseResult(metadata, pages, media, warnings, status, noteData?.bodyText)
    }
    private fun mergeMetadata(note: DocumentMetadata, end: DocumentMetadata?): DocumentMetadata = if (end == null) note else note.copy(
        created = end.created ?: note.created, modified = end.modified ?: note.modified, appVersion = end.appVersion ?: note.appVersion,
        formatVersion = end.formatVersion ?: note.formatVersion, direction = end.direction, locked = end.locked || note.locked,
        width = end.width ?: note.width, height = end.height ?: note.height, noteUuid = end.noteUuid ?: note.noteUuid,
        title = note.title.takeIf { it != "Untitled" } ?: end.title)
}

data class ParsedNoteData(val metadata: DocumentMetadata, val bodyText: String?, val titleBytes: ByteArray = byteArrayOf())
object NoteDocParser {
    fun parse(bytes: ByteArray, warnings: MutableList<ParseWarning> = mutableListOf()): ParsedNoteData = try {
        val r = LittleEndianReader(bytes); r.i32(); r.u8(); r.i32(); r.u8(); r.i32(); val version = r.i32(); val id = r.shortUtf16(); r.i32(); val created = timestamp(r.i64()); val modified = timestamp(r.i64()); val width = r.i32(); val height = r.i32(); r.i32(); r.i32(); r.i32()
        val titleSize = r.i32(); if (titleSize < 0 || titleSize > r.remaining || titleSize > SdocxLimits.MAX_OBJECT_BYTES) throw BoundsException("invalid title object size")
        val titleBytes = r.bytes(titleSize); val title = extractTitle(titleBytes); val body = if (r.remaining >= 4) { val size = r.i32(); if (size in 0..r.remaining && size <= SdocxLimits.MAX_OBJECT_BYTES) decodeTextObject(r.bytes(size)) else null } else null
        ParsedNoteData(DocumentMetadata(title.ifBlank { "Untitled" }, created, modified, formatVersion = version, width = width, height = height, noteUuid = id), body, titleBytes)
    } catch (e: Exception) { warnings += ParseWarning("note.note: ${e.message}", "note.note"); ParsedNoteData(DocumentMetadata(), null) }
    private fun extractTitle(bytes: ByteArray): String { if (bytes.size < 8) return ""; for (i in 0..bytes.size - 8) { val n = ByteBuffer.wrap(bytes, i, 4).order(ByteOrder.LITTLE_ENDIAN).int; if (n in 3..200 && i + 4 + n * 2 <= bytes.size) { val text = bytes.copyOfRange(i + 4, i + 4 + n * 2).toString(Charsets.UTF_16LE).trimEnd('\u0000'); if (text.isNotBlank() && text.all { !it.isISOControl() }) return text } }; return "" }
    private fun decodeTextObject(bytes: ByteArray): String? = runCatching { val r = LittleEndianReader(bytes); r.longUtf16() }.getOrNull()?.takeIf { it.isNotBlank() } ?: bytes.toString(Charsets.UTF_8).takeIf { it.any { c -> c.isLetterOrDigit() } }
}

object PageIdParser {
    fun parse(bytes: ByteArray, warnings: MutableList<ParseWarning> = mutableListOf()): List<String> {
        return try { val r = LittleEndianReader(bytes); if (r.remaining < 34) emptyList() else { r.skip(32); val count = r.u16(); if (count > SdocxLimits.MAX_NOTE_PAGES) throw BoundsException("page ID count too large"); buildList { repeat(count) { add(r.shortUtf16()); if (r.remaining >= 32) r.skip(32) } } } }
        catch (e: Exception) { warnings += ParseWarning("pageIdInfo.dat: ${e.message}", "pageIdInfo.dat"); emptyList() }
    }
}

object MediaRegistryParser {
    fun parse(bytes: ByteArray, warnings: MutableList<ParseWarning> = mutableListOf()): List<MediaBinding> {
        return try {
        if (bytes.size < 7) emptyList() else { val newer = bytes.takeLast(4).toByteArray().contentEquals("EOFX".toByteArray()); val older = bytes.takeLast(3).toByteArray().contentEquals("EOF".toByteArray()); if (!newer && !older) throw BoundsException("media registry missing EOF/EOFX")
        val r = LittleEndianReader(bytes); if (newer) r.i32(); val count = r.u16(); if (count > SdocxLimits.MAX_ARCHIVE_ENTRIES) throw BoundsException("media registry count too large"); buildList {
            repeat(count) { val entrySize = if (newer) r.i32() else r.remaining; if (entrySize < 0 || entrySize > r.remaining || entrySize > SdocxLimits.MAX_ENTRY_UNCOMPRESSED_BYTES) throw BoundsException("invalid media entry size"); val start = r.position; val entry = LittleEndianReader(r.bytes(entrySize)); val id = entry.u32().toString(); val name = entry.shortUtf16(); val hash = entry.bytes(minOf(64, entry.remaining)).toString(Charsets.UTF_8).trimEnd('\u0000'); if (entry.remaining >= 2) entry.u16(); if (entry.remaining >= 8) entry.i64(); val attached = if (newer && entry.remaining >= 1) entry.u8() != 0 else null; add(MediaBinding(id, "media/$name", name, hash.takeIf { it.isNotBlank() }, attached)); if (!newer) r.position = start + entry.position }
        }
        } } catch (e: Exception) { warnings += ParseWarning("media/mediaInfo.dat: ${e.message}", "media/mediaInfo.dat"); emptyList() }
    }
}

data class EndTagData(val metadata: DocumentMetadata)
object EndTagParser {
    fun parse(bytes: ByteArray, warnings: MutableList<ParseWarning> = mutableListOf()): DocumentMetadata? = try {
        val r0 = LittleEndianReader(bytes); val size = r0.u16(); if (size > r0.remaining) throw BoundsException("end tag size out of bounds"); val payload = r0.bytes(size); val ident = payload.takeLast(4).toByteArray().toString(Charsets.UTF_8); val r = LittleEndianReader(payload.copyOf(payload.size - if (ident == "SNAP" || ident == "SDOC") 4 else 0)); val version = r.i32(); val uuid = r.shortUtf16(); val modified = timestamp(r.i64()); r.u32(); r.shortUtf16(); val width = r.i32(); r.f32(); val app = r.shortUtf16(); val appVersion = if (r.remaining >= 2) runCatching { r.shortUtf16() }.getOrNull() else null; if (r.remaining >= 4) r.i32(); val created = if (r.remaining >= 8) timestamp(r.i64()) else null; if (r.remaining >= 4) r.i32(); if (r.remaining >= 2) r.u16(); if (r.remaining >= 2) r.u16(); val locked = bytes.containsSequence("encrypted")
        DocumentMetadata(title = "Untitled", created = created, modified = modified, appVersion = app.takeIf { it.isNotBlank() } ?: appVersion, formatVersion = version.takeIf { it > 0 }, direction = "ltr", locked = locked, width = width, height = null, noteUuid = uuid)
    } catch (e: Exception) { warnings += ParseWarning("end_tag.bin: ${e.message}", "end_tag.bin"); null }
}

private fun ByteArray.containsSequence(value: String): Boolean = String(this, Charsets.ISO_8859_1).contains(value, true)
private fun timestamp(value: Long): String? = value.takeIf { it > 946684800000000L }?.let { runCatching { DateTimeFormatter.ISO_INSTANT.format(Instant.ofEpochMilli(it / 1000).atOffset(ZoneOffset.UTC)) }.getOrNull() }

object PageParser {
    fun parse(number: Int, entry: String, bytes: ByteArray, warnings: MutableList<ParseWarning>): Page = try {
        val r = LittleEndianReader(bytes); val layerOffset = r.i32(); r.i32(); r.u8(); r.i32(); r.u8(); r.i32(); val orientation = r.i32(); val width = r.i32(); val height = r.i32(); r.i32(); r.i32(); val uuid = r.shortUtf16(); r.i64(); val version = r.i32(); r.i32(); if (layerOffset !in 0..bytes.size - 2) throw BoundsException("layer offset out of bounds")
        r.position = layerOffset; val layerCount = r.u16(); r.u16(); if (layerCount > SdocxLimits.MAX_OBJECTS_PER_PAGE) throw BoundsException("layer count too large"); val elements = mutableListOf<PageElement>(); repeat(layerCount) { if (r.remaining < 4) throw BoundsException("truncated layer"); r.skip(4); parseLayer(r, elements, warnings, width.toFloat(), height.toFloat()) }
        Page(number, width.toFloat().coerceAtLeast(1f), height.toFloat().coerceAtLeast(1f), elements)
    } catch (e: Exception) { warnings += ParseWarning("$entry: ${e.message}", entry); Page(number) }
    private fun parseLayer(r: LittleEndianReader, elements: MutableList<PageElement>, warnings: MutableList<ParseWarning>, pageWidth: Float, pageHeight: Float) {
        if (r.remaining < 24) throw BoundsException("truncated layer header"); r.i32(); r.u8(); val flags = r.u8(); r.u8(); val fieldFlags = r.u8(); r.i32(); if ((fieldFlags and 1) != 0) r.u8(); if ((fieldFlags and 2) != 0) r.i32(); if ((fieldFlags and 4) != 0) r.shortUtf16(); if ((fieldFlags and 8) != 0) r.shortUtf16(); if ((fieldFlags and 16) != 0) r.i64(); if ((fieldFlags and 32) != 0) r.i32(); val count = r.i32(); if (count !in 0..SdocxLimits.MAX_OBJECTS_PER_PAGE) throw BoundsException("object count too large"); repeat(count) { parseObject(r, elements, warnings, pageWidth, pageHeight, it) }; if (r.remaining >= 32) r.skip(32) else throw BoundsException("truncated layer hash")
    }
    private fun parseObject(r: LittleEndianReader, elements: MutableList<PageElement>, warnings: MutableList<ParseWarning>, pageWidth: Float, pageHeight: Float, order: Int) {
        val type = r.u8(); val childCount = r.u16(); if (childCount > SdocxLimits.MAX_OBJECTS_PER_PAGE) throw BoundsException("child count too large"); val size = r.i32(); if (size !in 0..minOf(SdocxLimits.MAX_OBJECT_BYTES, r.remaining)) { warnings += ParseWarning("object $type has invalid size $size"); if (size in 0..r.remaining) r.skip(size); return }; val data = r.bytes(size); val base = parseObjectBase(data, type, warnings)
        when (type) {
            1, 15 -> base?.stroke?.let { elements += HandwritingElement(listOf(it), base.left, base.top, order, pageWidth, pageHeight) }
            2, 3 -> base?.text?.let { elements += RichTextElement(it, base.left, base.top, order) }
            7 -> base?.bindId?.let { elements += ImageElement(it, base.left, base.top, order) } ?: elements.add(UnknownElement(type, sourceOrder = order))
            8, 10, 11, 13, 14, 17, 19, 20, 21, 22, 23 -> elements += AttachmentElement(base?.bindId ?: "bind-$type", base?.left ?: 0f, base?.top ?: 0f, order)
            else -> { warnings += ParseWarning("unknown object type $type"); elements += UnknownElement(type, sourceOrder = order) }
        }
        if (childCount > 0) warnings += ParseWarning("object type $type has nested children that were not decoded")
    }
    private data class Base(val left: Float, val top: Float, val bindId: String?, val stroke: Stroke?, val text: String?)
    private fun parseObjectBase(data: ByteArray, type: Int, warnings: MutableList<ParseWarning>): Base? {
        return try { if (data.size < 45) null else { val r = LittleEndianReader(data); r.i32(); if (r.u16() != 0) null else { val varOffset = r.i32(); val flagLen = r.u8(); r.u16(); if (flagLen > 2) r.skip(flagLen - 2); r.u8(); r.i32(); r.i32(); val uuidLen = r.i16(); if (uuidLen in 1..64 && uuidLen <= r.remaining) r.bytes(uuidLen) else if (uuidLen > 64) r.skip(minOf(uuidLen, r.remaining)); r.i64(); val left = r.f64(); val top = r.f64(); val right = r.f64(); val bottom = r.f64(); r.i32(); r.u8(); val stroke = if (type == 1 || type == 15) StrokeParser.parse(data.copyOfRange(varOffset.coerceIn(0, data.size), data.size), left.toFloat(), top.toFloat(), right.toFloat(), bottom.toFloat(), warnings) else null; val text = if (type == 2 || type == 3) TextObjectParser.parseText(data.copyOfRange(r.position.coerceIn(0, data.size), data.size)) else null; val bindId = findBindId(data); Base(left.toFloat(), top.toFloat(), bindId, stroke, text) } } }
        catch (e: Exception) { warnings += ParseWarning("object $type base: ${e.message}"); null }
    }
    private fun findBindId(data: ByteArray): String? { for (i in 0..data.size - 4) { val n = ByteBuffer.wrap(data, i, 4).order(ByteOrder.LITTLE_ENDIAN).int; if (n in 0..1_000_000) return n.toString() }; return null }
}

object TextObjectParser {
    fun parse(bytes: ByteArray): RichTextElement = RichTextElement(parseText(bytes) ?: "")
    fun parseText(bytes: ByteArray): String? = runCatching { val r = LittleEndianReader(bytes); r.longUtf16() }.getOrNull()?.takeIf { it.isNotBlank() } ?: bytes.toString(Charsets.UTF_8).takeIf { it.any { c -> c.isLetterOrDigit() } }
}
object ImageObjectParser { fun parse(bindId: String): ImageElement = ImageElement(bindId) }
object StrokeParser {
    fun parse(bytes: ByteArray, left: Float = 0f, top: Float = 0f, right: Float = 1f, bottom: Float = 1f, warnings: MutableList<ParseWarning> = mutableListOf()): Stroke? {
        return try { if (bytes.size < 64) null else { val padded = bytes.size >= 32 && bytes.copyOfRange(16, 32).all { it == 0.toByte() }; val pointOffset = if (padded) 50 else 34; val deltaOffset = if (padded) 76 else 60; if (pointOffset + 4 > bytes.size) null else { val count = ByteBuffer.wrap(bytes, pointOffset, 4).order(ByteOrder.LITTLE_ENDIAN).int; if (count !in 1..SdocxLimits.MAX_STROKE_POINTS) throw BoundsException("stroke point count $count exceeds limit"); val points = ArrayList<StrokePoint>(minOf(count, SdocxLimits.MAX_STROKE_POINTS)); var x = 0f; var y = 0f; points += StrokePoint(0f, 0f); var offset = deltaOffset; repeat(count - 1) { if (offset + 4 <= bytes.size) { x += fixed5_5(ByteBuffer.wrap(bytes, offset, 2).order(ByteOrder.LITTLE_ENDIAN).short.toInt() and 0xffff); y += fixed5_5(ByteBuffer.wrap(bytes, offset + 2, 2).order(ByteOrder.LITTLE_ENDIAN).short.toInt() and 0xffff); offset += 4; if (x.isFinite() && y.isFinite()) points += StrokePoint(x, y) else throw BoundsException("non-finite stroke coordinate") } }; if (points.size < 2) null else { val minX = points.minOf { it.x }; val minY = points.minOf { it.y }; val abs = points.dropLast(minOf(2, points.size - 1)).map { StrokePoint(it.x - minX + left, it.y - minY + top) }; Stroke(abs, width = 2f) } } } }
        catch (e: Exception) { warnings += ParseWarning("stroke: ${e.message}"); null }
    }
    private fun fixed5_5(word: Int): Float { val fraction = word and 0x1f; val integer = word shr 5 and 0x1f; val value = integer + fraction / 32f; return if (word and 0x8000 != 0) -value else value }
}
