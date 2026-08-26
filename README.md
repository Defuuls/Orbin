# Orbin

A modern, fast, and beautiful open-source **Android image board client**, built with Kotlin,
Jetpack Compose, and Material 3. Orbin targets **Android 12+ (API 31+)** and is engineered around
a strict, modular Clean Architecture so that supporting a new image board engine is a matter of
implementing a single interface.

Orbin is a **browsing client**: it reads boards, catalogs, and threads, and does not post,
reply, or create threads.

**Website:** https://defuuls.github.io/Orbin/

**Current release:** [98 — Ziti](https://github.com/Defuuls/Orbin/releases/tag/v98-Ziti)

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
- Subscribed feed with tap-to-top chrome, optional full-screen scrolling, and an adaptive
  tablet dock that keeps navigation close without crowding the feed.
- **List / Grid / image-only layouts:** the subscribed feed and every board catalog switch
  between three layouts from the same top-bar icon — the usual text-and-thumbnail list, a denser
  card grid, or a pure image-only grid with an adjustable thumbnail size (Medium/Large/Fill).
- **Already-read threads are dimmed** in the board catalog and subscribed feed (List/Grid
  layouts), so a returning glance can tell new threads apart from ones already opened.
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
- A thumbnail-only grid view that shows every attachment in the thread at a glance, with the
  same adjustable thumbnail sizing as the board/feed image-only layouts.
- A "More" overflow menu keeps less-frequent actions (download all media, export links) out of
  the main toolbar, alongside a real expand/collapse icon on collapsed posts.
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
  Settings → Content & Feed or the Gallery tab's top bar.
- **Deep scan (opt-in):** a catalog sweep reaches each thread's opening post but not the files
  attached to its replies. Switching this on walks the threads themselves for that reply media —
  far more thorough, and far slower, so it reports threads walked rather than pretending to be a
  load that is about to finish. Toggled from the wall's top bar or Settings.
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
- **Settings as a category hub:** Content & Feed, Notifications, Appearance, Media & Playback,
  Privacy & Network, and Storage & Backup each get their own screen instead of one long scroll
  through 35+ options, with a search icon on the hub to jump straight to any setting by name.
- Selectable app icons (five launcher variants), shown correctly in the feed's top bar.
- Adaptive layouts for tablets, foldables, landscape, and edge-to-edge.
- Tablet feed rows use an old-Reddit-style thumbnail-and-text layout for faster scanning on
  larger screens.
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
from 1, on its own cadence — it is not bundled with the full client's release. Because both apps
are tagged in this repository, "latest release" resolves by date across the two: pick by the
filename (`orbin-minimal-…apk` versus `orbin-v…apk`) rather than by whichever release is on top.
It is a pared-back app whose whole
interface is your subscribed boards as one flat feed, merged across every board you follow and
sorted by latest activity. Each row carries the opening post's first attachment as a preview, and
a row whose attachment is a video plays it — muted — while the row is on screen. Tap a row to read
the thread, tap an image to view it full screen. Nothing else: no bottom navigation, no settings
hub, no search, gallery, downloads, bookmarks, history, notifications, app-lock or themes.

Because there is no settings screen, autoplay is always on rather than a preference. On a feed
spanning every board you follow that will use mobile data without asking; the full client makes
the same behaviour opt-in under Settings → Media.

It is a different front end over the same layers — identical providers, caching, encrypted storage,
board filters and the always-on content filter — built from `app-minimal/`. It installs alongside
the full client under its own applicationId, which also means Android sandboxes their data
separately: its subscriptions are its own, chosen from the list icon in its top bar.

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
