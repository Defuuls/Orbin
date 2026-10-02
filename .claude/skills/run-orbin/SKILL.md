---
name: run-orbin
description: Build, run, and drive the Orbin Android app headlessly. Use when asked to run or start Orbin, launch the app, click through it, take a screenshot of a screen (feed, thread, onboarding, settings), check a UI change in the real app, or run its tests.
---

Orbin is a Kotlin Multiplatform image board client. Here there is no emulator (no `/dev/kvm`), so the
agent path is **`.claude/skills/run-orbin/driver.sh`**. It launches the real `MainActivity` under
Robolectric: the real Hilt graph, providers, DataStore, navigation and live network. It runs a step
script against that app, writes Roborazzi screenshots, and prints the semantics tree.

All paths are relative to the repo root.

## Prerequisites

The container already has JDK 21, the Android SDK at `/root/android-sdk` (platform 37, no emulator)
and network access through the agent proxy. Nothing else is installed. The Gradle wrapper fetches
Gradle 9.7.1.

## Run (agent path): the driver

```bash
.claude/skills/run-orbin/driver.sh 'wait:/an/;click:/an/;click:Start browsing;wait:Gathering threads;gone:Gathering threads;sleep:8000;idle:500;shot:feed;tree'
```

- The argument is a `;`-separated step script. With no argument it runs
  `idle:2000;shot:launch;tree`.
- Screenshots go to `build/orbin-drive/<name>.png`, or to `$OUT` if set. Open them with Read.
- The full Gradle log and JUnit XML go to `build/orbin-drive/drive.log`. The script echoes the
  `[drive]` steps, the tree and the first error lines.
- `DARK=true` runs in night mode.
- Each run is a fresh install: first-run onboarding every time, with an in-memory DB. It takes about
  30–45 s once warm.

| Step | Does |
|---|---|
| `wait:<text>` | wait up to 20 s for a node containing `<text>` (substring, unmerged tree) |
| `gone:<text>` | wait up to 60 s until nothing contains `<text>`, e.g. `Gathering threads` |
| `click:<text>` | click the first node containing `<text>` |
| `type:<value>` | type into the first editable field |
| `scroll:<text>` | scroll the first scrollable container to `<text>` |
| `idle:<ms>` | advance the Compose clock (animations, debounces) |
| `sleep:<ms>` | wall-clock wait (network fetches and image decodes run on real threads) |
| `shot:<name>` | screenshot the window |
| `tree` | print the semantics tree, to find the text to `click:` |
| `back` | system back |

These flows were verified this session:

```bash
# First-run screen (board list is fetched live from 4chan's API)
.claude/skills/run-orbin/driver.sh 'wait:/an/;shot:boards;tree'
# Onboarding → feed with live thumbnails
.claude/skills/run-orbin/driver.sh 'wait:/an/;click:/an/;click:Start browsing;wait:Gathering threads;gone:Gathering threads;sleep:8000;idle:500;shot:feed'
# Open a thread, in dark mode
DARK=true .claude/skills/run-orbin/driver.sh 'wait:/an/;click:/an/;click:Start browsing;wait:Gathering threads;gone:Gathering threads;click:T-rex;sleep:6000;idle:1000;shot:thread-dark'
```

Thread titles are live data, so `click:T-rex` only worked for that day's /an/ catalog. Run `tree`
first and pick text that's on screen.

### How it works (what to edit)

- **`drive.init.gradle`** is passed with `-I`. It adds `src/` to `:app`'s JVM test source set,
  adds the test dependencies and `kspTest` Hilt, turns on `includeAndroidResources`, and forwards
  `-Porbin.*` to the test. Repo build files are never edited.
- **`src/com/orbin/app/drive/DriveOrbin.kt`** is a `@HiltAndroidTest` that runs the steps. Add new
  verbs here.
- **`src/com/orbin/app/drive/FakeAndroidKeyStore.kt`** is an in-memory `AndroidKeyStore` JCA
  provider.

## Run (human path)

```bash
./gradlew :app:assembleDebug   # → app/build/outputs/apk/debug/*.apk; adb install on a device/emulator
```

