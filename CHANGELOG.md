# Changelog

All notable changes to Orbin are documented here. The format is based on
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and the project adheres to
[Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Changed
- **Settings is a hub, not one long scroll.** The single Settings screen held eight sections and
  35+ options in one column. It now lists six categories — Content & Feed, Notifications,
  Appearance, Media & Playback, Privacy & Network, Storage & Backup — each opening its own screen,
  with Advanced nested one level under Privacy & Network since it's the one page most people never
  open. Every option kept its exact label, default, and behavior; only where it lives changed.

## [67-Pollux] - 2026-08-07

### Removed
- **8kun support.** The 8kun.top LynxChan instance (added in v46–v48) is no longer bundled. It
  drops out of the Site provider picker in Settings automatically, since the provider seam has no
  other place that names it — see `LynxChanProviderModule`. BBW Chan (LynxChan) and 4chan
  (Vichan) are unaffected.

## [66-Spica] - 2026-08-07

### Fixed
- **Typed text in the muted/hidden tags field appeared reversed.** The field was bound directly to
  settings state that updates asynchronously, so the cursor reset to the start after every
  keystroke and each new character landed before the last. Typing is now backed by local field
  state that syncs to storage in the background instead of driving the field directly.
- **14 lint errors in the media module.** Unstable Media3 cache/playback APIs were used without the
  required opt-in, and a thumbnail composable's default `Modifier` value didn't follow Compose's
  modifier-parameter convention.
- A disabled row (e.g. Settings' always-on "HTTPS only" display) was announced to screen readers as
  a disabled button. Rows built from `ModernListItem`/`ModernCompactListItem` with no `onClick` no
  longer attach a clickable modifier at all.
- `ModernCardListItem` attached its own `clickable` on top of the click handling `ModernCard`
  already provides, doubling the touch target and gesture handling for every card list item.

### Added
- A contrast regression test for the 20 ported imageboard skins, checking body text against WCAG AA
  (4.5:1). Existing low-contrast skins are authentic to their source CSS and kept for that reason;
  the test catches any new skin falling short by accident.

## [65-Antares] - 2026-08-07

### Added
- **The catalog and a thread side by side on wide screens.** Above 840dp, opening a thread no
  longer replaces the catalog — a tablet reader keeps the list they are working through. Rotating
  across the threshold promotes the open thread to a full screen rather than losing it.
- **Pull to refresh** on the subscribed feed, the board catalog and a thread. The catalog had no
  refresh affordance at all — Paging only reloads on its own invalidation, so a stale catalog
  stayed stale until the screen was left and re-entered.
- **Spoilers reveal on tap.** They were blacked out permanently, so spoilered post text was simply
  unreadable. Each span reveals on its own; while hidden, a quote link inside a spoiler is inert,
  because it would otherwise take the tap meant to reveal it and navigate the reader to a post they
  could not yet see they were offered.
- **Haptic feedback** on the pull-to-refresh threshold, spoiler reveals and post collapsing. There
  was none anywhere in the app before.

### Fixed
- **A thread could not actually be refreshed.** `ThreadRepositoryImpl` caches for 30 minutes with
  no way to bust it, so asking for new replies returned the snapshot already on screen for the rest
  of that window. `observeThread`/`refreshThread` take a `forceRefresh` flag.
- **Backlink chips were below the minimum touch target.** A bare `clickable` on `labelSmall` text
  is roughly a 16dp target against a 48dp minimum; the touch bounds now expand without inflating
  the glyphs, so the dense catalog look is unchanged.
- **The thread media grid did not reflow.** It was pinned to three columns, right on a phone in
  portrait and stretched absurdly on a tablet or in landscape. It now sizes adaptively like every
  other grid in the app.
- The settings chip row was the one lazy list of eighteen without a stable item key.

## [64-Aldebaran] - 2026-08-06

### Added
- **Baseline profiles.** A `:benchmark` module records the classes used on the startup and feed
  path so ART compiles them ahead of time instead of interpreting them on first launch. Generating
  a profile needs real hardware, so it is a deliberate manual step
  (`./gradlew :app:generateReleaseBaselineProfile`) with the result committed.
- **Instrumentation tests actually run in CI, and actually test something.** The `androidTest`
  sources were compiled but never executed — and had never worked: every test called Compose
  assertion APIs *inside* the composition lambda, asserting against an empty tree because the
  screen under test was never composed. The two feature-screen files are rewritten to compose the
  real screen over in-memory repositories and assert real behaviour — that toggles write through,
  that quiet hours are gated on watch notifications, that retry appears only on failed downloads,
  and that the DNS-fallback notice follows the privacy monitor. A new workflow boots an emulator
  and runs them on every push and PR. The app-launch smoke test is rewritten but `@Ignore`d:
  `MainActivity` renders no Compose hierarchy under instrumentation for a reason not yet found.

### Fixed
- **The protobuf security pin broke instrumentation tests.** `com.google.protobuf:protobuf-*` was
  forced to `3.25.5`, which carries the CVE-2024-7254 fix but predates the `RuntimeVersion` class
  AGP's Unified Test Platform requires — so every instrumentation test died with
  `NoClassDefFoundError` the moment one actually ran. Nothing had run them, so nothing noticed.
  Raised to 4.35.1, which carries the same CVE fix and the class.
- **The Netty security pin referenced a version that does not exist.** Both the plugin classpath
  and project configurations forced `io.netty:netty-*` to `4.1.139.Final`; the newest release on
  that line is `4.1.136.Final`. Nothing had failed only because no resolved dependency pulled in
  Netty — the first one that did broke the build outright. The v57 note claiming Netty was
  "upgraded to 4.1.139.Final, resolving 9 CVEs" was therefore never accurate.
- **Screenshot tests recorded nothing and passed.** A filter meant to keep them out of the
  aggregate `test` task matched by task name, and the Roborazzi record and verify tasks *are* that
  task with extra properties — so the tasks that exist to run screenshot tests excluded them.
  They were the only tests in the module, so both reported success having captured and compared
  nothing.
- **Release-signing fallback now exists.** The developer guide promised local release builds fall
  back to debug signing without secrets; nothing implemented it, and the signing guard fired on any
  task whose name contained "Release" — including baseline profile generation, which never leaves
  the machine.
- **CI never enforced warnings-as-errors.** The developer guide said `orbin.warningsAsErrors=true`
  is what CI uses; no workflow set it. Six warnings had accumulated behind that gap despite an
  earlier release claiming they were all cleared. They are fixed and the flag is now actually
  passed, so the next one fails the build instead of joining them.

### Changed
- **Internal:** migrated off the deprecated `rememberTransformableState`, `TabRow`,
  `Modifier.menuAnchor()` and `MenuAnchorType` APIs, and removed two conditions the compiler could
  already prove.

## [63-Acrux] - 2026-08-05

Two settings stop being on/off switches, for different reasons. "Refresh feed on return" gains
range: both ends of the old switch are still reachable, with intervals between them. Encrypted DNS
loses its off position outright — that one is the point.

### Changed
- **Encrypted DNS is no longer a toggle.** DNS over HTTPS could be switched off, and shipped off
  by default, so the protection was absent for anyone who never went looking for it. It is now
  always on and the setting is *which* resolver answers your lookups — Cloudflare, OpenDNS or
  NextDNS. `DohConfig` has no "disabled" case at all, so plaintext resolution is not a state the
  app can be in rather than a state it merely avoids. The DoH switch is gone from first-run setup
  too.
- **"Refresh feed on return" is a timeframe, not a switch.** Choose how stale the feed may be
  before coming back to it reloads it: Always, 1, 5, 15 or 30 minutes, or Never. *Always* and
  *Never* are exactly what the old on and off positions did, and an existing preference carries
  over to the matching end rather than resetting.
- **Settings slides in over the screen behind it** instead of pushing it aside. The screen you came
  from stays where it was and is revealed again on the way back.

### Added
- **A visible notice when a network blocks your resolver.** Removing the off switch removed an
  escape hatch: some networks block the well-known DoH endpoints, and refusing to resolve would
  have left the app unable to load anything with no in-app remedy. Lookups now fall back to the
  system resolver in that case, and Settings says so — being quietly downgraded is worse than the
  toggle that was removed. The notice clears itself once an encrypted lookup succeeds again.
- **Open threads as** (Settings → Appearance): threads can open as a **Page**, the ordinary push
  that takes the feed with it, or **Slide over**, laying the thread on top with the feed left in
  place underneath. Page remains the default.

## [62-Hadar] - 2026-08-05

### Added
- **Check for updates,** in Settings → Network & privacy. It asks GitHub for the newest published
  release, compares it with the running build, and links to the release page when there is one.
  Only release metadata is fetched — Orbin does not download or install an APK, so the signed
  build and its checksum stay a deliberate manual step.

### Fixed
- **The "Internal updater" toggle now does something.** It shipped in v34 and has been inert ever
  since: no part of the app read it, and there was no update-checking code to read it. It now
  gates the new **Check for updates** row.

## [61-Achernar] - 2026-08-05

### Added
- **Saved searches are included in backups,** alongside settings, boards and bookmarks.

### Fixed
- **The database would have destroyed your data on the next schema change.** It was configured to
  recreate itself rather than migrate — a leftover from before the first release that survived
  sixty of them. Any future schema change would have silently dropped bookmarks, history,
  downloads, recent searches and saved searches, with no Android backup to recover from. Schema
  changes now migrate properly, and a missing migration fails loudly instead of deleting anything.

### Changed
- **Internal:** first unit tests for the settings module, covering the backup round trip; all
  compiler deprecation warnings cleared.

## [60-Procyon] - 2026-08-05

### Added
- **Backup and restore:** Settings → Storage can export your settings, subscribed and favourite
  boards, and bookmarks to a file you choose, and restore them afterwards. Orbin keeps everything
  encrypted on device and opts out of Android's cloud backup, so a reinstall used to lose all of
  it; this is the deliberate alternative. Importing **adds** boards and bookmarks rather than
  replacing them, so restoring cannot wipe an existing setup. The exported file is plain,
  unencrypted JSON — keep it somewhere you trust.
- **Image cache limit:** choose how much disk the image cache may use — 128, 256, 512 or 1024 MB,
  under Settings → Storage. Applies on the next app start.
- **Advanced network settings:** a custom user agent, connect and read timeouts, and a
  certificate-revocation toggle, under Settings → Advanced. Revocation checking stays off by
  default: it is slow and widely blocked, and when it fails it looks like sites being broken
  rather than a warning.

### Changed
- **Thumbnails appear sooner:** the small preview is painted immediately while the
  full-resolution image loads over it, instead of the cell staying blank for the whole fetch.
- **Settings are filed by what they affect:** *Threads per board* moved to Content, and *Image
  cache limit* to Storage.
- **App icon switching moved off the main thread** and now skips work when the icon has not
  changed, so it no longer costs frames on every launch.

### Fixed
- **Backups silently dropped some settings on import.** The image cache limit and the three
  network settings were written to the backup file but discarded when restoring it.

## [59-Betelgeuse] - 2026-08-04

A maintenance release. No user-facing behaviour changes; the launcher fix from 58-Capella is
included, so this is equally safe as a fresh install.

### Changed
- **Build toolchain:** Android Gradle Plugin 9.3.0 → 9.3.1 and Kotlin 2.4.0 → 2.4.10. Kotlin had
  been held at 2.4.0 because CodeQL's Kotlin extractor did not support the newer release; CodeQL
  now analyses 2.4.10 successfully.
- **CI:** `actions/setup-java` 5 → 5.6.0.

### Documentation
- **Wiki brought current with v58,** after drifting 24 releases: release history extended through
  v58, the settings guide gained the Notifications section and the media settings added since
  v34, and two errors in the developer guide were corrected (`compileSdk` is 37, and release tags
  must be annotated).
- **Release codename scheme documented as practised** — prominent naked-eye stars since v49.

## [58-Capella] - 2026-08-01

### Fixed
- **App would not open from the launcher:** tapping the icon opened App Info instead of the app.
  Switching icons could disable the alias the launcher had pinned, and could leave the package with
  no enabled launcher entry at all. There is now exactly one alias per icon variant, the selected
  one is enabled before the others are disabled, and no variant can go unmanaged.

  Installs already left without a launcher entry cannot be repaired by an update, because Android
  persists per-component enable-state across updates — those need an uninstall and reinstall.
- **Duplicate launcher icon:** the app could appear twice in the launcher after selecting an icon.

## [57-Arcturus] - 2026-08-01

### Added
- **Selectable app icons:** five launcher icon options — Orbital Orb, Nested Rings, Abstract Flow,
  Minimalist Essence and Dual Gradient — chosen from Settings and applied without a restart.

### Fixed
- **Blurry media thumbnails:** thumbnails rendered the provider's ~250px preview even in layouts that
  display it far larger, so the image was stretched well past its native resolution. Still images now
  load their full-resolution source in the FILL and LARGE grid sizes and in the full-width post
  layouts, while the compact and medium sizes keep the smaller preview, which is already sharp at
  those dimensions. Video, audio and animated attachments are unaffected.
- **Silent app icon failures:** icon switching logs when a launcher rejects the change instead of
  discarding the error.

### Changed
- **Netty upgraded to 4.1.139.Final,** resolving 9 CVEs covering ByteBuf leaks, infinite loops, SPDY
  vulnerabilities, CORS bypasses and injection attacks.
- **Single icon-switching path:** removed the duplicate `IconSwitcher`, leaving `AppIconManager` as
  the one implementation, which reapplies the icon from the persisted setting on every launch.

## [56-Vega] - 2026-07-31

### Added
- **Material Design 3 component library:** a shared set of modernized components, with the settings,
  board gallery and subscriptions screens and the app navigation rebuilt on top of it.

### Changed
- **Startup and scrolling performance:** WorkManager initialization moved off the main thread,
  thread caching and image preloading added, and ThreadScreen recomposition reduced by correcting
  `remember` dependencies and memoizing expensive work.
- **Network efficiency:** HTTP disk caching added to the shared OkHttp client.

## [55-Polaris] - 2026-07-29

### Added
- **Media scroll settings:** added optional settings to enable/disable media carousel independently
  for thread view and board view. Thread view is enabled by default; board view is disabled by default.
  Toggle "Media scroll in thread" and "Media scroll in board" in Settings > Media to control behavior.
- **Board view media carousel:** subscribed feed thread preview cards now support horizontal scrolling
  through multiple attachments when media scroll is enabled, with swipe gestures and page counter badge.

### Changed
- **Media carousel UX:** enhanced carousel implementation with consistent page counter styling and
  improved attachment navigation across all views (thread and board feeds).

## [54-Canopus] - 2026-07-29

### Added
- **Video indicators:** replaced "VID" text labels with Material3 PlayArrow icons in the subscribed
  feed and gallery views, making video and audio attachments more visually distinctive.
- **Media carousel:** posts with multiple attachments in thread view now support horizontal scrolling
  with swipe gestures. A page counter badge displays the current position (e.g., "2/5").

## [53-Sirius] - 2026-07-28

## [52-Wolf 359] - 2026-07-25

### Added
- **Relative post times:** the thread viewer now shows each post's age as a compact relative time
  ("just now", "5m", "3h", "2d"), falling back to the absolute date for posts older than a week.

### Changed
- **Post text rendering:** migrated the shared post renderer off the deprecated Compose
  `ClickableText` to the modern `LinkAnnotation` API, so quote links and URLs are exposed to
  accessibility services (announced as links by TalkBack). Catalog and feed cards now use a dedicated
  non-interactive preview renderer, so tapping anywhere on a preview reliably opens the thread.
- **Loading / empty / error states:** the shared placeholders share a single scaffold with clearer
  iconography and an accessible loading label for a more consistent look across screens.
- **Build:** updated the Android Gradle Plugin to 9.3.0.
- **Internal:** migrated all `hiltViewModel` call sites to `androidx.hilt.lifecycle.viewmodel.compose`,
  clearing the framework deprecation warning.

### Fixed
- **Thread collapse persistence:** collapsed posts now stay collapsed across configuration changes
  (e.g. screen rotation) instead of expanding again.

### Security
- **Dependency hardening:** corrected the Commons IO dependency override (it matched the wrong Maven
  group and never applied) so the patched `commons-io` 2.20.0 is enforced across all build
  configurations.

## [50-Fomalhaut] - 2026-07-14

### Added
- **Collapse all / Expand all:** added convenient toolbar buttons to collapse or expand all
  subscribed boards at once, for quickly toggling between full feed and collapsed board headers.
- **Enhanced top bar:** the subscribed feed title bar now displays the app name "Orbin" with a
  colored indicator square showing the currently selected theme's primary color.

## [49-Altair] - 2026-07-14

### Fixed
- **Board collapse:** fixed an issue where collapsed boards had no clickable headers in full-screen mode,
  making it impossible to expand them. Headers now always show when a board is collapsed, regardless of
  full-screen setting.

## [48-Sirius B] - 2026-07-13

### Added
- **Board collapse:** users can now collapse/expand boards in the subscribed feed by clicking the
  board name. A clickable collapse/expand icon (ExpandMore when collapsed, ExpandLess when expanded)
  provides visual feedback. Collapse state is persisted across app navigation using rememberSaveable.

## [47-Proxima Centauri] - 2026-07-13

### Added
- **8kun.top provider:** added support for 8kun.top as a new LynxChan instance, available in provider
  selection during onboarding and board browsing. Marked as NSFW by default.

### Changed
- **Settings UI:** reverted all dropdown menus back to horizontally scrolling filter chips for a more
  tactile selection experience. Includes provider selection, color themes, and all choice-based settings.
- **Color themes:** simplified the palette from 24 entries to 3 core themes (Default, Tomorrow,
  Tomorrow Dark) for a more focused and maintainable design system.
- **Release naming:** continue smallest-known-star codenames for release milestones.

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

[Unreleased]: https://github.com/Defuuls/Orbin/compare/v64-Aldebaran...HEAD
[64-Aldebaran]: https://github.com/Defuuls/Orbin/compare/v63-Acrux...v64-Aldebaran
[63-Acrux]: https://github.com/Defuuls/Orbin/compare/v62-Hadar...v63-Acrux
[62-Hadar]: https://github.com/Defuuls/Orbin/compare/v61-Achernar...v62-Hadar
[52-Wolf 359]: https://github.com/Defuuls/Orbin/compare/v51-Rigel...v52-Wolf-359
[48-Sirius B]: https://github.com/Defuuls/Orbin/compare/v47-Proxima-Centauri...v48-Sirius-B
[47-Proxima Centauri]: https://github.com/Defuuls/Orbin/compare/v46-Epsilon-Eridani...v47-Proxima-Centauri
[46-Epsilon Eridani]: https://github.com/Defuuls/Orbin/compare/v38-Ross-128...v46-Epsilon-Eridani
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
