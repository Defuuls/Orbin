# Changelog

All notable changes to Orbin are documented here. The format is based on
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and the project adheres to
[Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [46-Epsilon Eridani] - 2026-07-13

### Removed
- **Tranchan provider:** removed the WakabaProvider implementation and all Tranchan-specific code,
  references, and infrastructure. The app now exclusively supports 4chan (Vichan) and BBW Chan
  (LynxChan).

### Changed
- **Documentation:** updated README to reflect current providers and recent feature additions
  (fullscreen video, auto-rotate, post dates, 20+ imageboard color palettes, updated tech stack).
- **Release naming:** continue smallest-known-star codenames for release milestones.

## [38-Ross 128] - 2026-07-12

### Added
- **Post & thread dates:** the catalog shows each thread's creation date, and every post header in
  the thread view shows the date and time it was posted.

### Changed
- **Release:** prepare the v38 release for the Ross 128 milestone.
- **Release naming:** continue smallest-known-star codenames for release milestones.

### Removed
- **Verify file host links:** removed the dormant setting and all remaining code behind the
  file-host link verification feature.

## [37-Wolf 359] - 2026-07-12

### Added
- **Fullscreen video:** a setting to play videos edge-to-edge in an immersive presentation
  (hiding the status bar and app chrome), plus a fullscreen toggle button in the video controls.
- **Auto-rotate video:** a setting to turn the screen to landscape automatically when a wide video
  starts playing, for fullscreen viewing.

### Changed
- **Dependencies:** updated the toolchain and libraries in one coordinated bump — Kotlin 2.4.0,
  Compose BOM 2026.06, compileSdk 37, OkHttp 5, Coil 3.5, Coroutines 1.11, and the AndroidX,
  testing, and GitHub Actions groups.
- **Release:** prepare the v37 release for the Wolf 359 milestone.
- **Release naming:** continue smallest-known-star codenames for release milestones.

### Removed
- **File-host link checker:** removed the gofile.io/fast-file.ru/mega.nz link alive/dead
  verification and its setting.

## [36-TRAPPIST-1] - 2026-07-11

### Added
- **8chan.moe:** added 8chan.moe as a selectable LynxChan provider. The network layer now clears
  its POWBlock proof-of-work gate and terms-of-service redirect transparently, so browsing works
  the same as any other site.
- **8chan themes:** ported 8chan's palette skins (Yotsuba, Tomorrow, Miku, Lain, Penumbra,
  Windows 95, and more) as selectable app color themes.

### Changed
- **Settings pickers:** appearance and content options (color theme, app icon, theme mode,
  thumbnail size, feed limits, and the active site) are now compact drop-downs instead of
  horizontally scrolling chip tiles, keeping the long theme list usable.
- **Release:** prepare the v36 release for the TRAPPIST-1 milestone.
- **Release naming:** continue smallest-known-star codenames for release milestones.

### Known limitations
- **7chan.org:** not added. 7chan runs KusabaX, which exposes no JSON API, and the site sits
  behind a Cloudflare JS challenge that the app's HTTP client cannot pass, so it cannot currently
  be supported as a functional provider.

## [35-Proxima Centauri] - 2026-07-10

### Added
- **Board labels:** the subscribed feed and search results now show the board name (e.g. `/g/`)
  next to the title of each post, so posts keep their board context even in full-screen mode
  where board headers are hidden.

### Changed
- **Release:** prepare the v35 release for the Proxima Centauri milestone.
- **Release naming:** continue smallest-known-star codenames for release milestones.

## [34-Dippin] - 2026-07-09

### Added
- **Settings:** add an Internal updater toggle so update checks can be managed from app settings.

### Changed
- **Feed search:** move subscribed-thread search into the top of the subscribed feed and scope it
  to subscribed boards only.
- **Navigation:** remove Search as a standalone bottom-navigation tab now that subscribed search
  lives in the feed.
- **Release:** prepare the v34 release for the Dippin milestone.

## [33-CM Draconis A] - 2026-07-09

### Changed
- **Gallery:** the board picker now offers only subscribed boards (honouring the NSFW-board
  visibility setting), matching the feed instead of listing every board on the provider.
