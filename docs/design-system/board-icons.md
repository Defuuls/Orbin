# Board icon coverage

Orbin draws a local topic avatar for every board on 4chan and BBWChan. The icon
registry covers the public board directories observed on **10 November 2026**:
**77 4chan boards and 14 BBWChan boards**, 91 source-qualified identities in total.

The metadata-only references are [4chan boards.json](https://a.4cdn.org/boards.json)
and [BBWChan boards.js](https://bbw-chan.link/boards.js?json=1). The test snapshot
stores only provider, board code, and title; no posts, attachments, or user media
are fetched or shipped for this feature.

Each avatar combines an original topic pictogram, a stable color, a provider mark,
and a small geometric identity seal. Boards sharing a topic therefore keep
different avatars. Matching board codes on the two supported sites have different
provider marks and fingerprints. The line artwork is neutral, including for adult
boards; none of the icons contain explicit imagery or third-party artwork.

A future board always receives an icon without requiring an app update. Its full
provider/board identity determines a stable seal and color, and available title
metadata can suggest a topic pictogram. The adjacent board and source text remain
the accessible identity; decorative icons do not repeat announcements.

Icons appear in the board directory, feed source avatars, and shortcut circles
when no real production thumbnail is available. Board discovery, filtering, and
content policy are unchanged: this icon registry never supplies a visible board
list or makes a filtered board discoverable.

Coverage and alias/collision/fallback tests live in `BoardSymbolsTest`. The complete
[light atlas](../../ui-next/src/androidHostTest/screenshots/board_icons_all_light.png)
and [dark atlas](../../ui-next/src/androidHostTest/screenshots/board_icons_all_dark.png)
are captured by `BoardIconScreenshotTest` for visual review.
