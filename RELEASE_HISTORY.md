# Orbin — Complete Release History (v1 to v48)

A comprehensive record of all releases for Orbin, a modern Android image board client built with Kotlin, Jetpack Compose, and Material 3.

---

## v50 — Fomalhaut (2026-07-14)

### Added
- **Collapse all / Expand all:** Added convenient toolbar buttons to collapse or expand all subscribed boards at once, for quickly toggling between full feed and collapsed board headers.
- **Enhanced top bar:** The subscribed feed title bar now displays the app name "Orbin" with a colored indicator square showing the currently selected theme's primary color.

---

## v49 — Altair (2026-07-14)

### Fixed
- **Board collapse headers:** Fixed an issue where collapsed boards had no clickable headers in full-screen mode, making it impossible to expand them. Headers now always display when a board is collapsed.

---

## v48 — Sirius B (2026-07-13)

### Added
- **Board collapse:** Users can now collapse/expand boards in the subscribed feed by clicking the board name. A clickable collapse/expand icon (ExpandMore when collapsed, ExpandLess when expanded) provides visual feedback. Collapse state is persisted across app navigation using rememberSaveable.

---

## v47 — Proxima Centauri (2026-07-13)

### Added
- **8kun.top provider:** Added support for 8kun.top as a new LynxChan instance, available in provider selection during onboarding and board browsing. Marked as NSFW by default.

### Changed
- **Settings UI:** Reverted all dropdown menus back to horizontally scrolling filter chips for a more tactile selection experience. Includes provider selection, color themes, and all choice-based settings.
- **Color themes:** Simplified the palette from 24 entries to 3 core themes (Default, Tomorrow, Tomorrow Dark) for a more focused and maintainable design system.

---

## v46 — Epsilon Eridani (2026-07-13)

### Removed
- **Tranchan provider:** Removed the WakabaProvider implementation and all Tranchan-specific code, references, and infrastructure. The app now exclusively supports 4chan (Vichan) and BBW Chan (LynxChan).

### Changed
- **Documentation:** Updated README to reflect current providers and recent feature additions (fullscreen video, auto-rotate, post dates, 20+ imageboard color palettes, updated tech stack).

---

## v38 — Ross 128 (2026-07-12)

### Added
- **Post & thread dates:** The catalog shows each thread's creation date, and every post header in the thread view shows the date and time it was posted.

### Removed
- **Verify file host links:** Removed the dormant setting and all remaining code behind the file-host link verification feature.

---

## v37 — Wolf 359 (2026-07-12)

### Added
- **Fullscreen video:** A setting to play videos edge-to-edge in an immersive presentation (hiding the status bar and app chrome), plus a fullscreen toggle button in the video controls.
- **Auto-rotate video:** A setting to turn the screen to landscape automatically when a wide video starts playing, for fullscreen viewing.

### Changed
- **Dependencies:** Updated the toolchain and libraries in one coordinated bump — Kotlin 2.4.0, Compose BOM 2026.06, compileSdk 37, OkHttp 5, Coil 3.5, Coroutines 1.11, and the AndroidX, testing, and GitHub Actions groups.

### Removed
- **File-host link checker:** Removed the gofile.io/fast-file.ru/mega.nz link alive/dead verification and its setting.

---

## v36 — TRAPPIST-1 (2026-07-11)

### Added
- **8chan.moe:** Added 8chan.moe as a selectable LynxChan provider. The network layer now clears its POWBlock proof-of-work gate and terms-of-service redirect transparently, so browsing works the same as any other site.
- **8chan themes:** Ported 8chan's palette skins (Yotsuba, Tomorrow, Miku, Lain, Penumbra, Windows 95, and more) as selectable app color themes.

### Changed
- **Settings pickers:** Appearance and content options (color theme, app icon, theme mode, thumbnail size, feed limits, and the active site) are now compact drop-downs instead of horizontally scrolling chip tiles, keeping the long theme list usable.

### Known limitations
- **7chan.org:** Not added. 7chan runs KusabaX, which exposes no JSON API, and the site sits behind a Cloudflare JS challenge that the app's HTTP client cannot pass, so it cannot currently be supported as a functional provider.

---

## v35 — Proxima Centauri (2026-07-10)

### Added
- **Board labels:** The subscribed feed and search results now show the board name (e.g. `/g/`) next to the title of each post, so posts keep their board context even in full-screen mode where board headers are hidden.

