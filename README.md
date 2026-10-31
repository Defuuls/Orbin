# Orbin

![Orbin brand showcase](docs/assets/orbin-brand-showcase.svg)

Orbin is a privacy-focused, read-only **Android and iOS imageboard browser** built with Kotlin Multiplatform, Compose Multiplatform, and Material 3 Expressive. It browses boards, catalogs, threads, links, and media without posting, replying, or creating threads.

[Website](https://defuuls.github.io/Orbin/) · [Architecture](docs/architecture/README.md) · [Developer Guide](docs/wiki/Developer-Guide.md) · [iOS Build](docs/ios.md) · [Authentication](docs/authentication.md) · [Brand](docs/brand.md) · [Privacy](PRIVACY.md) · [Security](SECURITY.md)

**Current release:** [185 — Lotus Root](https://github.com/Defuuls/Orbin/releases/tag/v185-Lotus-Root)

**What's new in 185:** Security hardening across the release pipeline, notification routing, thread archives and backup import.

---

## Highlights

| Area | Capabilities |
| :--- | :--- |
| **Browsing & Layout** | Adaptive grid-first catalogs with uncropped aspect-ratio previews, A–Z subscribed board feed, 840dp+ two-pane split view, and explicit offline/error states across **Feed**, **Media**, and **Boards**. |
| **Thread Reader** | Structured posts (greentext, quotes, inline quote previews, backlinks, spoilers, cross-board links), multi-attachment carousels, Files sheet, scroll restore, watched-thread notifications, and deduplicated link export. |
| **Media & Downloads** | Coil 3 images + Media3 / AVPlayer video with caching, preloading, swipe gallery, double-tap seek, persistent session unmute, board media walls, optional deep-scan media discovery, and organized downloads (`Downloads/Orbin`). |
| **Themes & Accessibility** | Material 3 Expressive (`#276A3C` forest seed), Dynamic Color, Light / Dark / AMOLED modes, classic board palettes (Yotsuba, Warosu, Miku, Lain), global font scaling, and searchable settings. |
| **Privacy & Security** | SQLCipher-encrypted Room database, Keystore-backed encrypted preferences & biometric app lock, recents privacy cover (`FLAG_SECURE`), HTTPS-only transport with DoH fallback, and cloud-backup exclusion. |
| **Providers** | Engine-agnostic `ImageBoardProvider` contract shipping **4chan** (`provider:vichan`) and **BBW Chan** (`provider:lynxchan`). |

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
