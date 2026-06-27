# Development setup

## Prerequisites

- **JDK 17** (the build targets JVM 17). Verify with `java -version`.
- **Android SDK** with platform **API 35** and build-tools 35.
- **Android Studio Ladybug (2024.2)** or newer is recommended; the CLI works too.

Set `ANDROID_HOME` (or create `local.properties` with `sdk.dir=/path/to/Android/sdk`).

## First build

```bash
git clone https://github.com/Defuuls/Orbin.git
cd Orbin
./gradlew help          # downloads the Gradle distribution and warms caches
./gradlew assembleDebug # builds the debug APK
```

## Everyday commands

| Task | Command |
| --- | --- |
| Debug APK | `./gradlew assembleDebug` |
| Install on device | `./gradlew :app:installDebug` |
| All unit tests | `./gradlew test` |
| One module's tests | `./gradlew :domain:test` |
| Instrumented tests | `./gradlew connectedDebugAndroidTest` |
| Screenshot tests | `./gradlew verifyRoborazziDebug` |
| Record screenshots | `./gradlew recordRoborazziDebug` |
| Lint/format | `./gradlew ktlintCheck` / `./gradlew ktlintFormat` |
| Static analysis | `./gradlew detekt` |
| Compose compiler metrics | add `-Porbin.enableComposeCompilerReports=true` |

## Build configuration knobs

Gradle properties (set in `gradle.properties` or with `-P`):

- `orbin.warningsAsErrors=true` — treat Kotlin warnings as errors (CI uses this).

## Release signing (local)

Release builds are signed from a keystore referenced by `keystore.properties` (git-ignored):

```properties
storeFile=/absolute/path/orbin-release.jks
storePassword=…
keyAlias=…
keyPassword=…
```

In CI these come from repository secrets; see
[docs/architecture/ci-cd.md](architecture/ci-cd.md).

## Troubleshooting

- **`SDK location not found`** — set `ANDROID_HOME` or create `local.properties`.
- **Configuration cache errors after editing build logic** — run with `--no-configuration-cache`
  once, or delete `.gradle/configuration-cache`.
- **KSP/Hilt stale generation** — `./gradlew clean` then rebuild.