- **Bookmarks:** the Bookmarks bottom-navigation tab is removed; bookmarks now live in a
  Bookmarks tab inside the Gallery view, keeping the watch toggle, unread badges, and remove
  actions.
- **Release:** prepare the v33 release for the CM Draconis A milestone.
- **Release naming:** continue smallest-known-star codenames for release milestones.

## [32-EQ Pegasi A] - 2026-07-09

### Fixed
- **Full-screen feed:** drop the pinned board headers from the feed while the full-screen
  option is on — nothing stays fixed at the top and boards are no longer listed between
  threads, so the feed is a total full-screen view.

### Changed
- **Release:** prepare the v32 release for the EQ Pegasi A milestone.
- **Release naming:** continue smallest-known-star codenames for release milestones.

## [31-Fomalhaut C] - 2026-07-09

### Fixed
- **Full-screen feed:** the option now actually goes full screen — the status and navigation
  bars hide together with the feed chrome while scrolling, and the duplicated window-inset
  padding that left white strips at the top and bottom of the feed view is removed.

### Changed
- **Release:** prepare the v31 release for the Fomalhaut C milestone.
- **Release naming:** continue smallest-known-star codenames for release milestones.

## [30-Janus] - 2026-07-09

### Added
- **Feed chrome:** add iOS-style tap-to-top behavior from the top feed, board, and thread bars.
- **Settings:** add a Full-screen feed option that lets the subscribed feed hide top and bottom
  bars while scrolling for more reading space.
- **Tablet feed:** add an initial tablet mock-up with a floating dock, combined subscribed-feed
  controls, auto-hiding chrome, and old-Reddit-style thumbnail/text rows.

### Fixed
- **Media CDN usage:** cache video media through Media3 and avoid no-cache request headers for
  static media so repeated viewing does not churn CDN requests.

### Changed
- **Release:** prepare the v30 release for the Janus milestone.
- **Release naming:** smallest-known-star names replace bear-family codenames for release
  milestones.

## [25.2.1] - 2026-07-05

### Fixed
- **Thread links:** exporting thread links now saves to the configured saved media folder,
  falling back to `Downloads/Orbin` when the default save location is active.

### Changed
- **Release:** prepare the v25.2.1 release for the Cleopatra milestone.
- **Release naming:** continue famous-seductress codenames for release milestones.

## [25.2] - 2026-07-05

### Changed
- **Release:** prepare the v25.2 release for the Casey Jones milestone.
- **Release naming:** continue Grateful Dead codenames for release milestones.

## [25.1] - 2026-07-05

### Changed
- **Release:** prepare the v25.1 release for the Box of Rain milestone.
- **Release naming:** continue mythical-city codenames for release milestones.

## [25.0] - 2026-07-05

### Changed
- **Release:** prepare the v25.0 release for the Franklin's Tower milestone.
- **Release naming:** continue mythical-city codenames for release milestones.

## [24.4] - 2026-07-05

### Changed
- **Release:** prepare the v24.4 release for the Sugar Magnolia milestone.
- **Release naming:** continue mythical-city codenames for release milestones.

## [24.3] - 2026-07-05

### Changed
- **Release:** prepare the v24.3 release for the Truckin' milestone.
- **Release naming:** continue mythical-city codenames for release milestones.

## [24.2] - 2026-07-05

### Changed
- **Release:** prepare the v24.2 release for the Ripple milestone.
- **Release naming:** continue mythical-city codenames for release milestones.

## [24.1.1] - 2026-07-04

### Changed
- **Release:** prepare the v24.1.1 release for the Arcadia milestone.
- **Release naming:** continue mythical-city codenames for release milestones.

## [24.1] - 2026-07-04

### Changed
- **Release:** prepare the v24.1 release for the Arcadia milestone.
- **Release naming:** continue mythical-city codenames for release milestones.

## [24.0.4] - 2026-07-04

### Changed
- **Release:** prepare the v24.0.4 release for the Elysium milestone.
- **Release naming:** continue mythical-city codenames for release milestones.

## [24.0.3] - 2026-07-04

