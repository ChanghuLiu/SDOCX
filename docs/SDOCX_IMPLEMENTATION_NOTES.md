# SDOCX implementation notes

SDOCX is treated as a ZIP. Archive entry names are normalized and checked for traversal. `SdocxLimits` bounds archive entries (4096), each decompressed entry (100 MiB), total decompressed content (512 MiB), pages (2048), objects per page (100,000), object payloads (8 MiB), and stroke points (500,000). Exceeded limits become warnings or a corrupt result; they are not silently ignored.

The binary page path follows the MIT reference layout: little-endian note metadata, `pageIdInfo.dat`, EOF/EOFX media registry entries, page layer/object records, common object headers, and Samsung 5.5 fixed-point delta stroke payloads. Typed text and later rich-text span variants are parsed conservatively; unsupported objects remain warnings/placeholders rather than guessed content.

## Independently validated Windows text records

The transient Samsung Notes for Windows black-box corpus independently confirmed
that a `note.note` text record can be recognized by a bounded length prefix,
UTF-16LE code-unit count, text payload, span vector, and paragraph vector. Span
records carry a bounded data size, type, UTF-16 range, interval value, and
property bytes. Paragraph records carry a bounded data size, paragraph type,
paragraph-index range, and property bytes.

The observed paragraph codes are interpreted conservatively: code `2` is
indent level, code `5` is list metadata, and code `6` is the ordinary parsing
state. Within code `5`, values `4`, `8`, and `2` correspond to numbered,
bullet, and checkbox records in the controlled corpus. Numbering values and
checkbox state are read from the record properties; checked items may also
carry an enabled strikethrough span. A zero list code is treated as default
formatting rather than guessed content.

Span types `5`, `6`, `7`, and `20` are mapped to bold, italic, underline, and
strikethrough only when their property flag is enabled. This matters because
Samsung serializes both enabled and disabled style ranges. Arbitrary UTF-16
occurrences are not emitted as user text. Records that fail the structural
bounds/confidence checks are ignored with the surrounding note preserved and
are not converted into binary-looking Markdown.

These observations are implementation notes from our raw-binary inspection
and controlled black-box results; no GPL parser implementation was used.

## RC1 memory and source lifecycle

The Android layer never materializes a SAF source into a `ByteArray`. It copies
one source at a time to `cacheDir/sdocx-conversion` with a 64 KiB buffer and a
512 MiB source limit. The pure JVM exporter reopens that cache file for parsing
and, when enabled, streams the original bytes directly into the output ZIP;
the cache file is deleted after the note and stale cache files are removed at
app startup.

Structural SDOCX entries remain bounded byte arrays because the binary parsers
need random access. Embedded media payloads are written to temporary files by
the archive reader and streamed by the exporter, so a batch does not retain all
media in heap memory. The same archive entry and total decompressed-size limits
continue to apply while media is spilled. Media temp files are deleted when a
parse result is consumed by the exporter or pre-flight inspection.
