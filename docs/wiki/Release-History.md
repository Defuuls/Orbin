# Release History

Orbin ships regular, signed, tag-driven releases. This page covers **v30 through v34** in
detail; the full record lives in
[CHANGELOG.md](https://github.com/Defuuls/Orbin/blob/main/CHANGELOG.md) and on the
[Releases page](https://github.com/Defuuls/Orbin/releases).

Since v30, release milestones are codenamed after the **smallest known stars**, replacing the
bear-family codenames used for v26–v29.

## v34 — in development

Not yet released; landing on `main` after v33.

- **Feed search:** a search bar at the top of the subscribed feed filters your subscribed
  threads as you type, with a clear button and an inline "no subscribed threads match your
  search" empty state.
- **Bottom navigation:** the dedicated Search tab is removed; the bottom bar is now
  **Feed** and **Gallery**.
- **Internal updater setting:** a new toggle under Settings → Network & privacy — "Check for
  Orbin updates inside the app" — on by default.

## v33 — CM Draconis A (2026-07-09)

*Current release. `versionCode` 55.*

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