This is useless headless, since there is no device to install on.

## Test

```bash
./gradlew :app:testDebugUnitTest                                   # app JVM tests
./gradlew verifyRoborazziDebug verifyRoborazziAndroidHostTest      # screenshot goldens (what CI's "Verify screenshots" runs)
./gradlew ktlintCheck                                              # CI lint
```

## Gotchas

These are the deltas from production. Each one is something the real app needs that Robolectric
lacks, so the driver substitutes as little as possible:

- **SQLCipher has no JVM native lib.** The real `DatabaseModule` throws
  `UnsatisfiedLinkError: no sqlcipher`. The driver `@UninstallModules(DatabaseModule)` and binds
  `DriveDatabaseModule`: the same Room schema, DAOs and repositories, but in memory and unencrypted.
  Bugs specific to DB encryption or migrations can't be reproduced here.
- **No `AndroidKeyStore`.** Settings and the DB passphrase are encrypted by `LocalDataCipher` with a
  Keystore key. Without one, clicking "Start browsing" fails with
  `KeyStoreException: AndroidKeyStore not found`. `FakeAndroidKeyStore.install()` registers a
  software provider under that name, so the app's own AES/GCM code runs unchanged.
- **`HiltTestApplication` replaces `OrbinApplication`.** So its `onCreate` doesn't run (diagnostics
  install, watch scheduling), and two things it normally provides are wired by hand:
  - **WorkManager** is initialized in the test, because the manifest removes the auto-initializer.
  - **Coil's singleton `ImageLoader`** is the app's injected loader. Without that, every tile shows
    "Image unavailable".
- **Coil + Robolectric:** Coil decodes with `ImageDecoder` on API 28+, and Robolectric only
  implements `BitmapFactory`. The error is `ImageDecoder$DecodeException: Only supported on Android`.
  The driver installs the loader with `imageDecoderEnabled(false)`.
- **`idle` vs `sleep`:** the Compose clock (`idle`) doesn't advance network or image loads.
  Screenshots taken right after navigation show spinners or blank tiles. Use `gone:` or `wait:`
  for data, then `sleep:` for images.
- **`:app` doesn't set `includeAndroidResources`**, unlike the library conventions. Without it,
  Robolectric uses a stub manifest and fails with
  `Unable to resolve activity ... cmp=org.robolectric.default/...`.
- **AGP 9 built-in Kotlin:** extra test sources must go in `sourceSets.test.kotlin`. If they go in
  `.java`, they are silently skipped and you get "No tests found for given includes".
- **`kspTest` is created after AGP applies.** Adding dependencies to it eagerly fails with
  `Configuration with name 'kspTest' not found`. Attach with `configurations.matching`.

## Troubleshooting

- **`Received status code 429 from server: Too Many Requests` (repo.maven.apache.org)** on a cold
  cache: Maven Central rate-limits. `driver.sh` retries up to 4× with `--max-workers=2`. For
  other tasks, rerun with `--max-workers=2`, which took 3 attempts here.
- **`HTTP response code: 429 ... android-all-instrumented-15-robolectric-13954326-i7`**:
  Robolectric downloads its 200 MB Android runtime itself, outside Gradle, and doesn't retry.
  `driver.sh` pre-seeds `~/.m2/repository/org/robolectric/android-all-instrumented/` with
  `curl --retry` and prints `[driver] seeding Robolectric runtime: ...`. If it still fails, it says
  `could not download ...`; rerun a minute later. If `robolectric` or `sdk = [35]` changes, update
  `RV` in `driver.sh`. The needed version is in the error URL.
- **`Plugin [id: 'org.jetbrains.kotlin.plugin.compose' ...] was not found`** on the very first
  build: a transient resolution failure through the proxy. Rerun with `--refresh-dependencies`.
- **`ComposeTimeoutException: Condition still not satisfied after 20000 ms` on `wait:`**: the text
  isn't on screen. Rerun with `tree` in place of the failing step and pick text that's there.
  Errors from the app appear as `Suppressed:` lines under this exception in `drive.log`.
