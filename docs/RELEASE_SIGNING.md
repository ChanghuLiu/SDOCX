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