### Changed
- **Release:** prepare the v24.0.3 release for the Elysium milestone.
- **Release naming:** continue mythical-city codenames for release milestones.

## [24.0.2] - 2026-07-04

### Changed
- **Release:** prepare the v24.0.2 release for the Elysium milestone.
- **Release naming:** continue mythical-city codenames for release milestones.

## [24.0.1] - 2026-07-04

### Changed
- **Release:** prepare the v24.0.1 release for the Elysium milestone.
- **Release naming:** continue mythical-city codenames for release milestones.

## [24.0] - 2026-07-04

### Changed
- **Release:** prepare the v24.0 release for the Elysium milestone.
- **Release naming:** continue mythical-city codenames for release milestones.

### Security
- **Encryption at rest:** the local database (history, bookmarks, downloads, recent searches) is
  now encrypted with SQLCipher, and app settings are stored in an encrypted DataStore. Both are
  protected by a hardware-backed Android Keystore key that never leaves the TEE/StrongBox, so a
  copy of the app's data directory yields only ciphertext.

### Changed
- **One-time data reset:** because encryption changes the on-disk format, existing history,
  bookmarks, downloads, and settings — **including favorites and subscriptions** — are reset once
  when updating to this version.

## [23.9] - 2026-07-04

### Changed
- **Release:** prepare the v23.9 release for the Thule milestone.
- **Release naming:** continue mythical-city codenames for release milestones.

## [23.8] - 2026-07-04

### Fixed
- **App lock:** prevent biometric unlock from hanging by cancelling stale prompts on app
  background/destruction, ignoring stale callbacks, and timing out stuck unlock attempts.
- **Permissions:** wait until Orbin is ready and unlocked before requesting notification
  permission so the Android permission dialog does not race the biometric prompt.
- **Release:** prepare the v23.8 release for the Delilah milestone.
- **Release naming:** famous-seductress codenames replace mythical-city codenames for
  release milestones.

## [23.7] - 2026-07-04

### Changed
- **Release:** prepare the v23.7 release for the Hyperborea milestone.
- **Release naming:** continue mythical-city codenames for release milestones.

## [23.6] - 2026-07-04

### Changed
- **Release:** prepare the v23.6 release for the Lyonesse milestone.
- **Release naming:** continue mythical-city codenames for release milestones.

## [23.5] - 2026-07-04

### Changed
- **Release:** prepare the v23.5 release for the Camelot milestone.
- **Release naming:** continue mythical-city codenames for release milestones.

## [23.4] - 2026-07-03

### Changed
- **Release:** prepare the v23.4 release for the Avalon milestone.
- **Release naming:** continue mythical-city codenames for release milestones.

## [23.3] - 2026-07-03

### Changed
- **Release:** prepare the v23.3 release for the Shangri-La milestone.
- **Release naming:** continue mythical-city codenames for release milestones.

## [23.1] - 2026-07-02

### Fixed
- **4chan loading:** accept numeric media IDs from live catalog/thread responses so
  boards, feeds, and threads load when the API sends `tim` as a number.
- **Release:** prepare the v23.1 release for the El Dorado milestone.

## [23.0] - 2026-07-01

### Security
- **App lock:** re-arm biometric/device-credential lock after backgrounding, keep locked
  content gated until persisted settings load, and provide recovery when Android auth is unavailable.
- **Comment parser:** reject unsafe numeric HTML entity code points and decode astral Unicode
  characters without truncation.
- **HTTPS policy:** remove the unused mutable HTTPS-only setter so the always-on transport
  boundary is reflected in the settings API.

### Changed
- **Release:** prepare the v23.0 release for the Atlantis milestone.
- **Release naming:** mythical-city codenames replace rare-fish codenames for release milestones.

## [22.0] - 2026-06-30

### Changed
- **Release:** prepare the v22.0 release for the Largetooth Sawfish milestone.
- **Security:** resolve Dependabot transitive dependency alerts by applying patched
  Gradle plugin-classpath and project dependency versions.

## [21.0] - 2026-06-30

### Changed
- **Release:** prepare the v21.0 release for the Sakhalin Sturgeon milestone.
- **CodeQL:** use a checked-in manual CodeQL workflow that runs a clean Android debug build
  for Java/Kotlin analysis instead of relying on GitHub's autobuild.

