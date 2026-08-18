# Notes Escape V1.1 Release

Release preparation for the existing Closed Testing track.

- Version name: `1.1`
- Version code: `6` (the next code after the documented/uploaded code 5)
- Source validation commit: `b2a438e3daa5a20e98c3d3a4159a4496b5665b77`
- Application ID: `com.notesescape.sdocx`
- Target SDK: 36

## Artifacts

- AAB: `app/build/outputs/bundle/release/app-release.aab`
- AAB size: 3,511,372 bytes
- AAB SHA-256: `92c086052de81342cfa591d394ae34856f48f50809f262fe35e303d141cfbdde`
- APK: `app/build/outputs/apk/release/app-release.apk`
- APK size: 2,001,119 bytes
- Release certificate SHA-256:
  `13a16eefbfa4c9766700196fe574b7c112c4ca143b776ca79420d2f24f18b045`

The release uses the external signing configuration documented in
`docs/RELEASE_SIGNING.md`; no signing secrets are stored here.

## Quality and device smoke test

The clean release gates passed: `test`, `lint`, `assembleRelease`, and
`bundleRelease`. The release APK installed on Samsung SM-G973W running Android
12 after removing the locally installed debug-signed package. The app launched;
Portable Markdown, Obsidian Vault, Select folder, About, and Privacy were
available. A folder import discovered seven real SDOCX files, reported zero
locked/corrupt files, exported an Obsidian ZIP, and displayed seven converted,
zero partial, and zero failed notes.

## Known limitations and warnings

- Samsung Notes internal notebook organization is not claimed; folder
  preservation is based on the filesystem tree selected through SAF.
- Vanilla Obsidian Desktop UI opening was not run because no official Ubuntu
  installation or local official package was available.
- The documented Google Play native-symbol warning is non-blocking: the
  AndroidX native libraries are pre-stripped upstream, so matching native
  symbols cannot be reconstructed locally.

## Closed Testing release notes

What's new:

- Added Obsidian Vault export.
- Preserves folder structure when importing a folder.
- Preserves images, handwriting SVGs, and attachments.
- Added optional note metadata/front matter.
- Improved export reliability and ZIP attachment handling.
