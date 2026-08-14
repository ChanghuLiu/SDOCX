# Public fixture conversion results

Source: `bxff/samsung-notes-format/sdocxFiles`, MIT, upstream commit `6f4b92abc1f7f5f5ccc152159d66bba0a4922cf6`.

The five fixtures were run through the Kotlin `SdocxParser` and `ArchiveExporter`. Each produced a non-empty Markdown note, a non-empty handwriting SVG, both migration reports, and no ZIP path traversal. The public set contains handwriting/media samples; typed text and image-object counts are therefore `NOT PRESENT`, not inferred as failures.

| fixture | title | text | pages | images | media | handwriting | markdown | SVG | result |
|---|---|---|---:|---:|---:|---:|---|---|---|
| `Eg walker O(n^2) case_250527_190920.sdocx` | Eg walker O(n^2) case | NOT PRESENT | 1 | 0 | 1 | 47 strokes | PASS | PASS | PASS |
| `Mako OT N problem still exists Minimal dub_260129_190327.sdocx` | Mako OT N problem still exists. Minimal dub | NOT PRESENT | 1 | 0 | 1 | 6 strokes | PASS | PASS | PASS |
| `Mako OT N problem still exists_251002_005645 (1).sdocx` | Mako OT N problem still exists. | NOT PRESENT | 1 | 0 | 1 | 29 strokes | PASS | PASS | PASS |
| `ThisIsTheTitle_251009_012211.sdocx` | ThisIsTheTitle | NOT PRESENT | 1 | 0 | 1 | 1 stroke | PASS | PASS | PASS |
| `ThisIsTheTitle_251009_042302.sdocx` | ThisIsTheTitle | NOT PRESENT | 1 | 0 | 1 | 3 strokes | PASS | PASS | PASS |

Evidence checks: format version `4000`; timestamps were plausible 2025/2026 ISO instants; all stroke coordinates were finite; all five reports were `SUCCESS` with no warnings. The media entries resolve through `media/mediaInfo.dat` EOFX to the original `media/*@page_0000000.spi` bytes. These `.spi` assets are preserved as attachments because they are not ordinary image MIME payloads.