---

## v34 — Dippin (2026-07-09)

### Added
- **Settings:** Add an Internal updater toggle so update checks can be managed from app settings.

### Changed
- **Feed search:** Move subscribed-thread search into the top of the subscribed feed and scope it to subscribed boards only.
- **Navigation:** Remove Search as a standalone bottom-navigation tab now that subscribed search lives in the feed.

---

## v33 — CM Draconis A (2026-07-09)

### Changed
- **Gallery:** The board picker now offers only subscribed boards (honouring the NSFW-board visibility setting), matching the feed instead of listing every board on the provider.
- **Bookmarks:** The Bookmarks bottom-navigation tab is removed; bookmarks now live in a Bookmarks tab inside the Gallery view, keeping the watch toggle, unread badges, and remove actions.

---

## v32 — EQ Pegasi A (2026-07-09)

### Fixed
- **Full-screen feed:** Drop the pinned board headers from the feed while the full-screen option is on — nothing stays fixed at the top and boards are no longer listed between threads, so the feed is a total full-screen view.

---

## v31 — Fomalhaut C (2026-07-09)

### Fixed
- **Full-screen feed:** The option now actually goes full screen — the status and navigation bars hide together with the feed chrome while scrolling, and the duplicated window-inset padding that left white strips at the top and bottom of the feed view is removed.

---

## v30 — Janus (2026-07-09)

### Added
- **Feed chrome:** Add iOS-style tap-to-top behavior from the top feed, board, and thread bars.
- **Settings:** Add a Full-screen feed option that lets the subscribed feed hide top and bottom bars while scrolling for more reading space.
- **Tablet feed:** Add an initial tablet mock-up with a floating dock, combined subscribed-feed controls, auto-hiding chrome, and old-Reddit-style thumbnail/text rows.

### Fixed
- **Media CDN usage:** Cache video media through Media3 and avoid no-cache request headers for static media so repeated viewing does not churn CDN requests.

---

## v25.2.1 — Cleopatra (2026-07-05)

### Fixed
- **Thread links:** Exporting thread links now saves to the configured saved media folder, falling back to `Downloads/Orbin` when the default save location is active.

---

## v25.2 — Casey Jones (2026-07-05)

No major changes documented.

---

## v25.1 — Box of Rain (2026-07-05)

No major changes documented.

---

## v25.0 — Franklin's Tower (2026-07-05)

No major changes documented.

---

## v24.4 — Sugar Magnolia (2026-07-05)

No major changes documented.

---

## v24.3 — Truckin' (2026-07-05)

No major changes documented.

---

## v24.2 — Ripple (2026-07-05)

No major changes documented.

---

## v24.1.1 — Arcadia (2026-07-04)

No major changes documented.

---

## v24.1 — Arcadia (2026-07-04)

No major changes documented.

---

## v24.0.4 — Elysium (2026-07-04)

No major changes documented.

---

## v24.0.3 — Elysium (2026-07-04)

No major changes documented.

---

## v24.0.2 — Elysium (2026-07-04)

No major changes documented.

---

## v24.0.1 — Elysium (2026-07-04)

No major changes documented.

---

## v24.0 — Elysium (2026-07-04)

### Security
- **Encryption at rest:** The local database (history, bookmarks, downloads, recent searches) is now encrypted with SQLCipher, and app settings are stored in an encrypted DataStore. Both are protected by a hardware-backed Android Keystore key that never leaves the TEE/StrongBox, so a copy of the app's data directory yields only ciphertext.

### Changed
- **One-time data reset:** Because encryption changes the on-disk format, existing history, bookmarks, downloads, and settings — **including favorites and subscriptions** — are reset once when updating to this version.

---

## v23.9 — Thule (2026-07-04)

No major changes documented.

---

## v23.8 — Delilah (2026-07-04)

### Fixed
- **App lock:** Prevent biometric unlock from hanging by cancelling stale prompts on app background/destruction, ignoring stale callbacks, and timing out stuck unlock attempts.
- **Permissions:** Wait until Orbin is ready and unlocked before requesting notification permission so the Android permission dialog does not race the biometric prompt.

---

## v23.7 — Hyperborea (2026-07-04)

No major changes documented.

---

## v23.6 — Lyonesse (2026-07-04)

No major changes documented.

---

## v23.5 — Camelot (2026-07-04)

No major changes documented.

---