## [20.0] - 2026-06-30

### Changed
- **Release:** prepare the v20.0 release for the Alabama Sturgeon milestone.
- **Release naming:** continue rare fish codenames for release milestones.

## [19.0] - 2026-06-30

### Changed
- **Release:** prepare the v19.0 release for the Mandarinfish milestone.
- **Release naming:** continue rare fish codenames for release milestones.

## [18.0] - 2026-06-30

### Changed
- **Release:** prepare the v18.0 release for the Leafy Seadragon milestone.
- **Release naming:** continue rare fish codenames for release milestones.

## [17.0] - 2026-06-30

### Changed
- **Release:** prepare the v17.0 release for the Gulper Eel milestone.
- **Release naming:** continue rare fish codenames for release milestones.

## [16.0] - 2026-06-30

### Changed
- **Release:** prepare the v16.0 release for the Barreleye milestone.
- **Release naming:** continue rare fish codenames for release milestones.

## [15.0] - 2026-06-30

### Changed
- **Release:** prepare the v15.0 release for the Devil's Hole Pupfish milestone.
- **Release naming:** continue rare fish codenames for release milestones.

## [14.0] - 2026-06-30

### Changed
- **Release:** prepare the v14.0 release for the Red Handfish milestone.
- **Release naming:** continue rare fish codenames for release milestones.

## [13.0] - 2026-06-29

### Changed
- **Release:** prepare the v13.0 release for the Oarfish milestone.
- **Release naming:** continue rare fish codenames for release milestones.

## [12.0] - 2026-06-29

### Changed
- **Release:** prepare the v12.0 release for the Coelacanth milestone.
- **Release naming:** rare fish names replace dessert and gelato-flavor codenames for new
  release milestones.

## [11.0] - 2026-06-28

### Added
- **Release:** prepare the v11.0 release for the Coconut milestone.
- **Media:** improve video playback reliability and add visible preload/download progress feedback.

## [10.0] - 2026-06-28

### Added
- **Release:** prepare the v10.0 release for the Tiramisu milestone.

## [9.0] - 2026-06-28

### Added
- **Release:** prepare the v9.0 release for the Choco milestone.

## [8.0] - 2026-06-28

### Added
- **Settings:** add a configurable subscribed-feed thread limit with 6, 12, 18, and all-thread
  options.
- **Storage:** add a saved-media folder picker and route custom-folder downloads through the
  selected Android folder.
- **Networking:** add DNS-over-HTTPS provider selection for Cloudflare, OpenDNS, and NextDNS.
- **Docs:** add an app screenshot to the README.

## [7.0] - 2026-06-28

### Security
- **Downloads:** require HTTPS media downloads, keep sanitized file names inside
  `Downloads/Orbin`, and remove cleartext OkHttp connection support.
- **Privacy:** make recent search history opt-in, keep HTTPS-only enforced in setup and
  settings, and move local release signing material outside the repository tree.
- **App lock:** fail closed when biometric/device authentication is unavailable and enable
  secure-window protection while the app is locked.

### Added
- **Subscribed feed:** add a continuous subscribed-board feed that loads followed boards with
  bounded request concurrency.
- **Search:** add subscribed-board selection and content-type filters for posts, images, videos,
  audio, and URLs.
- **Maintenance:** add Dependabot coverage for Gradle dependencies and GitHub Actions.

### Changed
- **Setup:** expose the search-history privacy preference during onboarding and keep HTTPS-only
  presented as an always-on privacy boundary.

## [6.0] - 2026-06-28

### Security
- **Downloads:** sanitise the remote-supplied file name (basename only, no separators,
  traversal or control characters) and only enqueue `http(s)` URLs, closing a path-traversal
  vector in the public Downloads folder.
- **Backups:** disabled `allowBackup` and added data-extraction rules so local history,
  bookmarks, subscriptions and downloads are excluded from cloud backup and device transfer.
- **Comment parser:** cap tag-nesting depth so a maliciously nested post can no longer
  overflow the stack and crash the app.
