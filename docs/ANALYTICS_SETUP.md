# Firebase Analytics setup

This app uses Google Analytics for Firebase only when a Firebase project is
configured. The SDK events are anonymous to the app: users do not create an
account or sign in.

1. Create a Firebase project for SDOCX and enable Google Analytics.
2. Register an Android app with package name `com.notesescape.sdocx`.
3. Download its `google-services.json` and place it at `app/google-services.json`.
4. Build and publish the updated app, then check Firebase Analytics Realtime
   and Events reports.

The generated config contains project and app identifiers, not private keys.
Do not place credentials or signing secrets in this file. The PR intentionally
does not include a generated Firebase config because the Firebase project has
not yet been created. Without that config, events are safely ignored.

Events are fixed app-workflow events. Never add document names, paths, content,
URIs, or free-text exceptions as event values. Review the Google Play Data
safety answers and the public privacy policy before releasing a configured
build.
