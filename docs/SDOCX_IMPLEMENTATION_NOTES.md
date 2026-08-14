# SDOCX implementation notes

SDOCX is treated as a ZIP. Archive entry names are normalized and checked for traversal. `SdocxLimits` bounds archive entries (4096), each decompressed entry (100 MiB), total decompressed content (512 MiB), pages (2048), objects per page (100,000), object payloads (8 MiB), and stroke points (500,000). Exceeded limits become warnings or a corrupt result; they are not silently ignored.

The binary page path follows the MIT reference layout: little-endian note metadata, `pageIdInfo.dat`, EOF/EOFX media registry entries, page layer/object records, common object headers, and Samsung 5.5 fixed-point delta stroke payloads. Typed text and later rich-text span variants are parsed conservatively; unsupported objects remain warnings/placeholders rather than guessed content.
