# Google Play Data Safety answers

These answers are based on the current source code, manifest, dependencies, and
release configuration. They should be reviewed again if the app gains a new
SDK, permission, backend, or telemetry feature.

## Data collected

No.

The app does not collect personal data, diagnostics, analytics, identifiers, or
files. It has no account, analytics SDK, advertising SDK, backend, or Internet
permission.

## Data shared

No.

User-selected `.sdocx` files and generated ZIP archives stay on the device. The
app does not upload or share them automatically.

## Account

No account is required or supported.

## Ads and analytics

No advertising and no analytics.

## Files and device storage

The user explicitly selects source files or a folder through Android Storage
Access Framework and explicitly chooses the ZIP destination. One source note at
a time may be copied into app-local temporary cache for bounded processing;
temporary conversion files are cleaned after completion, cancellation, or
failure and stale cache is cleaned at app startup. The app does not request
broad storage permissions.

## Network

No Internet permission is declared and there is no network processing.
