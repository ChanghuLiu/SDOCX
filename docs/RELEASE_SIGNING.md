# Release signing

The Google Play upload key is kept outside this repository.

- Keystore path: `~/.android-keystores/notes-escape-upload.jks`
- Local signing config: `~/.config/notes-escape/signing.properties`
- Alias: `notes_escape_upload`

The Gradle release build reads the local signing configuration only when that
file exists and contains the required properties. Debug builds, tests, lint,
and source-only development do not require it. The configuration file and
keystore are intentionally excluded from Git and must never be copied into the
repository or placed on a public CI log.

This keystore is the Google Play upload key. Google Play App Signing should
manage the distribution app-signing key. Keep secure backups of the upload key;
if it is lost, Google provides an upload-key reset process through Play
Console, subject to its current account procedures.

This document intentionally contains no passwords or private-key material.

## Native debug symbols

Release builds use `debugSymbolLevel = "SYMBOL_TABLE"` so that available native
symbol tables can be included with the AAB for Google Play crash symbolication.
This setting applies to the release build only. Third-party native libraries
that were already stripped upstream cannot have native debug symbols recovered
by this project.

For versionCode 4, the native code is supplied by
`androidx.graphics:graphics-path:1.0.1`, transitively through
`androidx.compose.ui:ui-graphics:1.9.0`. It packages
`libandroidx.graphics.path.so` for `arm64-v8a`, `armeabi-v7a`, `x86`, and
`x86_64`. AGP reported that native debug metadata had already been stripped
from each ABI, so no `native-debug-symbols.zip` was generated and no native
symbol metadata was added to the AAB. Play can only symbolicate this library if
the dependency publisher supplies an unstripped build or a matching symbol
file; the project cannot reconstruct those symbols locally.
