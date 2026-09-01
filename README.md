# Orbin

A modern, fast, and beautiful open-source **Android image board client**, built with Kotlin,
Jetpack Compose, and Material 3. Orbin targets **Android 12+ (API 31+)** and is engineered around
a strict, modular Clean Architecture so that supporting a new image board engine is a matter of
implementing a single interface.

Orbin is a **browsing client**: it reads boards, catalogs, and threads, and does not post,
reply, or create threads.

**Website:** https://defuuls.github.io/Orbin/

**Current release:** [115 — Misaki](https://github.com/Defuuls/Orbin/releases/tag/v115-Misaki)

**Available providers:** 4chan (Vichan, read-only example instance), BBW Chan (LynxChan)

> **Status:** under active development, with regular signed releases. The architecture, build
> system, domain core, networking, media pipeline, encrypted data layer, and two reference
> providers (vichan/4chan-compatible and LynxChan) are in place; features continue to land
> incrementally. See [CHANGELOG.md](CHANGELOG.md).

![Orbin thread viewer in thumbnail-grid mode, with encrypted-at-rest and biometric app-lock highlights](docs/assets/orbin-hero-screenshot.svg)

![Orbin settings screen showing the always-on built-in content filter alongside content, appearance, media, network, privacy, and storage preferences](docs/assets/orbin-settings-screenshot.svg)

---

## Features

**Browsing**
- Multi-provider support through a clean provider abstraction (4chan via Vichan and BBW Chan via
  LynxChan included; additional imageboard engines can be added without modifying app code).
- Board list, catalog with sorting, and a rich thread viewer with post dates.
- Subscribed feed merged across every board you follow and ordered by activity, so a quiet
  board's stale thread no longer outranks a busy board's live one. A colour per board keeps the
  mix sortable by eye.
- **List / Grid / image-only layouts:** the subscribed feed and every board catalog switch
  between three layouts — the usual text-and-thumbnail list, a denser card grid, or a pure
  image-only grid. The switch is three words at the top of the list that scroll away with it,
  rather than an icon parked in a bar.
- **Already-read threads lose their weight** in the board catalog and subscribed feed, so a
  returning glance can tell new threads from ones already opened without making the old ones
  harder to read.
- **Pull-to-refresh** on the subscribed feed, board catalog, and thread view, with haptic
  feedback on the refresh threshold.
- **Offline banner:** a banner appears across the app whenever the device loses its network
  connection, and clears automatically once it's back.
- **Two-pane layout** on wide screens (≥840dp): opening a thread from a board catalog keeps the
  catalog visible alongside it instead of replacing it, and the open thread survives rotating
  across the width threshold.

**Thread viewer**
- Structured reply tree with quote links, quote previews, and backlinks.
- Inline images and video, collapsible replies, and thread stats.
- A "Files" view that shows every attachment in the thread at a glance, its density set by the
  thumbnail-size preference.
- Watch, Files, download-all and share are words set beneath the title, where they scroll away
  with it rather than occupying the top of the screen for the life of the thread. Tapping a
  post's number-and-time line folds it away.
- **Tap-to-reveal spoilers:** spoilered text stays blacked out until tapped, then reveals in
  place; a quote link hidden inside a spoiler is inert until revealed, so the tap that would
  navigate away can't be taken by accident.
- **Scroll-position restore:** reopening a thread — even after fully closing the app — resumes at
  the post you were last reading rather than jumping back to the top. The position is saved as
  part of reading history and updates automatically as you scroll.

**Media**
- **All media in one grid:** a single continuous wall of every image and video from every board,
  swept from the catalogs of all of them at once — no board to pick, no thread to open. It fills
  board by board as the sweep runs, carries a press-and-drag fast scrollbar for crossing thousands
  of tiles, and tapping any file opens it in the gallery. Boards whose catalog could not be
  fetched are reported, so a partial sweep is never presented as a complete one. Reachable from
  Settings → Content & Feed, or by typing "All media" into Search.
- **Deep scan (opt-in):** a catalog sweep reaches each thread's opening post but not the files
  attached to its replies. Switching this on walks the threads themselves for that reply media —
  far more thorough, and far slower, so it reports threads walked rather than pretending to be a
  load that is about to finish. Toggled from Settings.
- Hardware-accelerated image and video, progressive loading, pinch-zoom, swipe gallery,
  background preloading, autoplay + mute toggle, fullscreen playback, auto-rotate video, and a
  native download manager.
- **Configurable download folder structure:** downloaded media can be saved Flat, By board, By
  board then thread, or By thread, for both the default downloads folder and a custom saved-media
  folder.
- **Autoplay in feed:** optionally, each thread's first video plays inline and muted directly in
  the subscribed feed as its row scrolls into view, and stops as it scrolls away — no need to
  open the thread first.

**Personalization**
- Material 3 with dynamic color, light/dark, and AMOLED-black themes, plus 20+ ported imageboard
  color palettes (Yotsuba, Tomorrow, Miku, Lain, Penumbra, Windows 95, and more), checked in CI
  against a WCAG AA contrast baseline.
- **Settings as one scrolling list:** Content & Feed, Notifications, Appearance, Media & Playback,
  Privacy & Network and Storage & Backup are headings you scroll past rather than screens you
  navigate to, with every option editable where it stands and searchable by name.
- Adaptive layouts for tablets, foldables, landscape, and edge-to-edge.
- Predictive back gesture and smooth shared-element transitions.

**Privacy & security**
- **Encrypted at rest:** the local database (history, bookmarks, downloads, searches) is encrypted
  with SQLCipher and app settings with an encrypted DataStore, both protected by a hardware-backed
  Android Keystore key — so a copy of the app's data directory is only ciphertext.
- **Biometric app-lock** gated on a Keystore-backed cryptographic operation (not just the prompt
  callback), plus `FLAG_SECURE` to keep locked content out of the recents preview.
- HTTPS-only networking enforced end-to-end, optional DNS-over-HTTPS, and a configurable
  user-agent. Cloud backup and device-transfer of local data are disabled, so nothing leaves the
  device implicitly.
- **Backup and restore** as the deliberate alternative to that: export settings, subscribed and
  favourite boards, bookmarks and saved searches to a file you choose, and import it after a
  reinstall. Importing merges rather than replaces, so a restore cannot destroy an existing
  setup. The file is plain JSON and is not encrypted — keep it somewhere you trust.

## Orbin Minimal

**Orbin Minimal releases separately**, under
[`minimal-v*` tags](https://github.com/Defuuls/Orbin/releases?q=minimal&expanded=true) numbered
from 1 — and, from `minimal-v4`, carrying a codename the way the full client's tags do — on its
own cadence — it is not bundled with the full client's release. Because both apps
are tagged in this repository, "latest release" resolves by date across the two: pick by the
filename (`orbin-minimal-…apk` versus `orbin-v…apk`) rather than by whichever release is on top.
It is a pared-back app whose whole
interface is your subscribed boards as one flat feed, merged across every board you follow and
sorted by latest activity. Each row carries the opening post's first attachment as a preview, and
a row whose attachment is a video plays it — muted — while the row is on screen. Tap a row to read
the thread, tap an image to view it full screen. Nothing else: no bottom navigation, no settings
hub, no search, gallery, downloads, bookmarks, history, notifications, app-lock or themes.

The screens are the full client's own — the same feed, reader and board picker from `ui-next`,
which is why the feed here has the same three layouts, the same pull-to-refresh and the same
floating rail. The rail's one affordance is Boards rather than Search, because the board picker is
the only other place this app has, and it is the whole of the navigation.

Because there is no settings screen, feed autoplay is off and stays off — it is the full client's
own setting, and this app has nowhere to turn it on. It used to be forced on here instead, which on
a feed spanning every board you follow meant using mobile data without asking. Videos still play
when you open the thread.

It is a different front end over the same layers — identical providers, caching, encrypted storage,
board filters and the always-on content filter — built from `app-minimal/`. It installs alongside
the full client under its own applicationId, which also means Android sandboxes their data
separately: its subscriptions are its own, chosen from the board picker behind the rail's Boards.

## Tech stack

| Concern | Choice |
| --- | --- |
| Language | Kotlin 2.4.10 (K2), Coroutines, Flow/StateFlow, Serialization |
| UI | Jetpack Compose (BOM 2026.08.00), Material 3, Navigation Compose, Paging 3 |
| DI | Hilt |
| Persistence | Room + SQLCipher, encrypted DataStore |
| Networking | OkHttp, Retrofit, kotlinx.serialization |
| Media | Coil 3 (images), Media3/ExoPlayer (video) |
| Background | WorkManager |
| Quality | detekt, ktlint, JUnit, Turbine, MockK, Truth, Robolectric, Roborazzi |

Exact versions are pinned in [`gradle/libs.versions.toml`](gradle/libs.versions.toml).

## Module structure

```
Orbin/
├── app/                      # Application, MainActivity, navigation host, DI aggregation
├── app-minimal/              # Second shipped APK: subscribed boards as one flat feed, nothing else
├── benchmark/                # Baseline profile generation (startup + feed path)
├── build-logic/              # Gradle convention plugins (the build's backbone)
├── core/
│   ├── common/               # Result types, dispatchers, NetworkMonitor
│   ├── model/                # Pure domain entities (no Android deps)
│   ├── designsystem/         # Theme, color, typography, reusable components
│   ├── ui/                   # Shared Compose UI building blocks
│   └── testing/              # Test fixtures and rules
├── domain/                   # Repository contracts + use cases (pure logic)
├── data/                     # Room, DataStore, Paging, repository implementations
├── network/                  # OkHttp/Retrofit, DoH, connectivity
├── media/                    # Coil 3 + Media3 integration, download manager
├── ui-next/                  # The interface itself: screens as plain data plus slots, no app types
├── provider/
│   ├── api/                  # The ImageBoardProvider SPI (pure Kotlin)
│   ├── vichan/               # 4chan provider (vichan/4chan-compatible JSON)
│   └── lynxchan/             # BBW Chan provider (LynxChan JSON)
└── feature/                  # home, board, thread, search, history, settings, gallery
                              # (includes bookmarks), downloads, onboarding
```

See [docs/architecture/README.md](docs/architecture/README.md) for the dependency graph and design
rationale, and [docs/provider-api/adding-a-provider.md](docs/provider-api/adding-a-provider.md) to
add a new engine.

## Build instructions

**Requirements**
- JDK 17+
- Android SDK with API 37 (`compileSdk` 37, `targetSdk` 36, `minSdk` 31)
- Android Studio Ladybug+ (or the command line below)

**Common tasks**
```bash
./gradlew assembleDebug          # build the debug APK
./gradlew test                   # JVM unit tests across all modules
./gradlew detekt ktlintCheck     # static analysis & formatting
./gradlew :app:installDebug      # install on a connected device/emulator
```

The build uses a Gradle version catalog (`gradle/libs.versions.toml`) and convention plugins in
`build-logic/`; module build files stay intentionally small (often three lines). See the
[Developer Guide](https://github.com/Defuuls/Orbin/wiki/Developer-Guide) for the full toolchain,
CI workflows, and how releases are cut.

## Documentation

| Doc | What's in it |
| --- | --- |
| [Wiki](https://github.com/Defuuls/Orbin/wiki) | User guide, settings reference, release history, troubleshooting |
| [User Guide](https://github.com/Defuuls/Orbin/wiki/User-Guide) | Day-to-day use: feed, threads, gallery, downloads |
| [Settings Guide](https://github.com/Defuuls/Orbin/wiki/Settings-Guide) | Every setting, explained |
| [Developer Guide](https://github.com/Defuuls/Orbin/wiki/Developer-Guide) | Building, toolchain, CI, and cutting a release |
| [Architecture and Modules](https://github.com/Defuuls/Orbin/wiki/Architecture-and-Modules) | Layers, module graph, key design decisions |
| [Troubleshooting](https://github.com/Defuuls/Orbin/wiki/Troubleshooting) | Build problems and in-app behavior questions |
| [CHANGELOG.md](CHANGELOG.md) | Every release, in detail |
| [SECURITY.md](SECURITY.md) | Supported versions and how to report a vulnerability privately |

## Contributing

Contributions are welcome — please read [CONTRIBUTING.md](CONTRIBUTING.md) and the
[development setup](docs/development-setup.md). By contributing you agree your work is licensed
under the project's AGPLv3 license.

## License

Orbin is released under the [GNU Affero General Public License v3.0](LICENSE).
