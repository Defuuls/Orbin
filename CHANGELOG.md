# Changelog

All notable changes to Orbin are documented here. The format is based on
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and the project adheres to
[Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
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
  (quote links → backlinks).
- **`:network`:** secure-by-default OkHttp client (HTTPS-only, optional DNS-over-HTTPS,
  configurable user-agent), and a connectivity-based `NetworkMonitor` implementation.
- **`:provider:vichan`:** reference provider for vichan/4chan-compatible JSON APIs with a
  data-driven site configuration, an HTML comment parser, a DTO→domain mapper, and Hilt
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

[Unreleased]: https://github.com/Defuuls/Orbin/commits/main
