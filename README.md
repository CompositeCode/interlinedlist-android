# InterlinedList — Android

The native Android client for **[InterlinedList](https://interlinedlist.com)** — *"Productivity, Connected."*
A social platform for curating, sharing, and syncing lists, documents, and messages.

This client talks to the InterlinedList REST API and is built **offline-first** (a Room cache
reconciled via the API's sync endpoints), targeting **full API parity** delivered in phases.
See [`docs/ROADMAP.md`](docs/ROADMAP.md) for the build-out plan and phase breakdown.

## Tech stack

Jetpack Compose · Material 3 · Hilt · Retrofit/OkHttp · kotlinx.serialization · Room ·
Coroutines/Flow · WorkManager · Coil. Multi-module Gradle build with a Kotlin DSL and a
version catalog (`gradle/libs.versions.toml`).

```
:app                  entry, DI graph, navigation host, theme
:core:model           domain models
:core:common          result/error types, dispatchers, session contract
:core:designsystem    brand theme (Play typeface, brand palette), logo composables
:core:network         Retrofit API, OkHttp, Bearer auth interceptor, DTOs
:core:database        Room database, DAOs, entities
:core:datastore       encrypted session store (il_tok_ token)
```

## Prerequisites

- **JDK 17** (Android Gradle Plugin requirement)
- **Android SDK** with platform `android-35`, build-tools `35.0.0`, platform-tools
- **Android Studio** (latest stable) recommended for development, or the SDK command-line
  tools for headless builds
- A `local.properties` at the repo root pointing at your SDK (not committed):

  ```properties
  sdk.dir=/absolute/path/to/Android/sdk
  ```

## Build & test

```bash
# Build the debug APK
./gradlew :app:assembleDebug

# Run unit tests
./gradlew testDebugUnitTest

# Run instrumented tests (requires a connected device or running emulator)
./gradlew connectedDebugAndroidTest
```

> If your default JDK is newer than 17, point Gradle at a JDK 17 for the build, e.g.
> `JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew :app:assembleDebug`.

The debug APK is written to `app/build/outputs/apk/debug/`.

## API & authentication

The app authenticates with `POST /api/auth/sync-token` (email + password) to obtain a bearer
token (`il_tok_…`), stored in `EncryptedSharedPreferences` and attached to every request as
`Authorization: Bearer <token>`. For manual / end-to-end testing you'll need an InterlinedList
account. API docs: <https://interlinedlist.com/help/api>.

## Branding

Brand assets live in [`brand/`](brand/) (from the official kit). Colours: Ocean Blue `#0F4C5F`,
Emerald `#34A56D`, Amber Gold `#F9AF36`. Typeface: **Play**. The logo is used as-is — never
recoloured or distorted.

## License

See [LICENSE](LICENSE).
