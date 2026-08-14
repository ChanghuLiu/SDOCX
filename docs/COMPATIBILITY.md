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

## NOT VERIFIED

- Universal compatibility across Samsung Notes builds.
- All rich-text, image, audio, PDF, video, table, formula, and custom object variants.
- Locked/encrypted exports from every Samsung Notes version; V1 never decrypts them.
