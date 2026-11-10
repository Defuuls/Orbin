# Orbin

Orbin is a privacy-focused, read-only **Android and iOS imageboard browser** built with Kotlin Multiplatform, Compose Multiplatform, and a **shared TikTok-style reader interface**. It browses boards, catalogs, threads, links, and media with a combined multi-site feed, clear source labels, and strictly read-only access.

[Shared Home preview](ui-next/src/androidHostTest/screenshots/social_ios_home.png) · [iOS settings preview](ui-next/src/androidHostTest/screenshots/social_ios_settings.png)

[Website](https://defuuls.github.io/Orbin/) · [Architecture](docs/architecture/README.md) · [Developer Guide](docs/wiki/Developer-Guide.md) · [iOS Build](docs/ios.md) · [Authentication](docs/authentication.md) · [Brand](docs/brand.md) · [Privacy](PRIVACY.md) · [Security](SECURITY.md)

**Current release:** [196 — Kai Lan](https://github.com/Defuuls/Orbin/releases/tag/v196-Kai-Lan)

**What's new in 196:** Mandatory first-run board selection initializes Home before browsing on Android and iOS, and repairs installations that previously skipped setup.

---

## Highlights

| Area | Capabilities |
| :--- | :--- |
| **Shared reader interface** | Full-screen vertical Home, overlaid media actions, official TikTok Sans typography, flat comments, accessible controls, and shared light/dark/true-black themes across Android and iOS. |
| **Browsing & Layout** | Fixed Home, Watched, central Boards+, Search, and Saved navigation; combined multi-site feed; complete provider-backed directories with original icons for all 91 current boards; and adaptive two-pane browsing. |
| **Thread Reader** | Persistent title and source labels, OP badges, green quote links, inline quote expansion, backlinks, spoilers, multi-attachment carousels, and Files sheet. |
| **Settings and privacy** | Searchable directory with dedicated subpages, retained privacy and backup controls, and inherited app/site/board playback settings. |
| **Media & Hardware Performance** | Coil 3 images + Media3 / AVPlayer video with bounded next-clip preloading, swipe gallery, double-tap seek, persistent session unmute, board media walls, optional deep-scan media discovery, and organized downloads (`Downloads/Orbin`). |
| **Themes & Ergonomics** | Compact monochrome surfaces, eight selectable color themes, Light / Dark / AMOLED modes, system text scaling, accessible controls, and searchable settings grouped by task. |
| **Privacy & Security** | SQLCipher-encrypted Room database, Keystore-backed encrypted preferences & biometric app lock, recents privacy shield (`FLAG_SECURE`), HTTPS-only transport with DoH fallback, and zero telemetry or cloud backups. |
| **Providers** | Engine-agnostic `ImageBoardProvider` contract shipping **4chan** (`provider:vichan`) and **BBWChan** (`provider:lynxchan`). |

---

## Architecture & Module Map

```text
ui-next (app-agnostic Compose screens)
  ↓
feature/* (Android ViewModels)  |  app-ios (iOS presenters & navigation)
  ↓
domain / core:model (models, policies, repository contracts)
  ↓
storage / core:data / data / network / media
  ↓
provider:api → provider:vichan | provider:lynxchan
```

```text
Orbin/
├── app/                 # Android application shell, navigation, Hilt DI
├── app-ios/             # iOS shared Kotlin application, navigation, and presenters
├── iosApp/              # iOS Xcode project (generated via XcodeGen)
├── ui-next/             # App-agnostic Compose Multiplatform screen kit
├── feature/             # Android feature modules (home, board, thread, gallery, downloads, search, settings, onboarding)
├── domain/              # Domain policies, use cases, and repository contracts
├── storage/             # Multiplatform Room database, DAOs, and repository implementations
├── data/                # Android encryption, background workers, and platform repositories
├── network/             # Android OkHttp / Retrofit transport and interceptors
├── media/               # Coil 3 image loader, Media3 video player, preloader, and downloader
├── provider/            # Engine contracts (:api) and implementations (:vichan, :lynxchan)
└── core/                # Shared KMP primitives (:common, :model, :data, :network, :designsystem, :ui, :testing, :graph)
```

Architecture boundaries are enforced in CI via `scripts/validate_architecture.py`. See [`docs/architecture/README.md`](docs/architecture/README.md) and [`docs/architecture/module-map.md`](docs/architecture/module-map.md) for details.

---

## Quick Start

Requires **JDK 17+**. Build the debug APK or run the full verification suite (architecture, repo consistency, ktlint, detekt, Android Lint, unit tests, and Kover coverage):

```bash
# Build Android debug APK
./gradlew assembleDebug

# Run all quality gates
bash scripts/check.sh
```

For iOS builds (requires macOS + Xcode), see [`docs/ios.md`](docs/ios.md). For testing and quality gates, see [`docs/architecture/quality-gates.md`](docs/architecture/quality-gates.md) and [`docs/wiki/Developer-Guide.md`](docs/wiki/Developer-Guide.md).

## License

See [LICENSE](LICENSE).