## v23.4 — Avalon (2026-07-03)

No major changes documented.

---

## v23.3 — Shangri-La (2026-07-03)

No major changes documented.

---

## v23.1 — El Dorado (2026-07-02)

### Fixed
- **4chan loading:** Accept numeric media IDs from live catalog/thread responses so boards, feeds, and threads load when the API sends `tim` as a number.

---

## v23.0 — Atlantis (2026-07-01)

### Security
- **App lock:** Re-arm biometric/device-credential lock after backgrounding, keep locked content gated until persisted settings load, and provide recovery when Android auth is unavailable.
- **Comment parser:** Reject unsafe numeric HTML entity code points and decode astral Unicode characters without truncation.
- **HTTPS policy:** Remove the unused mutable HTTPS-only setter so the always-on transport boundary is reflected in the settings API.

---

## v22.0 — Largetooth Sawfish (2026-06-30)

### Changed
- **Security:** Resolve Dependabot transitive dependency alerts by applying patched Gradle plugin-classpath and project dependency versions.

---

## v21.0 — Sakhalin Sturgeon (2026-06-30)

### Changed
- **CodeQL:** Use a checked-in manual CodeQL workflow that runs a clean Android debug build for Java/Kotlin analysis instead of relying on GitHub's autobuild.

---

## v20.0 — Alabama Sturgeon (2026-06-30)

No major changes documented.

---

## v19.0 — Mandarinfish (2026-06-30)

No major changes documented.

---

## v18.0 — Leafy Seadragon (2026-06-30)

No major changes documented.

---

## v17.0 — Gulper Eel (2026-06-30)

No major changes documented.

---

## v16.0 — Barreleye (2026-06-30)

No major changes documented.

---

## v15.0 — Devil's Hole Pupfish (2026-06-30)

No major changes documented.

---

## v14.0 — Red Handfish (2026-06-30)

No major changes documented.

---

## v13.0 — Oarfish (2026-06-29)

No major changes documented.

---

## v12.0 — Coelacanth (2026-06-29)

Transition from dessert/gelato-flavor codenames to rare fish codenames for release milestones.

---

## v11.0 — Coconut (2026-06-28)

### Added
- **Media:** Improve video playback reliability and add visible preload/download progress feedback.

---

## v10.0 — Tiramisu (2026-06-28)

Initial release framework established.

---

## v9.0 — Choco (2026-06-28)

Early development release.

---

## v8.0 (2026-06-28)

### Added
- **Settings:** Add a configurable subscribed-feed thread limit with 6, 12, 18, and all-thread options.
- **Storage:** Add a saved-media folder picker and route custom-folder downloads through the selected Android folder.
- **Networking:** Add DNS-over-HTTPS provider selection for Cloudflare, OpenDNS, and NextDNS.
- **Docs:** Add an app screenshot to the README.

---

## v7.0 (2026-06-28)

### Security
- **Downloads:** Require HTTPS media downloads, keep sanitized file names inside `Downloads/Orbin`, and remove cleartext OkHttp connection support.
- **Privacy:** Make recent search history opt-in, keep HTTPS-only enforced in setup and settings, and move local release signing material outside the repository tree.
- **App lock:** Fail closed when biometric/device authentication is unavailable and enable secure-window protection while the app is locked.

### Added
- **Subscribed feed:** Add a continuous subscribed-board feed that loads followed boards with bounded request concurrency.
- **Search:** Add subscribed-board selection and content-type filters for posts, images, videos, audio, and URLs.
- **Maintenance:** Add Dependabot coverage for Gradle dependencies and GitHub Actions.

### Changed
- **Setup:** Expose the search-history privacy preference during onboarding and keep HTTPS-only presented as an always-on privacy boundary.

---

## v6.0 (2026-06-28)

### Security
- **Downloads:** Sanitise the remote-supplied file name (basename only, no separators, traversal or control characters) and only enqueue `http(s)` URLs, closing a path-traversal vector in the public Downloads folder.
- **Backups:** Disabled `allowBackup` and added data-extraction rules so local history, bookmarks, subscriptions and downloads are excluded from cloud backup and device transfer.
- **Comment parser:** Cap tag-nesting depth so a maliciously nested post can no longer overflow the stack and crash the app.
- **Networking:** Enforce the "HTTPS only" preference per-request (live) via an interceptor, so toggling it takes effect without an app restart.