- **Networking:** enforce the "HTTPS only" preference per-request (live) via an interceptor,
  so toggling it takes effect without an app restart.

### Added
- **Onboarding:** a first-run setup wizard (`:feature:onboarding`) that walks through
  subscribing to boards and the appearance / media / privacy preferences, then records a
  persisted flag so it only shows once.
- **Build system:** multi-module Gradle setup with a version catalog and `build-logic`
  convention plugins (application/library/feature/compose/hilt/room/jvm-library).
- **`:core:model`:** immutable domain entities (Board, Thread, Post, CatalogThread,
  MediaAttachment, Bookmark, History, Search) and a structured `PostComment` tree for rendering
  greentext, spoilers, quote links, and backlinks without exposing HTML to the UI.
- **`:provider:api`:** the `ImageBoardProvider` SPI, capabilities/metadata, a typed
  `ProviderException` hierarchy, and a `ProviderRegistry`.
- **`:core:common`:** `OrbinResult` with typed `DataError`, injectable coroutine dispatchers, and
  the `NetworkMonitor` contract.
- **`:domain`:** repository contracts and use cases, including `BuildReplyGraphUseCase`
  (quote links -> backlinks).
- **`:network`:** secure-by-default OkHttp client (HTTPS-only, optional DNS-over-HTTPS,
  configurable user-agent), and a connectivity-based `NetworkMonitor` implementation.
- **`:provider:vichan`:** reference provider for vichan/4chan-compatible JSON APIs with a
  data-driven site configuration, an HTML comment parser, a DTO->domain mapper, and Hilt
  multibinding registration.
- **Persistence:** Room database (bookmarks, history, recent searches, downloads)
  with exported schemas; offline-friendly repositories.
- **Features:** bookmarks (unread badges, watch toggle), reading history, board
  search with recent queries, all reachable from a Material 3 bottom navigation bar.
- **Media:** Media3/ExoPlayer video playback and a pinch-zoom swipe gallery opened
  from thread media.
- **Downloads:** native download manager over the platform DownloadManager
  (notifications, resume, retry) with an in-app history screen.
- **Notifications:** watched-thread background updates via a WorkManager worker and
  a swappable `ThreadNotifier` abstraction.
- **Testing:** ViewModel unit tests with fakes/Turbine and Roborazzi screenshot tests
  for the design system; a screenshots CI workflow.
- **Docs:** README, contributing guide, architecture overview, and the "add a provider" guide.
- **CI:** GitHub Actions workflows for build/test/lint and tag-driven signed releases.

## [5.0] - 2026-06-27

### Changed
- **Boards:** replaced the board-setup slide-in panel with a full-screen board gallery of
  large, tappable tiles (the Tune action is now a grid icon). Favorites stay on the home
  list and subscriptions are managed under Settings, so the old setup panel was redundant.

## [4.0] - 2026-06-27

### Changed
- **Settings:** board subscriptions are now managed from a dedicated **Subscriptions**
  screen under Settings, rather than the board-setup overlay.

### Fixed
- **Gallery:** media can be swiped between items again — a zoomable image no longer
  consumes single-finger swipes unless it is zoomed in, so the pager scrolls as intended.

