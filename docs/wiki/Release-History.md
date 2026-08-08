# Release History

Orbin ships regular, signed, tag-driven releases. This page covers **v49 through v74** in
detail, summarises v35–v48, and keeps the **v30–v34** detail further down; the full record
lives in
[CHANGELOG.md](https://github.com/Defuuls/Orbin/blob/main/CHANGELOG.md) and on the
[Releases page](https://github.com/Defuuls/Orbin/releases).

Releases v30–v33 were codenamed after the **smallest known stars**, replacing the bear-family
codenames used for v26–v29; v34 is "Dippin".

The star theme has held since, but the selection shifted: v37–v48 stayed with nearby or dim
stars (Wolf 359, Ross 128, Proxima Centauri, Sirius B), while **from v49 onward the codenames
are prominent naked-eye stars** — Altair, Fomalhaut, Rigel, Sirius, Canopus, Polaris, Vega,
Arcturus, Capella, Betelgeuse, Procyon, Achernar, Hadar, Acrux, Aldebaran, Antares, Spica, Pollux,
Deneb, Regulus, Bellatrix, Elnath, Alnair, Peacock, Avior.

## v74 — Avior (2026-08-08)

*Current release.*

- **New setting: autoplay videos in the subscribed feed** (Settings → Media & Playback). Each
  thread's first attachment plays inline and muted as its row scrolls into view in List or Grid
  feed layout, and stops when it scrolls away — no need to open the thread. Off by default.

## v73 — Peacock (2026-08-08)

- **The subscribed feed (the app's home screen) gets the same List/Grid/image-only layout
  toggle** already shipped for the single-board catalog in v72. Board grouping, per-board
  collapse/expand, and per-board thread-count overrides all keep working in every mode. This is
  the screen most people actually mean by "board view" day to day — the v72 change alone missed
  it.

## v72 — Alnair (2026-08-08)

- **Board catalog gets a third layout: image-only, no text.** It joins List and Grid, mirroring
  the thread viewer's own text/image split — cycle through all three from the same top-bar icon.
  It has an adjustable thumbnail size (Medium/Large/Fill) with its own size-cycle icon, and only
  pulls full-resolution images at Large/Fill to avoid the same bandwidth-contention regression
  fixed in the thread grid.

## v71 — Elnath (2026-08-08)

- **Reverted the v70 thumbnail change — it made grid-view blur worse, not better.** Fetching the
  full-resolution source for every tile in the thread's thumbnail grid (which can hold hundreds of
  attachments) contends for bandwidth and decode time while scrolling, leaving more tiles stuck on
  the low-res placeholder than the small thumbnail ever was on its own. Medium is back to the
  cheap provider thumbnail; only Large and Fill pull full resolution, as before v70.
- **The feed top bar's icon box now shows your selected app icon**, not a plain colored square.

## v70 — Bellatrix (2026-08-08)

- **Fixed blurry thumbnails in the thread's grid view.** Grid mode only fetched the
  full-resolution source image at the Large and Fill sizes; the default Medium size (96dp) used
  the provider's ~250px thumbnail, which upscales visibly on most modern phones. It now always
  requests full resolution, matching the reply-list view, which already did this.

## v69 — Regulus (2026-08-08)

- **Board catalog and thread viewer are less cluttered.** The thread bar's five unlabeled icons
  are now three: Download all media and Export links move into a "More" (⋮) menu. Catalog cards no
  longer render reply count, media count, poster count, and closed/archived status as bordered
  chips that each duplicated the card's own tap-to-open action — they're now plain tonal labels.
  Preview-reply rows lost the same redundant per-row click target. A collapsed post's header shows
  a real expand/collapse icon instead of a "[+]" text suffix, and long usernames ellipsize instead
  of crowding the post number and timestamp off the row.

## v68 — Deneb (2026-08-07)

- **Settings is a hub, not one long scroll.** The single Settings screen held eight sections and
  35+ options in one column. It now lists six categories — Content & Feed, Notifications,
  Appearance, Media & Playback, Privacy & Network, Storage & Backup — each opening its own screen,
  with Advanced nested one level under Privacy & Network since it's the one page most people never
  open. Every option kept its exact label, default, and behavior; only where it lives changed.

## v67 — Pollux (2026-08-07)

- **Removed 8kun support.** The 8kun.top LynxChan instance is no longer bundled; it drops out of
  the Site provider picker in Settings automatically once its Hilt registration is gone, since
  nothing else in the app names a provider directly. BBW Chan (LynxChan) and 4chan (Vichan) are
  unaffected.

## v66 — Spica (2026-08-07)

Bug fixes and a design-system accessibility pass.

- **Typed text in the muted/hidden tags field appeared reversed.** The field was bound directly
  to settings state that updates asynchronously, so the cursor reset to the start after every
  keystroke and each new character landed before the last. Typing is now backed by local field
  state that syncs to storage in the background instead of driving the field directly.
- **14 lint errors in the media module,** covering unstable Media3 API opt-ins and a Compose
  modifier-parameter convention violation.
- A disabled row (e.g. Settings' always-on "HTTPS only" display) was announced to screen readers
  as a disabled button. Non-interactive list rows no longer attach a clickable modifier at all.
- A card-based list item attached its own click handling on top of the click handling its
  container already provided, doubling the touch target and gesture handling.
- **Added:** a contrast regression test for the 20 ported imageboard skins, checking body text
  against WCAG AA (4.5:1).

## v65 — Antares (2026-08-07)

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
- **Haptic feedback** on the pull-to-refresh threshold, spoiler reveals and post collapsing.
- **Fixed:** a thread could not actually be refreshed (the 30-minute cache had no bypass);
  backlink chips were below the minimum touch target; the thread media grid was pinned to three
  columns instead of reflowing; the settings chip row lacked a stable item key.

## v64 — Aldebaran (2026-08-06)

- **Baseline profiles.** A `:benchmark` module records the classes used on the startup and feed
  path so ART compiles them ahead of time instead of interpreting them on first launch.
- **Instrumentation tests actually run in CI, and actually test something,** after a rewrite —
  the previous versions asserted against an empty Compose tree and had never caught a real
  regression. A new workflow boots an emulator and runs them on every push and PR.
- **Fixed:** a protobuf security pin that broke every instrumentation test with
  `NoClassDefFoundError`; a Netty security pin referencing a version that did not exist;
  screenshot tests that recorded and verified nothing while reporting success; a missing
  release-signing fallback; CI not actually enforcing warnings-as-errors despite documentation
  claiming it did.

## v63 — Acrux (2026-08-05)

- **Encrypted DNS is no longer a toggle.** It is now always on; the setting is *which* resolver
  answers your lookups. A visible notice appears when a network blocks the chosen resolver and
  Orbin falls back to the system resolver, rather than failing silently or failing closed.
- **"Refresh feed on return" is a timeframe, not a switch:** Always, 1, 5, 15 or 30 minutes, or
  Never.
- **Open threads as** (Settings → Appearance): **Page** (default) or **Slide over**.
- Settings now slides in over the screen behind it instead of pushing it aside.

## v62 — Hadar (2026-08-05)

- **Check for updates,** in Settings → Network & privacy. Fetches only release metadata from
  GitHub and links to the release page — Orbin never downloads or installs an APK itself.
- **Fixed:** the "Internal updater" toggle, present since v34, finally gates something.

## v61 — Achernar (2026-08-05)

- **Saved searches included in backups**, alongside settings, boards and bookmarks.
- **Fixed:** the database was configured to recreate itself rather than migrate on a schema
  change — a leftover from before the first release that would have silently dropped all local
  data on the next migration. Schema changes now migrate properly.

## v60 — Procyon (2026-08-05)

- **Backup and restore:** Settings → Storage can export settings, subscribed/favourite boards,
  and bookmarks to a file, and restore them afterward. Importing **adds** rather than replaces,
  so restoring cannot wipe an existing setup.
- **Image cache limit** (128–1024 MB) and **advanced network settings** (custom user agent,
  timeouts, certificate-revocation checking), under Settings → Storage / Advanced.
- **Fixed:** backups silently dropped the image cache limit and network settings on import.

## v59 — Betelgeuse (2026-08-04)

A maintenance release: toolchain bumps (AGP 9.3.0 → 9.3.1, Kotlin 2.4.0 → 2.4.10) and a wiki
pass bringing the docs current after a long drift.

## v58 — Capella (2026-08-01)

Hotfix for a launcher bug introduced in v57.

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
