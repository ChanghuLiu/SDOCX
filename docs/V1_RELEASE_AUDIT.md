# V1 Release Candidate audit

Audit basis: current source tree, merged manifests, Gradle configuration, JVM
tests, Android instrumentation tests, Samsung Android validation, and license
notices.

| Area | Status | Evidence / notes |
|---|---|---|
| Application ID | PASS | `com.notesescape.sdocx` |
| Version | PASS | versionCode `1`, versionName `1.0` |
| compileSdk / targetSdk | PASS | compileSdk 36.1, targetSdk 36 |
| minSdk | PASS | minSdk 26 |
| Release debuggable | PASS | Android release default is `debuggable=false`; no release override |
| Release optimization | PASS | Explicitly disabled in V1 (`optimization.enable = false`); document and revisit before a production hardening release |
| Signing | PARTIAL | No signing keys or credentials are committed; the generated AAB is unsigned and requires production signing before Play submission |
| Permissions | PASS | No INTERNET, READ_EXTERNAL_STORAGE, WRITE_EXTERNAL_STORAGE, or MANAGE_EXTERNAL_STORAGE |
| RTL | PASS | `android:supportsRtl="true"`; Arabic resource/instrumentation coverage |
| Network access | PASS | No network dependency or backend; no Internet permission |
| Third-party SDKs | PASS | AndroidX, Jetpack Compose, and DocumentFile only; no analytics or ads |
| App icon | PASS | Launcher and round launcher resources exist for supported densities |
| Launch theme | PASS | Material theme and launcher activity are configured |
| Crash handling | PASS | Per-note parse failures are isolated and reported; cancellation has a distinct result |
| Large file handling | PASS | Bounded SAF-to-cache streaming, source limit, one-note-at-a-time processing |
| SAF behavior | PASS | OpenMultipleDocuments, OpenDocumentTree, CreateDocument, recursive folder enumeration, share intents |
| Temporary cache cleanup | PASS | Startup cleanup plus completion/cancel/failure cleanup |
| Locked/corrupt handling | PASS | Locked notes are skipped without decryption; corrupt notes are reported |
| Batch isolation | PASS | Corrupt-file batch test and real five-note batch validation |
| Localization | PASS | English, French, Arabic, Spanish, Portuguese, Korean resources; Compose uses string resources |
| Privacy consistency | PASS | In-app copy, PRIVACY.md, public HTML, and manifest agree with local-only behavior |
| License notices | PASS | `THIRD_PARTY_NOTICES.md` covers included MIT material and attribution |
| GPL contamination | PASS | No GPL source, fixtures, or generated outputs tracked; Windows corpus is documented as transient evidence only |
| Debug-only dependencies | PASS | Compose tooling and test manifest are debug/test configurations only |
| Release artifacts | PASS | `./gradlew bundleRelease` produced `app/build/outputs/bundle/release/app-release.aab`; `jarsigner` verified it is unsigned; no Play upload is performed |
| Rich-text compatibility | PARTIAL | Controlled Android note did not contain enabled native formatting ranges; do not claim perfect formatting |
| Cross-version compatibility | PARTIAL | Real-device evidence covers one Samsung Android build only |
| Page-layout recreation | PARTIAL | Markdown/asset migration is the goal; original page layout is not guaranteed |

## Release decision

No known privacy, permission, licensing, or build blocker remains for generating
a V1 release candidate. Store submission still requires a production signing
key, Play Console setup, store assets, content declarations, and a final manual
review of the listing and device behavior. Rich-text and cross-version limits
must remain visible in product claims.
