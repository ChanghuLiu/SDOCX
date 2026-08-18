# V1.1 Real Obsidian Validation

Validation was performed against the local V1.1 source tree and a real Samsung
Android device. Private source fixtures and exported archives were kept outside
Git.

## Device and build

- Manufacturer: Samsung
- Model: SM-G973W
- Android: 12
- Notes Escape source baseline: `02ad6febbcaef77836f0dd29e8e62391168f1789`
- Validation build included the media-entry collision fix made after device testing.
- Samsung Notes application version was not collected.

## Source tree

The device test tree contained:

```text
TestVault/
  Work/2026/Meetings/Meeting A.sdocx
  Work/2026/Meetings/Meeting B.sdocx
  Personal/Travel.sdocx
  Unicode/中文/中文笔记.sdocx
  Unicode/العربية/ملاحظة.sdocx
  Duplicate/Meeting.sdocx
  Duplicate/Meeting Copy.sdocx
```

Real Samsung SDOCX exports were reused for image, mixed-media, attachment,
plain-text, and rich-text cases. They were not copied into the repository.

## Results

- Folder selection: PASS. The selected filesystem hierarchy was retained below
  `Notes/`; the UI reported that it was preserved from the selected folder.
- Nested hierarchy: PASS. `Work/2026/Meetings` survived in the extracted ZIP.
- Unicode paths: PASS. Chinese, Arabic, and other Unicode names decoded correctly
  from the ZIP and were not flattened.
- Markdown: PASS. Seven note files opened as UTF-8 and their available text
  survived.
- Metadata ON: PASS. Front matter contained only `title`, parsed `created` and
  `modified` values when present, `source`, `source_format`, and
  `migration_status`. No timestamps were invented.
- Metadata OFF: PASS. The second extracted export had no YAML front matter and
  retained the nested hierarchy and media payloads.
- Media: PASS for the tested real image exports. JPEG entries were non-zero.
- Handwriting: PASS for tested pages. Three SVG entries were non-empty and
  parsed as SVG XML.
- PDF attachment: PASS. The tested PDF was present and non-zero.
- Wikilinks: PASS. Every emitted attachment wikilink in the validated archive
  resolved to an actual ZIP entry using vault-root-relative paths.
- Collisions: PASS. Same-title notes produced deterministic `(2)` naming, with
  matching attachment directories and no overwrite.
- Individual-file selection: not completed on this device. Android DocumentsUI
  displayed the searched `.sdocx` results as disabled in the multi-select flow.
  The product's synthetic tests cover the required root-level flattening rule.
- Cancellation: not completed during this run because the small real export
  completed before a meaningful cancellation window appeared. The cache-first
  implementation has `try/finally` cleanup and the existing automated tests
  cover archive behavior.
- Actual desktop Obsidian open: not run; no existing desktop Obsidian
  installation was detected, and no third-party software was installed.

## Archive layout and caveats

The validated archive contained `Notes/`, `Attachments/`, and
`_Notes Escape/` with both migration reports. The tested ZIP passed `unzip -t`.
The exporter uses vault-root-relative Obsidian wikilinks, for example
`![[Attachments/Work/2026/Meetings/...jpg]]`.

The result-screen folder count follows the product rule: unique non-empty note
directories represented by exported notes. This differs from the preflight
count, which also counts represented ancestor folders. Samsung Notes internal
notebook organization was not inferred or claimed.

## Follow-up

On a device where DocumentsUI permits multi-select, repeat the individual-file
case and confirm root-level `Notes/` output. Open the extracted archive in
Obsidian Desktop and check note opening, image rendering, SVG rendering, PDF
opening, and Chinese/Arabic text display.