### Added
- **Onboarding:** A first-run setup wizard (`:feature:onboarding`) that walks through subscribing to boards and the appearance / media / privacy preferences, then records a persisted flag so it only shows once.
- **Build system:** Multi-module Gradle setup with a version catalog and `build-logic` convention plugins (application/library/feature/compose/hilt/room/jvm-library).
- **`:core:model`:** Immutable domain entities (Board, Thread, Post, CatalogThread, MediaAttachment, Bookmark, History, Search) and a structured `PostComment` tree for rendering greentext, spoilers, quote links, and backlinks without exposing HTML to the UI.
- **`:provider:api`:** The `ImageBoardProvider` SPI, capabilities/metadata, a typed `ProviderException` hierarchy, and a `ProviderRegistry`.
- **`:core:common`:** `OrbinResult` with typed `DataError`, injectable coroutine dispatchers, and the `NetworkMonitor` contract.
- **`:domain`:** Repository contracts and use cases, including `BuildReplyGraphUseCase` (quote links -> backlinks).
- **`:network`:** Secure-by-default OkHttp client (HTTPS-only, optional DNS-over-HTTPS, configurable user-agent), and a connectivity-based `NetworkMonitor` implementation.
- **`:provider:vichan`:** Reference provider for vichan/4chan-compatible JSON APIs with a data-driven site configuration, an HTML comment parser, a DTO->domain mapper, and Hilt multibinding registration.
- **Persistence:** Room database (bookmarks, history, recent searches, downloads) with exported schemas; offline-friendly repositories.
- **Features:** Bookmarks (unread badges, watch toggle), reading history, board search with recent queries, all reachable from a Material 3 bottom navigation bar.
- **Media:** Media3/ExoPlayer video playback and a pinch-zoom swipe gallery opened from thread media.
- **Downloads:** Native download manager over the platform DownloadManager (notifications, resume, retry) with an in-app history screen.
- **Notifications:** Watched-thread background updates via a WorkManager worker and a swappable `ThreadNotifier` abstraction.
- **Testing:** ViewModel unit tests with fakes/Turbine and Roborazzi screenshot tests for the design system; a screenshots CI workflow.
- **Docs:** README, contributing guide, architecture overview, and the "add a provider" guide.
- **CI:** GitHub Actions workflows for build/test/lint and tag-driven signed releases.

---

## v5.0 (2026-06-27)

### Changed
- **Boards:** Replaced the board-setup slide-in panel with a full-screen board gallery of large, tappable tiles (the Tune action is now a grid icon). Favorites stay on the home list and subscriptions are managed under Settings, so the old setup panel was redundant.

---

## v4.0 (2026-06-27)

### Changed
- **Settings:** Board subscriptions are now managed from a dedicated **Subscriptions** screen under Settings, rather than the board-setup overlay.

### Fixed
- **Gallery:** Media can be swiped between items again — a zoomable image no longer consumes single-finger swipes unless it is zoomed in, so the pager scrolls as intended.

---

## v3.0 (2026-06-27)

Early development release. Limited documentation available.

---

## v2.0 (2026-06-27)

Early development release. Limited documentation available.

---

## v1.0 (2026-06-27)

Initial alpha release of Orbin — a modern Android image board client built with Kotlin, Jetpack Compose, and Material 3.

---

## Summary

**Total Releases:** 48+ versions  
**Development Timeline:** June 27 — July 13, 2026  
**Current Version:** v48 — Sirius B  
**Architecture:** Clean Architecture with multi-module Gradle setup, Jetpack Compose UI, Room persistence, and a provider abstraction for supporting multiple image board engines.

### Key Milestones

- **v6.0:** Foundation release with core architecture, onboarding, and secure-by-default networking
- **v7.0:** Subscribed feed and security hardening (app lock, privacy controls)
- **v23.0:** Shift to mythical-city codenames; biometric security improvements
- **v24.0:** Encryption at rest with SQLCipher and encrypted DataStore
- **v30.0:** Shift to smallest-known-star codenames; tablet support and full-screen feed
- **v36.0:** Multi-provider support (8chan.moe) with 20+ color themes
- **v37.0:** Fullscreen and auto-rotate video features; major dependency updates
- **v38.0:** Post/thread date display
- **v47.0:** 8kun.top support; Settings UI revert to filter chips; theme simplification
- **v48.0:** Board collapse/expand in subscribed feed
