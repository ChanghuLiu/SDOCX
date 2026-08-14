# RC1 real-device test plan

Run these exactly five tests on a Samsung device after installing the debug
APK. Export each note from Samsung Notes as `.sdocx`, import it into Notes
Escape, and save one ZIP per test. Do not infer support from a successful file
selection alone; inspect the Markdown, assets, originals, and both migration
reports.

## TEST A — Plain text

Title: `SDOCX Plain Text`

Body:

```text
Hello Samsung Notes
Été à Montréal
这是一个三星笔记测试
삼성 노트 테스트입니다
هذه ملاحظة اختبار
Meeting ✅ 🔒 📱
```

Expected artifacts and validation:

- `notes/SDOCX Plain Text.md` exists and front matter has the title.
- The six body lines are present byte-for-byte, including accents, Arabic,
  CJK, Korean, and surrogate-pair emoji.
- `migration-report.json` marks the note `SUCCESS` or `PARTIAL` with a
  truthful warning; it must not claim success if text is missing.
- The original `.sdocx` is present when the default original option is enabled.

## TEST B — Rich text

Title: `SDOCX Rich Text`

Include normal text, bold, italic, underline, strikethrough, a bullet list, a
numbered list, a checkbox, and a hyperlink if Samsung Notes permits one.

Expected artifacts and validation:

- Markdown contains `**bold**`, `*italic*`, `~~strikethrough~~`, list markers,
  and `- [ ]` or `- [x]` when the checkbox is reliably decoded.
- The link is emitted as `[label](url)` when its URL is available.
- Underline is preserved only in Obsidian Rich through minimal HTML; Clean
  Markdown may omit purely visual underline.
- Any missing semantic range appears as `PARTIAL`/a warning rather than being
  silently described as complete.

## TEST C — Image

Title: `SDOCX Image`

Include a typed paragraph, one JPEG/photo, and a typed paragraph after the
image.

Expected artifacts and validation:

- Both typed paragraphs appear in page order.
- The image’s original bytes are copied under `assets/` without recompression;
  JPEG magic bytes and extension agree.
- The Markdown image reference points to the copied asset and the report image
  count is accurate.
- The original `.sdocx` and both report files exist.

## TEST D — Mixed handwriting

Title: `SDOCX Mixed`

Include typed English, typed Arabic, typed Chinese, handwriting strokes, and
one image.

Expected artifacts and validation:

- Typed text is preserved without reversing Arabic or normalizing user content.
- At least one non-empty `handwriting_page_XX.svg` is valid XML, has a finite
  viewBox, and contains visible path data.
- The image is copied with original bytes, and page-order Markdown references
  both the image and handwriting asset.
- If one object type cannot be decoded, the report is `PARTIAL` with a warning.

## TEST E — Attachment

Title: `SDOCX Attachment`

Include a PDF attachment and/or an audio recording where Samsung Notes
supports it.

Expected artifacts and validation:

- The attachment is copied under `assets/attachments/` with no execution or
  re-encoding.
- PDF/audio magic bytes, extension, and report attachment count are checked.
- Unknown media is still copied and reported as an attachment.
- A locked or unsupported attachment never aborts another note in the batch.

Record device model, Android version, Samsung Notes version, export date,
selected options, report status, warnings, and the exact ZIP contents for each
test. These five notes are the next evidence needed to move typed text,
rich-text spans, decoded images, and attachments from PARTIALLY VERIFIED.
