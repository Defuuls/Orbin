# Developer Guide

How to build Orbin, what the toolchain looks like after the July 2026 AGP 9 upgrade, and how CI
and releases work. Current as of **v76 (Mirfak)**. See also
[[Architecture and Modules|Architecture-and-Modules]] and the in-repo docs under
[`docs/`](https://github.com/Defuuls/Orbin/tree/main/docs).

## Prerequisites

- **JDK 17** (the build targets JVM 17) — verify with `java -version`.
- **Android SDK** with platform **API 37** (`compileSdk` 37, `targetSdk` 36, `minSdk` 35).
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
| Baseline profile (needs a device) | `./gradlew :app:generateReleaseBaselineProfile` |
| Lint/format | `./gradlew ktlintCheck` / `./gradlew ktlintFormat` |
| Static analysis | `./gradlew detekt` |
| Compose compiler metrics | add `-Porbin.enableComposeCompilerReports=true` |

Build knobs: `orbin.warningsAsErrors=true` treats Kotlin warnings as errors (CI uses this).

## Toolchain and SDK notes (AGP 9 upgrade, July 2026)

The build was upgraded from AGP 8.13.2 / Gradle 8.14.3 / Kotlin 2.0.21 in July 2026, and has moved
on from the versions that upgrade originally landed. Current pinned versions
(`gradle/libs.versions.toml` and `gradle/wrapper/gradle-wrapper.properties` are authoritative):

| Component | Version | Notes |
| --- | --- | --- |
| AGP | **9.3.1** | 9.2.0 had an R8 `RecordTag` regression that motivated the original upgrade to a patch release; the project has since moved past 9.2 entirely. |
| Gradle | **9.6.1** | Ahead of AGP 9's stated minimum, which is fine — AGP does not pin an upper bound. |
| Kotlin (KGP) | **2.4.10** | Held at 2.4.0 for a time because CodeQL's Kotlin extractor lagged; CodeQL now analyses 2.4.10 successfully (v59). |
| KSP | **2.3.10** | Standalone-versioned since 2.3.0; 2.3.1+ is required for AGP 9's built-in Kotlin. |
| Room | **2.8.4** | 2.7.0 was the first release with proper KSP2/Kotlin 2.x support. |
| Hilt | **2.60.1** | 2.59 is the first plugin supporting (and requiring) AGP 9; must move in the same commit as AGP. |

AGP 9 also required source changes in `build-logic` (untyped `CommonExtension`, the public
`api.dsl.LibraryExtension` interface, and `compileOptions` moving off `CommonExtension`). The
original upgrade's full version matrix and rationale live in
[`docs/agp-9-upgrade.md`](https://github.com/Defuuls/Orbin/blob/main/docs/agp-9-upgrade.md) — read
it for the *why*, and the version catalog for the current *what*.

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
material is absent the release signing config borrows the debug key, so release-shaped local
builds — including baseline profile generation — work without secrets. `assembleRelease` and
`bundleRelease` still fail fast if release signing is missing, since those produce artifacts
meant to leave the machine.

That fallback borrows the debug keystore's *path*, and AGP only creates `debug.keystore` when it
first signs a debug build. On a machine that has never built one — a fresh CI runner, say — a
release-shaped build fails with "Keystore file ... not found". Run `./gradlew :app:assembleDebug`
once first; the Baseline profile workflow does exactly that.

## Baseline profiles

`:benchmark` is a `com.android.test` module that records the classes on Orbin's startup and
subscribed-feed path, so ART compiles them ahead of time rather than interpreting them on first
launch.

```bash
./gradlew :app:generateReleaseBaselineProfile
```

This needs a **rooted** device — a `google_apis` emulator or an unlocked handset. That rules out
running it on every push, but not CI: the **Baseline profile** workflow (`workflow_dispatch`) boots
such an emulator, records the profile, and opens a draft PR with the result. Run it locally with the
command above if you have a device to hand.

The profile lands in `app/src/release/generated/baselineProfiles/` and is **committed**, because
nothing regenerates it automatically. Re-record it when startup or the feed changes shape; a stale
profile is not harmful, only progressively less useful.

## CI workflows

| Workflow | Trigger | What it does |
| --- | --- | --- |
| `ci.yml` | every push to `main` and every PR | `ktlintCheck` + `detekt`, unit tests, then a debug APK build (uploaded as an artifact). Superseded runs are cancelled. |
| `codeql.yml` | scheduled/push | Manual CodeQL setup that runs a clean Android debug build for Java/Kotlin analysis instead of GitHub's autobuild. |
| `screenshots.yml` | PRs touching UI | Records Roborazzi screenshots and uploads them as artifacts. |
| `instrumentation.yml` | every push to `main` and every PR | Boots an API 35 emulator (KVM on the GitHub runner) and runs `connectedDebugAndroidTest` for the modules that have `androidTest` sources, discovered per run. Separate from `ci.yml` because an emulator boot plus a test run is minutes of wall clock. |
| `baseline-profile.yml` | manual (`workflow_dispatch`) | Boots a rooted API 35 emulator, records a baseline profile, and opens a draft PR with it. |
| `new-version.yml` | manual (`workflow_dispatch`) | Prepares a release PR from inputs: version name, `versionCode`, codename, base branch, draft flag. Bumps `app/build.gradle.kts` and `CHANGELOG.md`. |
| `release.yml` | push of a `v*` tag (or manual dispatch with a tag name) | Builds a **signed** release APK, stages the R8 `mapping.txt`, computes SHA-256 checksums, generates release notes from the commit log since the previous tag, and publishes the GitHub Release. |
| `wiki-sync.yml` | push to `main` touching `docs/wiki/**` (or manual) | Mirrors `docs/wiki/` onto the repository's GitHub wiki via `rsync --delete`, so `docs/wiki` is the single source of truth — edit it in a PR like any other file, never the wiki directly. |
| `pages.yml` | push to `main` touching `site/**` (or manual) | Deploys the static landing page in `site/` to GitHub Pages (https://defuuls.github.io/Orbin/). |

Required repository secrets for releases: `RELEASE_KEYSTORE_BASE64`,
`RELEASE_KEYSTORE_PASSWORD`, `RELEASE_KEY_ALIAS`, `RELEASE_KEY_PASSWORD`.

## Cutting a release

1. Run the **New Version** workflow (or bump `versionName`/`versionCode` in
   `app/build.gradle.kts` and update `CHANGELOG.md` by hand) and merge the release PR.
2. Tag and push. Tags are `v<number>-<Codename>`, and the tag must be **annotated** — the
   release job reads its message as the GitHub Release title:

   ```bash
   git tag -a v76-Mirfak -m "Mirfak"
   git push origin v76-Mirfak
   ```

3. The tag push triggers `release.yml`; the GitHub Release appears with the signed APK,
   mapping file, and checksums once the job completes.

If pushing a tag is not possible, run **Release** via `workflow_dispatch` instead, supplying
`tag` and `tag_message`. The job creates and pushes the annotated tag itself, producing an
identical result.

**Codenames:** every milestone gets a star codename. Since v49 the scheme has been prominent
naked-eye stars — Altair, Fomalhaut, Rigel, Sirius, Canopus, Polaris, Vega, Arcturus, Capella,
Betelgeuse, Procyon, Achernar, Hadar, Acrux, Aldebaran, Antares, Spica, Pollux, Deneb, Regulus,
Bellatrix, Elnath, Alnair, Peacock, Avior, Alkaid, Mirfak.
Earlier releases drew on nearby or dim stars instead: v30–v33 used the smallest known stars
(Janus, Fomalhaut C, EQ Pegasi A, CM Draconis A), v34 broke the pattern with "Dippin", and
v37–v48 returned to nearby stars such as Wolf 359, Ross 128, Proxima Centauri and Sirius B.

Pick names that are distinctive, short enough for changelog entries, and **not already used** —
`git tag --list 'v*'` is the authoritative list of what is taken, not this page or the
[[Release History|Release-History]]: a v66 release once shipped briefly as "Rigel" before this
check would have caught that v51 got there first. A couple of earlier reuses (Epsilon Eridani
for v44 and v46, Proxima Centauri for v35 and v47, Wolf 359 for v37 and v52) predate this
guidance and were left as historical record rather than relitigated.

## Contributing

Read [CONTRIBUTING.md](https://github.com/Defuuls/Orbin/blob/main/CONTRIBUTING.md) and
[`docs/development-setup.md`](https://github.com/Defuuls/Orbin/blob/main/docs/development-setup.md).
To add a new image board engine, see
[`docs/provider-api/adding-a-provider.md`](https://github.com/Defuuls/Orbin/blob/main/docs/provider-api/adding-a-provider.md)
— it should not require touching app code.
