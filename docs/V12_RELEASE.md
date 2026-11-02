# Notes Escape 1.2.8 Release

Status: submitted to Google Play Production; currently in review, not yet publicly available.

- Application ID: `com.notesescape.sdocx`
- Version name/code: `1.2.8 (15)`
- Base: merged SDOCX picker/viewer fix `1a5c99a` on `fix/notes-escape-sdocx-mime`
- Release-prep commit: `6ea28ac` on `release/notes-escape-1.2.8-code15`
- Release notes: `docs/PLAY_RELEASE_NOTES_1.2.8.md`

## Validation and submission

- Unit tests: `:app:testDebugUnitTest` passed, 24 tests, 0 failures/errors/skips.
- Debug lint: `:app:lintDebug` passed with 56 warnings and 0 errors; Release vital lint passed as part of the Release bundle build.
- Release signing gate: `:app:verifyReleaseSigning` passed.
- Release bundle: `:app:bundleRelease` passed.
- Release APK: `:app:assembleRelease` passed.
- APK metadata: `com.notesescape.sdocx`, version code 15, version name 1.2.8, min SDK 27, target SDK 36.
- AAB: `app/build/outputs/bundle/release/app-release.aab`, 4,067,134 bytes, SHA-256 `dac91caaff3246783781f1c1ff45ed102bae2e0376d29a3ae411213ee53f58a0`.
- APK: `app/build/outputs/apk/release/app-release.apk`, 2,439,561 bytes, SHA-256 `37f5452a29d6b58c06010b7cb57369777dde95a8890987bc641d8dfe0fb9b524`.
- Play upload-certificate SHA-256 fingerprint: `13a16eefbfa4c9766700196fe574b7c112c4ca143b776ca79420d2f24f18b045`; matches the public fingerprint recorded for V1.1.
- Play Console before submission: production `14 (1.2.7)`, 100% rollout. Android Publisher API accepted the uploaded bundle with versionCode 15 and SHA-256 matching the local AAB; edit validation succeeded.
- Submitted Production release `15 (1.2.8)` with `completed` rollout (100%) and English release notes. Android Publisher edit commit returned HTTP 200. Play Console now shows `Release 15 (1.2.8) in review` across 178 countries/regions.
- Device runtime check: installed and launched the code 15 `.debug` variant on SM-G950W Android 9, SM-A115W Android 12 and T434J Android 16. The official Play package remained installed on all three. This is not a Play-signed update test. No in-place release APK installation was attempted because the local APK is signed by the upload key, while Play installs use Google's app-signing key.

## Remaining release gate

- Google review and public rollout are pending. Do not mark publicly available until Play Console changes from `in review` to a live/available release.
- Play Console surfaced a recommended outdated `androidx.fragment:fragment` SDK notice for the new release. Console did not block submission; assess and schedule dependency upgrade separately.
- No additional Play metadata, listing assets, pricing, or app permissions were changed in this release submission.
