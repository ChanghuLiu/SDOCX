# SDOCX implementation notes

SDOCX is treated as a ZIP. Archive entry names are normalized and checked for traversal. `SdocxLimits` bounds archive entries (4096), each decompressed entry (100 MiB), total decompressed content (512 MiB), pages (2048), objects per page (100,000), object payloads (8 MiB), and stroke points (500,000). Exceeded limits become warnings or a corrupt result; they are not silently ignored.

The binary page path follows the MIT reference layout: little-endian note metadata, `pageIdInfo.dat`, EOF/EOFX media registry entries, page layer/object records, common object headers, and Samsung 5.5 fixed-point delta stroke payloads. Typed text and later rich-text span variants are parsed conservatively; unsupported objects remain warnings/placeholders rather than guessed content.

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
