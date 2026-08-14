# Notes Escape: SDOCX

Notes Escape: SDOCX is a local Android V1 prototype for converting Samsung Notes `.sdocx` exports into Markdown/Obsidian-compatible ZIP archives. It uses Android Storage Access Framework streams and has no account, server, analytics, ads, or network processing.

Build with `./gradlew test`, `./gradlew lint`, and `./gradlew assembleDebug`.

V1 intentionally does not OCR or decrypt locked notes. The public fixture results are recorded in [docs/FIXTURE_RESULTS.md](docs/FIXTURE_RESULTS.md). See [docs/V1_SCOPE.md](docs/V1_SCOPE.md), [docs/COMPATIBILITY.md](docs/COMPATIBILITY.md), and [PRIVACY.md](PRIVACY.md).
