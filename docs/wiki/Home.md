# Orbin

Orbin is a modern, fast, open-source **Android image board client** built with Kotlin, Jetpack
Compose, and Material 3. It is engineered around a strict, modular Clean Architecture so that
supporting a new image board engine is a matter of implementing a single interface.

- **Repository:** https://github.com/Defuuls/Orbin
- **Releases:** https://github.com/Defuuls/Orbin/releases
- **Changelog:** https://github.com/Defuuls/Orbin/blob/main/CHANGELOG.md
- **License:** MIT

## Status

| | |
| --- | --- |
| Current release | **v35 — Proxima Centauri** (2026-07-10) |
| Website | https://defuuls.github.io/Orbin/ |
| Platform | Android 15+ (`minSdk` 35), compile/target SDK 36 |
| Codename scheme | Per-release codenames (smallest-known-star names since v30) |

Orbin is under active development with regular signed releases. The architecture, build system,
domain core, networking, media pipeline, encrypted data layer, and the reference provider are in
place; features continue to land incrementally.

## Screenshots

![Orbin thread viewer in thumbnail-grid mode, with encrypted-at-rest and biometric app-lock highlights](https://raw.githubusercontent.com/Defuuls/Orbin/main/docs/assets/orbin-hero-screenshot.svg)

![Orbin settings screen showing content, appearance, media, network, privacy, and storage preferences](https://raw.githubusercontent.com/Defuuls/Orbin/main/docs/assets/orbin-settings-screenshot.svg)

## Highlights

- **Multi-provider browsing** — vichan/4chan-compatible engine included; new engines
  (LynxChan, TinyIB, …) can be added without touching app code.
- **Subscribed feed** — a continuous feed of your followed boards with tap-to-top chrome,
  an optional true full-screen mode, in-feed search (v34), and an adaptive tablet layout.
- **Rich thread viewer** — structured reply tree with quote links and backlinks, inline media,
  a thumbnail-only grid view, and reading history with scroll restore.
- **Gallery & bookmarks** — a pinch-zoom swipe gallery; since v33 bookmarks live in a tab
  inside the Gallery view.
- **Privacy & security** — SQLCipher-encrypted database, encrypted DataStore settings,
  hardware-backed Keystore keys, biometric app-lock, HTTPS-only networking, optional
  DNS-over-HTTPS.

## Wiki pages

| Page | What's in it |
| --- | --- |
| [[User Guide\|User-Guide]] | Browsing, the subscribed feed, threads, gallery, downloads |
| [[Settings Guide\|Settings-Guide]] | Every settings section and option, explained |
| [[Release History\|Release-History]] | v30–v35 in detail, plus earlier release eras |
| [[Developer Guide\|Developer-Guide]] | Building, toolchain, CI, and the release workflow |
| [[Architecture and Modules\|Architecture-and-Modules]] | Layers, module graph, key design decisions |
| [[Troubleshooting]] | Build problems and in-app behavior questions |
