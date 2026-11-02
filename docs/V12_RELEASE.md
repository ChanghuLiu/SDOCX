# Notes Escape 1.2.8 Release Candidate

Status: release preparation only; not uploaded to Google Play.

- Application ID: `com.notesescape.sdocx`
- Version name/code: `1.2.8 (15)`
- Base: merged SDOCX picker/viewer fix `1a5c99a` on `fix/notes-escape-sdocx-mime`
- Release notes: `docs/PLAY_RELEASE_NOTES_1.2.8.md`

## Validation

- Unit tests: `:app:testDebugUnitTest` passed, 24 tests, 0 failures/errors/skips.
- Debug lint: `:app:lintDebug` passed with 56 warnings and 0 errors; Release vital lint passed as part of the Release bundle build.
- Release signing gate: `:app:verifyReleaseSigning` passed.
- Release bundle: `:app:bundleRelease` passed.
- Release APK: `:app:assembleRelease` passed.
- APK metadata: `com.notesescape.sdocx`, version code 15, version name 1.2.8, min SDK 27, target SDK 36.
- AAB: `app/build/outputs/bundle/release/app-release.aab`, 4,067,134 bytes, SHA-256 `dac91caaff3246783781f1c1ff45ed102bae2e0376d29a3ae411213ee53f58a0`.
- APK: `app/build/outputs/apk/release/app-release.apk`, 2,439,561 bytes, SHA-256 `37f5452a29d6b58c06010b7cb57369777dde95a8890987bc641d8dfe0fb9b524`.
- Upload-certificate SHA-256 fingerprint: `13a16eefbfa4c9766700196fe574b7c112c4ca143b776ca79420d2f24f18b045`; matches the public fingerprint recorded for V1.1.
- Device smoke test of this Release candidate: NOT RUN. The locally signed APK uses the upload certificate; it cannot serve as an in-place update of a Play-installed package signed with Google Play's app-signing certificate. No device install was attempted.

## Release gates

- Play Console acceptance of version code 15: BLOCKED / not live-verified. Current availability was not confirmed in Play Console.
- Google Play upload and rollout: NOT DONE. This candidate was not uploaded or published.
- Do not replace or clear an existing Play installation during verification.
- Review the release notes and candidate in Play Console before any upload; upload and rollout remain distinct actions.
