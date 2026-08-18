# Notes Escape: SDOCX

Notes Escape: SDOCX is a local Android migration tool for converting Samsung Notes `.sdocx` exports into Portable Markdown or an Obsidian Vault ZIP. It uses Android Storage Access Framework streams and has no account, server, analytics, ads, or network processing.

Use **Select folder** to automatically preserve the selected filesystem hierarchy beneath `Notes/` and `Attachments/`; the app presents this as informational status, not a fake toggle. Selecting individual files cannot reliably recover their original folders, so those files are exported at the vault root. Obsidian metadata front matter is optional, and attachment links are vault-root-relative wikilinks. This is filesystem hierarchy preservation; it does not claim Samsung Notes internal notebook/folder recovery. See [docs/OBSIDIAN_EXPORT.md](docs/OBSIDIAN_EXPORT.md).

Build with `./gradlew test`, `./gradlew lint`, and `./gradlew assembleDebug`.

V1 intentionally does not OCR or decrypt locked notes. The public fixture results are recorded in [docs/FIXTURE_RESULTS.md](docs/FIXTURE_RESULTS.md). See [docs/V1_SCOPE.md](docs/V1_SCOPE.md), [docs/COMPATIBILITY.md](docs/COMPATIBILITY.md), and [PRIVACY.md](PRIVACY.md).

The RC1 Android flow uses SAF for files, recursively discovers `.sdocx` files
from selected folders, caches one source at a time with bounded streaming, and
streams original files and media into the ZIP. Samsung Android validation is
complete on a Samsung SM-G973W running Android 12 with Samsung Notes 4.4.29.23:
plain text, image, mixed handwriting, and PDF attachment tests passed. Rich-text
semantic preservation remains partially verified, and these results do not
claim compatibility with every Samsung Notes version.

The app is an independent migration utility and is not affiliated with,
endorsed by, or sponsored by Samsung. See the in-app About / Help and Privacy
sections, [docs/COMPATIBILITY.md](docs/COMPATIBILITY.md),
[docs/SAMSUNG_ANDROID_TEST_RESULTS.md](docs/SAMSUNG_ANDROID_TEST_RESULTS.md),
and [docs/V1_RELEASE_AUDIT.md](docs/V1_RELEASE_AUDIT.md).

Transient Samsung Notes for Windows black-box results are recorded in
[docs/WINDOWS_SAMPLE_COMPATIBILITY.md](docs/WINDOWS_SAMPLE_COMPATIBILITY.md).
They are separate compatibility evidence and do not establish universal
Samsung Notes Android compatibility.
