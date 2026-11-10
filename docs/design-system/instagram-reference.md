# Current iOS reference audit

Reference checked on 2026-11-10: Instagram **454.1**, released 2026-11-04,
from the [official iOS App Store listing](https://apps.apple.com/us/app/instagram/id389801252)
and [Apple lookup metadata](https://itunes.apple.com/lookup?id=389801252&country=us).

The [official Home reference](https://is1-ssl.mzstatic.com/image/thumb/PurpleSource221/v4/76/44/b3/7644b346-96a2-fdbe-e9e6-84fd4ccf3f30/5_iOS_5.5.jpg/1176x2088bb.jpg)
shows a centered heading, circular shortcut rail, dense source header,
edge-to-edge media, outlined actions, and a floating five-icon capsule.
The [official comments reference](https://is1-ssl.mzstatic.com/image/thumb/PurpleSource211/v4/46/04/ec/4604ec65-497b-85a4-fe51-c96719f9aa86/2_iOS_5.5.jpg/1176x2088bb.jpg)
shows small avatars beside compact author/time and comment columns.

## Applied layout contracts

| Area | Orbin adaptation |
| --- | --- |
| Primary navigation | Five icon-only destinations in the same positional rhythm: Home, Boards, Watched, Search, Saved. Each opens a real destination. |
| Navigation container | Floating 56dp capsule, nominal 24dp side margins, 8dp bottom spacing, gray selected well, safe-area clearance, and at least 48dp touch targets. Narrow windows retain usable targets. |
| Home header | Centered source title/dropdown, compact side actions, and source selection in a dedicated sheet. Routine thread-count and section-title rows removed. |
| Shortcut rail | 84dp outer circles with image inset and compact board/source captions. Unseen content uses gradient rings; read content uses neutral rings. |
| Feed | Compact source/avatar header, post overflow menu, full-width media, 24dp outlined actions, real reply count, and short caption previews. Text-only posts retain no blank media frame. |
| Icon family | Original geometric vectors with consistent 1.8dp rounded strokes and filled selected variants, replacing the mixed Material icon weights. |
| Search | Borderless gray field with a compact 36dp visual height, a 48dp target, and actual query/clear/submit behavior. Text scales without fixed-height clipping. |
| Settings | Compact header, root-only search, flat directory rows, trailing values and chevrons, quieter section labels, and dedicated selection lists with checkmarks. Long scope lists are searchable. |
| Replies | Small left avatar, aligned author/time and body column, compact metadata, and natural attachment sizing. |

Dimensions are implementation targets derived from the reference's proportions,
not raw device-pixel measurements. The published references are marketing
composites with differing viewport scales. They do not show every settings
subpage, and cannot certify a pixel-for-pixel match on every iPhone, iOS version,
or Instagram account rollout. Native device screenshots at a fixed viewport
are required for that final comparison. The shared UI's iOS-mode host renders
verify layout and behavior; they are not iPhone simulator captures.

## Review artifacts

- [Home](../../ui-next/src/androidHostTest/screenshots/social_ios_home.png)
- [Boards](../../ui-next/src/androidHostTest/screenshots/social_ios_boards.png)
- [Watched](../../ui-next/src/androidHostTest/screenshots/social_ios_watched.png)
- [Search](../../ui-next/src/androidHostTest/screenshots/social_ios_search.png)
- [Settings](../../ui-next/src/androidHostTest/screenshots/social_ios_settings.png)
- [Playback](../../ui-next/src/androidHostTest/screenshots/social_ios_playback.png)
- [Thread](../../ui-next/src/androidHostTest/screenshots/social_ios_thread.png)

The PNGs render actual shared composables with deterministic local fixtures.
Interaction checks cover five-tab dispatch/selection, source selection, overflow
actions, settings selection/back behavior, and large-text layout. Roborazzi
records and verifies the complete shared screenshot suite.
