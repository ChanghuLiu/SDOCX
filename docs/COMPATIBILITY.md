# Compatibility

## VERIFIED

- Android baseline: Kotlin, Compose Material 3, compileSdk/targetSdk 36, minSdk 26.
- No Internet or broad-storage permissions; SAF file and recursive tree enumeration are implemented and source files are processed through one-note cache files.
- Five public MIT fixtures from `bxff/samsung-notes-format/sdocxFiles` at commit `6f4b92abc1f7f5f5ccc152159d66bba0a4922cf6` were converted locally.
- Fixture titles, one-page structure, one media registry binding, stroke counts, finite coordinates, Markdown, SVG, and migration reports are covered by JVM tests.

## PARTIALLY VERIFIED

- Typed text and rich-text spans have conservative parsers and synthetic Markdown coverage, but the five public fixtures contain handwriting samples rather than typed page text.
- Image/object bind-ID resolution is implemented against the MIT media registry layout and media is streamed from temporary files; no public fixture in this set contains a decoded image object.
- End-tag timestamps and format version 4000 are recovered from the five fixtures. App-version, direction, and encryption metadata are version-tolerant but not fully verified across Samsung Notes releases.
- Recursive SAF enumeration is implemented with case-insensitive matching, deterministic ordering, inaccessible-child tolerance, and no traversal outside the selected tree; provider behavior still requires manual device testing.
- The Android UI now resolves English, French, Arabic, Spanish, Portuguese, and Korean resources. Arabic RTL configuration is covered by an instrumentation test, pending a connected device run.

### Samsung Notes for Windows evidence

- The transient 11-sample Windows corpus is documented in [WINDOWS_SAMPLE_COMPATIBILITY.md](WINDOWS_SAMPLE_COMPATIBILITY.md). It is black-box evidence only; the GPL-3.0 parser and sample files are not part of this project.
- Windows-produced samples now provide PARTIALLY VERIFIED evidence for typed text, paragraph ordering, bold/italic/underline/strikethrough ranges, lists, checkbox state, and indentation. `cupcake` and `fire_and_ice_F` generated semantically correct Markdown; `mushroom` generated typed Markdown plus resolved JPEG/unknown media and had one bounded stroke warning.
- Windows samples do not establish Samsung Notes Android/mobile compatibility. The Android/mobile typed-text, rich-text, and decoded-image paths remain PARTIALLY VERIFIED until the five real-device notes in `docs/REAL_DEVICE_TEST_PLAN.md` are exported and compared.
- The Windows corpus showed no decoded `ImageElement`; resolved JPEG media in `mushroom` is recorded as media-registry/copy-through evidence rather than image-object evidence.

## NOT VERIFIED

- Universal compatibility across Samsung Notes builds.
- Exact compatibility of every Samsung Notes Android/mobile binary object variant.
- All rich-text, image, audio, PDF, video, table, formula, and custom object variants.
- Locked/encrypted exports from every Samsung Notes version; V1 never decrypts them.