[Unreleased]: https://github.com/Defuuls/Orbin/compare/v38-Ross-128...HEAD
[38-Ross 128]: https://github.com/Defuuls/Orbin/compare/v37-Wolf-359...v38-Ross-128
[37-Wolf 359]: https://github.com/Defuuls/Orbin/compare/v36-TRAPPIST-1...v37-Wolf-359
[36-TRAPPIST-1]: https://github.com/Defuuls/Orbin/compare/v35-Proxima-Centauri...v36-TRAPPIST-1
[35-Proxima Centauri]: https://github.com/Defuuls/Orbin/compare/v34-Dippin...v35-Proxima-Centauri
[34-Dippin]: https://github.com/Defuuls/Orbin/compare/v33-CM-Draconis-A...v34-Dippin
[33-CM Draconis A]: https://github.com/Defuuls/Orbin/compare/v32-EQ-Pegasi-A...v33-CM-Draconis-A
[32-EQ Pegasi A]: https://github.com/Defuuls/Orbin/compare/v31-Fomalhaut-C...v32-EQ-Pegasi-A
[31-Fomalhaut C]: https://github.com/Defuuls/Orbin/compare/v30-Janus...v31-Fomalhaut-C
[30-Janus]: https://github.com/Defuuls/Orbin/compare/v29-Brown-Bears...v30-Janus
[25.2.1]: https://github.com/Defuuls/Orbin/compare/v25.2...v25.2.1
[25.2]: https://github.com/Defuuls/Orbin/compare/v25.1...v25.2
[25.1]: https://github.com/Defuuls/Orbin/compare/v25.0...v25.1
[25.0]: https://github.com/Defuuls/Orbin/compare/v24.4...v25.0
[24.4]: https://github.com/Defuuls/Orbin/compare/v24.3...v24.4
[24.3]: https://github.com/Defuuls/Orbin/compare/v24.2...v24.3
[24.2]: https://github.com/Defuuls/Orbin/compare/v24.1.1...v24.2
[24.1.1]: https://github.com/Defuuls/Orbin/compare/v24.1...v24.1.1
[24.1]: https://github.com/Defuuls/Orbin/compare/v24.0.4...v24.1
[24.0.4]: https://github.com/Defuuls/Orbin/compare/v24.0.3...v24.0.4
[24.0.3]: https://github.com/Defuuls/Orbin/compare/v24.0.2...v24.0.3
[24.0.2]: https://github.com/Defuuls/Orbin/compare/v24.0.1...v24.0.2
[24.0.1]: https://github.com/Defuuls/Orbin/compare/v24.0...v24.0.1
[24.0]: https://github.com/Defuuls/Orbin/compare/v23.9...v24.0
[23.9]: https://github.com/Defuuls/Orbin/compare/v23.8...v23.9
[23.8]: https://github.com/Defuuls/Orbin/compare/v23.7...v23.8
[23.7]: https://github.com/Defuuls/Orbin/compare/v23.6...v23.7
[23.6]: https://github.com/Defuuls/Orbin/compare/v23.5...v23.6
[23.5]: https://github.com/Defuuls/Orbin/compare/v23.4...v23.5
[23.4]: https://github.com/Defuuls/Orbin/compare/v23.3...v23.4
[23.3]: https://github.com/Defuuls/Orbin/compare/v23.1...v23.3
[23.0]: https://github.com/Defuuls/Orbin/compare/v22.0...v23.0
[22.0]: https://github.com/Defuuls/Orbin/compare/v21.0...v22.0
[21.0]: https://github.com/Defuuls/Orbin/compare/v20.0...v21.0
[20.0]: https://github.com/Defuuls/Orbin/compare/v19.0...v20.0
[19.0]: https://github.com/Defuuls/Orbin/compare/v18.0...v19.0
[18.0]: https://github.com/Defuuls/Orbin/compare/v17.0...v18.0
[17.0]: https://github.com/Defuuls/Orbin/compare/v16.0...v17.0
[16.0]: https://github.com/Defuuls/Orbin/compare/v15.0...v16.0
[15.0]: https://github.com/Defuuls/Orbin/compare/v14.0...v15.0
[14.0]: https://github.com/Defuuls/Orbin/compare/v13.0...v14.0
[13.0]: https://github.com/Defuuls/Orbin/compare/v12.0...v13.0
[12.0]: https://github.com/Defuuls/Orbin/compare/v11.0...v12.0
[11.0]: https://github.com/Defuuls/Orbin/compare/v10.0...v11.0
[10.0]: https://github.com/Defuuls/Orbin/compare/v9.0...v10.0
[9.0]: https://github.com/Defuuls/Orbin/releases/tag/v9.0
[8.0]: https://github.com/Defuuls/Orbin/releases/tag/v8.0
[7.0]: https://github.com/Defuuls/Orbin/releases/tag/v7.0
[6.0]: https://github.com/Defuuls/Orbin/releases/tag/v6.0
[5.0]: https://github.com/Defuuls/Orbin/releases/tag/v5.0
[4.0]: https://github.com/Defuuls/Orbin/releases/tag/v4.0
