# Samsung Notes Android test results

This document records black-box interoperability results from five notes
created in Samsung Notes on one test device. The private `.sdocx` files and
generated ZIP remain outside this repository and are not distributed here.

## Device

- Model: Samsung SM-G973W
- Android: 12
- Samsung Notes package: `com.samsung.android.app.notes`
- Samsung Notes versionName: `4.4.29.23`
- Samsung Notes versionCode: `442923000`
- Test date: 2026-08-14
- SDOCX format version recovered from all five files: `4000`
- Samsung Notes app version inside SDOCX metadata: not present (`null`)

## Results

| Test | Pages | Typed text | Rich spans | Images / media | Handwriting | Attachment handling | Result |
|---|---:|---|---|---|---|---|---|
| A — Plain Text | 2 | 19 blocks; 87 recovered characters; all six required lines exact | none present | 0 / 0 | 0 | none | PASS |
| B — Rich Text | 2 | 20 blocks; 131 recovered characters | 0 bold, 0 italic, 0 underline, 0 strike; 0 lists; 0 checkboxes | 0 / 1 unknown `.spi` media | 0 | unknown media copied/reported | PARTIAL |
| C — Image | 2 | 3 blocks; before/after text recovered | none present | 0 page image objects / 1 JPEG media binding | 0 | registry media copied and linked | PASS |
| D — Mixed | 2 | 16 blocks; English, Arabic, and Chinese exact | none present | 0 page image objects / 2 media bindings (JPEG + `.spi`) | 6 strokes, 266 points; SVG has 6 paths | unknown `.spi` copied as attachment | PASS |
| E — Attachment | 3 parser page records; Samsung UI showed a two-page PDF import | 1 block | none present | 0 page image objects / 1 PDF media binding | 0 | valid PDF copied and linked; Samsung imported it as pages | PASS |

The parser reported `SUCCESS` for all five archives. Test B is still marked
`PARTIAL` because the created note did not contain enabled structured formatting
ranges, so it is not evidence that Android rich-text spans are decoded. C and D
pass the tested artifact criteria, while exact Android `ImageElement` decoding
remains a separate PARTIALLY VERIFIED capability because these exports exposed
the JPEG through the media registry and an embedded replacement character.

## Output validation

- The batch ZIP contained five Markdown notes, five original SDOCX files,
  copied JPEG/PDF/unknown media where present, one handwriting SVG, and both
  migration reports.
- Test A Markdown contains exactly these requested lines: `Hello Samsung
  Notes`, `Été à Montréal`, `这是一个三星笔记测试`, `삼성 노트 테스트입니다`,
  `هذه ملاحظة اختبار`, and `Meeting ✅ 🔒 📱`.
- Test C Markdown contains both typed paragraphs and a link to the copied
  JPEG. Test D Markdown contains all three typed language lines, the JPEG link,
  and the handwriting SVG link. Test E Markdown contains the PDF attachment
  link.
- The copied JPEG bytes from the two SDOCX media streams matched each other;
  the Samsung Notes embedded JPEG differs from the pushed source image,
  indicating device-side image normalization before export. The exporter then
  copied the exported media bytes unchanged.
- The copied PDF passed local PDF validation, retained its original bytes, and
  was not executed or opened by the migration tool.
- The SVG is valid XML with `viewBox="0 0 1080.0 1527.0"`, finite coordinates,
  round stroke styling, and six visible paths.

## Remaining limitations

- This evidence covers one Samsung Notes Android build only; it does not claim
  compatibility with all Samsung Notes versions or device models.
- The rich-text note needs a separately-created fixture with native enabled
  formatting ranges to verify Android bold, italic, underline, strikethrough,
  list, checkbox, and hyperlink decoding.
- Android image exports in this batch did not expose a page `ImageElement`; the
  media-registry fallback now preserves and links the image, but exact object
  ordering and bind-object decoding need another controlled Android fixture.
- Samsung Notes appVersion was not embedded in these records.
