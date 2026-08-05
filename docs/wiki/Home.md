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
| Current release | **v62 — Hadar** (2026-08-05) |
| Website | https://defuuls.github.io/Orbin/ |
| Platform | Android 15+ (`minSdk` 35), compile SDK 37, target SDK 36 |
| Codename scheme | Per-release star codenames (prominent naked-eye stars since v49; nearby or dim stars for v30–v48) |

Orbin is under active development with regular signed releases. The architecture, build system,
domain core, networking, media pipeline, encrypted data layer, and the reference provider are in
place; features continue to land incrementally.

## Highlights

- **Multi-provider browsing** — vichan/4chan-compatible engine included; new engines
  (LynxChan, TinyIB, …) can be added without touching app code.
- **Subscribed feed** — a continuous feed of your followed boards with tap-to-top chrome,
  an optional true full-screen mode, in-feed search (v34), and an adaptive tablet layout.
- **Rich thread viewer** — structured reply tree with quote links and backlinks, inline media,
  a thumbnail-only grid view, and reading history with scroll restore.
- **Gallery & bookmarks** — a pinch-zoom swipe gallery; since v33 bookmarks live in a tab
  inside the Gallery view.
- **Media carousel** — posts with several attachments scroll horizontally with a page counter,
  in the thread view and optionally in the board feed, with configurable thumbnail sizes.
- **Selectable app icons** — five launcher icons (Orbital Orb, Nested Rings, Abstract Flow,
  Minimalist Essence, Dual Gradient), switchable from Settings without a restart (v57).
- **Saved searches & watch notifications** — saved search queries, plus per-thread watch
  notifications with configurable quiet hours (v51).
- **Privacy & security** — SQLCipher-encrypted database, encrypted DataStore settings,
  hardware-backed Keystore keys, biometric app-lock, HTTPS-only networking, optional
  DNS-over-HTTPS.

## Wiki pages

| Page | What's in it |
| --- | --- |
| [[User Guide\|User-Guide]] | Browsing, the subscribed feed, threads, gallery, downloads |
| [[Settings Guide\|Settings-Guide]] | Every settings section and option, explained |
| [[Release History\|Release-History]] | v49–v58 in detail, plus earlier release eras |
| [[Developer Guide\|Developer-Guide]] | Building, toolchain, CI, and the release workflow |
| [[Architecture and Modules\|Architecture-and-Modules]] | Layers, module graph, key design decisions |
| [[Troubleshooting]] | Build problems and in-app behavior questions |
