# Release History

Orbin ships regular, signed, tag-driven releases. This page covers **v49 through v58** in
detail, summarises v35–v48, and keeps the **v30–v34** detail further down; the full record
lives in
[CHANGELOG.md](https://github.com/Defuuls/Orbin/blob/main/CHANGELOG.md) and on the
[Releases page](https://github.com/Defuuls/Orbin/releases).

Releases v30–v33 were codenamed after the **smallest known stars**, replacing the bear-family
codenames used for v26–v29; v34 is "Dippin".

The star theme has held since, but the selection shifted: v37–v48 stayed with nearby or dim
stars (Wolf 359, Ross 128, Proxima Centauri, Sirius B), while **from v49 onward the codenames
are prominent naked-eye stars** — Altair, Fomalhaut, Rigel, Sirius, Canopus, Polaris, Vega,
Arcturus, Capella.

## v58 — Capella (2026-08-01)

*Current release.* Hotfix for a launcher bug introduced in v57.

- **App would not open from the launcher:** tapping the icon opened App Info instead of the
  app. Switching icons could disable the alias the launcher had pinned, and could leave the
  package with no enabled launcher entry at all. There is now exactly one alias per icon
  variant, the selected one is enabled before the others are disabled, and no variant can go
  unmanaged.
- **Duplicate launcher icon:** the app could appear twice in the launcher after selecting an
  icon.

> Installs already left without a launcher entry cannot be repaired by updating, because
> Android persists per-component enable-state across updates. Those need a reinstall.

## v57 — Arcturus (2026-08-01)

- **Selectable app icons:** five launcher icons — Orbital Orb, Nested Rings, Abstract Flow,
  Minimalist Essence and Dual Gradient — chosen from Settings and applied without a restart.
- **Crisp media thumbnails:** thumbnails rendered the provider's ~250px preview even in layouts
  that display it far larger. Still images now load their full-resolution source in the FILL
  and LARGE grid sizes and in the full-width post layouts, while compact and medium sizes keep
  the smaller preview.
- **Netty upgraded to 4.1.139.Final,** resolving 9 CVEs covering ByteBuf leaks, infinite loops,
  SPDY vulnerabilities, CORS bypasses and injection attacks.

## v56 — Vega (2026-07-31)

- **Material Design 3 component library:** a shared set of modernized components, with the
  settings, board gallery and subscriptions screens and the app navigation rebuilt on top of it.
- **Startup and scrolling performance:** WorkManager initialization moved off the main thread,
  thread caching and image preloading added, and thread-view recomposition reduced.
- **Network efficiency:** HTTP disk caching added to the shared OkHttp client.

## v55 — Polaris (2026-07-29)

- **Media scroll settings:** the media carousel can be enabled per view — thread view on by
  default, board view off — under Settings → Media.
- **Board view media carousel:** feed thread preview cards scroll horizontally through
  attachments, with swipe gestures and a page counter badge.

## v54 — Canopus (2026-07-29)

- **Video indicators:** "VID" text labels replaced with Material 3 PlayArrow icons in the
  subscribed feed and gallery views.
- **Media carousel:** posts with multiple attachments in thread view scroll horizontally, with
  a page counter badge showing position (e.g. "2/5").

## v53 — Sirius (2026-07-28)

- **Board view media:** the play icon and horizontal media scrolling landed for the board view,
  ahead of the feed and gallery treatment that followed in v54.

## v52 — Wolf 359 (2026-07-25)

- **Relative post times:** the thread viewer shows each post's age compactly ("just now", "5m",
  "3h", "2d"), falling back to the absolute date beyond a week.
- **Post text rendering:** migrated off the deprecated Compose `ClickableText` to
  `LinkAnnotation`, so quote links and URLs are announced as links by TalkBack.
- **Thread collapse persistence:** collapsed posts stay collapsed across configuration changes.
- **Dependency hardening:** corrected the Commons IO override, which matched the wrong Maven
  group and never applied, so patched `commons-io` 2.20.0 is enforced everywhere.

## v51 — Rigel (2026-07-14)

- **Saved searches:** search queries can be saved and reused, backed by a new database table.
- **Thread watch notifications:** per-thread watch notifications with user-configurable quiet
  hours.
- **Download retry:** failed downloads can be retried.
- **Top bar:** shows the active provider and the selected app icon variant.
- **Testing:** instrumentation tests added for critical user flows.

## v50 — Fomalhaut (2026-07-14)

- **Collapse all / Expand all:** toolbar buttons to collapse or expand every subscribed board at
  once.
- **Enhanced top bar:** the subscribed feed title bar shows the app name with a colored square
  indicating the active theme's primary color.

## v49 — Altair (2026-07-14)

- **Board collapse:** fixed collapsed boards having no clickable header in full-screen mode,
  which made them impossible to expand. Headers now always show when a board is collapsed.

## v35–v48 at a glance

These releases ran quickly through the nearby-star codenames; the full detail is in
[CHANGELOG.md](https://github.com/Defuuls/Orbin/blob/main/CHANGELOG.md).

| Versions | Notable milestones |
| --- | --- |
| v46–v48 | 8kun.top added as a LynxChan instance; settings reverted to filter chips; the color palette cut from 24 entries to 3 core themes; bookmarks and board-collapse refinements. |
| v37–v45 | Post and thread dates surfaced in the catalog and thread view; fullscreen video with auto-rotate; the dormant file-host link verification feature removed. |
| v35–v36 | Continued feed and provider work following the v34 search release. |

## v34 — Dippin (2026-07-09)

- **Feed search:** a search bar at the top of the subscribed feed filters your subscribed
  threads as you type, with a clear button and an inline "no subscribed threads match your
  search" empty state.
- **Bottom navigation:** the dedicated Search tab is removed; the bottom bar is now
  **Feed** and **Gallery**.
- **Internal updater setting:** a new toggle under Settings → Network & privacy — "Check for
  Orbin updates inside the app" — on by default.

## v33 — CM Draconis A (2026-07-09)

- **Gallery:** the board picker now offers only subscribed boards (honouring the NSFW-board
  visibility setting), matching the feed instead of listing every board on the provider.
- **Bookmarks:** the Bookmarks bottom-navigation tab is removed; bookmarks now live in a
  Bookmarks tab inside the Gallery view, keeping the watch toggle, unread badges, and remove
  actions.

## v32 — EQ Pegasi A (2026-07-09)

- **Full-screen feed:** the pinned board headers are dropped from the feed while the
  full-screen option is on — nothing stays fixed at the top and boards are no longer listed
  between threads, so the feed is a total full-screen view.

## v31 — Fomalhaut C (2026-07-09)

- **Full-screen feed:** the option now actually goes full screen — the status and navigation
  bars hide together with the feed chrome while scrolling, and the duplicated window-inset
  padding that left white strips at the top and bottom of the feed view is removed.

## v30 — Janus (2026-07-09)

- **Feed chrome:** iOS-style tap-to-top behavior from the top feed, board, and thread bars.
- **Settings:** a new Full-screen feed option lets the subscribed feed hide top and bottom bars
  while scrolling for more reading space.
- **Tablet feed:** an initial tablet layout with a floating dock, combined subscribed-feed
  controls, auto-hiding chrome, and old-Reddit-style thumbnail/text rows.
- **Media CDN usage:** video media is cached through Media3 and static media no longer sends
  no-cache request headers, so repeated viewing does not churn CDN requests.
- **Release naming:** smallest-known-star names replace bear-family codenames.

## Earlier releases at a glance

| Versions | Codename era | Notable milestones |
| --- | --- | --- |
| v26–v29 | Bear families | Incremental releases leading up to the v30 feed work. |
| v23–v25.2.1 | Mythical cities (with a few detours) | v23.0 hardened the app lock, comment parser, and HTTPS policy; v23.1 fixed 4chan boards failing to load on numeric media IDs; v23.8 fixed biometric-unlock hangs; v25.2.1 fixed thread-link exports to respect the saved-media folder. |
| v24.0 | — | **Encryption at rest:** SQLCipher database + encrypted DataStore behind a hardware-backed Keystore key. Because the on-disk format changed, this version performed a **one-time reset** of history, bookmarks, downloads, and settings (including favorites and subscriptions). |
| v12–v22 | Rare fish | v21 added a manual CodeQL workflow; v22 resolved Dependabot dependency alerts. |
| v4–v11 | Desserts & gelato flavors | v6 laid down the multi-module architecture, provider SPI, Room persistence, and CI; v7 added the subscribed feed, search filters, and download/privacy hardening; v8 added feed thread limits, the saved-media folder picker, and DNS-over-HTTPS providers; v11 improved video playback reliability. |

## How releases are cut

Releases are tag-driven: pushing a `v*` tag builds a signed APK, generates release notes from
the commit log, computes SHA-256 checksums, and publishes a GitHub Release with the APK and R8
mapping file attached. A `New Version` workflow can prepare the version-bump PR from inputs
(version name, code, codename). See the [[Developer Guide|Developer-Guide]] for the full
workflow.
