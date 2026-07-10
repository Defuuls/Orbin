# Developer Guide

How to build Orbin, what the toolchain looks like after the July 2026 AGP 9 upgrade, and how CI
and releases work. See also [[Architecture and Modules|Architecture-and-Modules]] and the
in-repo docs under [`docs/`](https://github.com/Defuuls/Orbin/tree/main/docs).

## Prerequisites

- **JDK 17** (the build targets JVM 17) — verify with `java -version`.
- **Android SDK** with platform **API 36** (`compileSdk`/`targetSdk` 36, `minSdk` 35).
- **Android Studio Ladybug (2024.2)** or newer recommended; the command line works too.
- Set `ANDROID_HOME`, or create `local.properties` with `sdk.dir=/path/to/Android/sdk`.

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

Build knobs: `orbin.warningsAsErrors=true` treats Kotlin warnings as errors (CI uses this).

## Toolchain and SDK notes (AGP 9 upgrade, July 2026)

The build was upgraded from AGP 8.13.2 / Gradle 8.14.3 / Kotlin 2.0.21 to:

| Component | Version | Notes |
| --- | --- | --- |
| AGP | **9.2.1** | 9.2.0 has an R8 `RecordTag` regression — stay on the patch release. |
| Gradle | **9.4.1** | AGP 9.2 minimum/default. |
| Kotlin (KGP) | **2.2.21** | AGP 9 bundles/expects KGP 2.2.10+. |
| KSP | **2.3.6** | Standalone-versioned since 2.3.0; 2.3.1+ required for AGP 9's built-in Kotlin. |
| Room | **2.8.4** | 2.7.0 was the first release with proper KSP2/Kotlin 2.x support. |
| Hilt | **2.60.1** | 2.59 is the first plugin supporting (and requiring) AGP 9; must move in the same commit as AGP. |

AGP 9 also required source changes in `build-logic` (untyped `CommonExtension`, the public
`api.dsl.LibraryExtension` interface, and `compileOptions` moving off `CommonExtension`). The
full version matrix and rationale live in
[`docs/agp-9-upgrade.md`](https://github.com/Defuuls/Orbin/blob/main/docs/agp-9-upgrade.md).

The build uses a Gradle **version catalog** (`gradle/libs.versions.toml`) and **convention
plugins** in `build-logic/` (application/library/feature/compose/hilt/room/jvm-library), so
module build files stay intentionally small.

## Release signing (local)

Keep release signing files **outside** the repository tree and point local release builds at
them with environment variables:

```bash
export ORBIN_KEYSTORE_FILE=/absolute/path/orbin-release.jks
export ORBIN_KEYSTORE_PASSWORD=...
export ORBIN_KEY_ALIAS=...
export ORBIN_KEY_PASSWORD=...
```

A git-ignored `keystore.properties` is still supported for emergency local use. When signing
material is absent, `assembleRelease` falls back to the debug signing config, so local release
builds work without secrets.

## CI workflows

| Workflow | Trigger | What it does |
| --- | --- | --- |
| `ci.yml` | every push to `main` and every PR | `ktlintCheck` + `detekt`, unit tests, then a debug APK build (uploaded as an artifact). Superseded runs are cancelled. |
| `codeql.yml` | scheduled/push | Manual CodeQL setup that runs a clean Android debug build for Java/Kotlin analysis instead of GitHub's autobuild. |
| `screenshots.yml` | PRs touching UI | Roborazzi screenshot verification. |
| `new-version.yml` | manual (`workflow_dispatch`) | Prepares a release PR from inputs: version name, `versionCode`, codename, base branch, draft flag. Bumps `app/build.gradle.kts` and `CHANGELOG.md`. |
| `release.yml` | push of a `v*` tag (or manual dispatch with a tag name) | Builds a **signed** release APK, stages the R8 `mapping.txt`, computes SHA-256 checksums, generates release notes from the commit log since the previous tag, and publishes the GitHub Release. |

Required repository secrets for releases: `RELEASE_KEYSTORE_BASE64`,
`RELEASE_KEYSTORE_PASSWORD`, `RELEASE_KEY_ALIAS`, `RELEASE_KEY_PASSWORD`.

## Cutting a release

1. Run the **New Version** workflow (or bump `versionName`/`versionCode` in
   `app/build.gradle.kts` and update `CHANGELOG.md` by hand) and merge the release PR.
2. Tag and push:

   ```bash
   git tag v34
   git push origin v34
   ```

3. The tag push triggers `release.yml`; the GitHub Release appears with the signed APK,
   mapping file, and checksums once the job completes.

**Codenames:** every milestone gets a codename (v30–v33 used the smallest known stars — Janus,
Fomalhaut C, EQ Pegasi A, CM Draconis A; v34 is "Dippin"). Pick names that are distinctive,
short enough for changelog entries, and not already used.

## Contributing

Read [CONTRIBUTING.md](https://github.com/Defuuls/Orbin/blob/main/CONTRIBUTING.md) and
[`docs/development-setup.md`](https://github.com/Defuuls/Orbin/blob/main/docs/development-setup.md).
To add a new image board engine, see
[`docs/provider-api/adding-a-provider.md`](https://github.com/Defuuls/Orbin/blob/main/docs/provider-api/adding-a-provider.md)
— it should not require touching app code.
