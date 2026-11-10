# Orbin shared reader design

Implementation of the [multi-site reader design brief](https://chatgpt.com/share/6af2955b-4bdc-83e9-be75-820a46e1281d), including the revised 4chan and BBWChan scope.

Android and iOS use the same Compose Multiplatform `ui-next` screens. Feature
ViewModels and iOS presenters supply formatted state, production media slots,
and callbacks. The shared UI never reads repositories directly.

## Navigation and source identity

The primary destinations are **Home, Boards, Watched, Search, and Saved**. Settings opens
from the header menu as a separate page, with back navigation and no bottom
bar. Downloads, history, and watched threads remain reachable through settings.

The content hierarchy is **site → board → thread → post**. 4chan and BBWChan have
equal prominence in the source selector. Home can combine followed/favorite
boards from both sites or filter by source independently of the Boards selector.
Search covers favorite and followed boards across both providers.
Every combined-feed item and local saved/history record displays its site and
board. Keys and click lookups include provider, board, and thread: matching board
codes or post numbers across sites must never collide. Board directories come
from providers rather than a fixed list of example boards.

## Visual language

The default theme is monochrome, with white light surfaces, dark text, quiet gray
metadata, and subtle section separators. Dark and true-black modes and existing
optional color themes remain available. Use semantic `next` colors and `NextType`,
`NextSpace`, and `NextRadius`; do not introduce a separate screen palette.

Use compact 20–22sp screen titles, 16sp body text, 16dp side margins, and an 8dp
spacing rhythm. Flat rows replace bordered cards and hardware decoration. Post
media can extend to the screen edges; its metadata and text retain their gutters.
Text-only posts do not reserve an empty media frame. Production slots preserve
image aspect ratios, spoilers, multiple attachments, links, and existing players.
Still images do not acquire video-only mute or looping controls.

Anonymous post headers show the actual author and post number, with a small OP
badge on the original post. Quote links use accessible green in light and dark
modes. Watch, files, download, share, save, and thread navigation actions continue
to use their real host callbacks.

## Settings and playback

“Settings and activity” is a searchable directory with dedicated subpages and selection lists for
browsing, appearance, playback, privacy, storage, and app information. Search finds
all supplied controls by label, group, hint, value, and choices. Unknown controls
remain accessible through More settings. Security, DNS, backup, and update
controls retain their existing behavior and platform availability.

Playback preferences resolve in order: app defaults → site override → board
override. A board scope always contains its provider. Turning inheritance off
copies the current effective values; resetting removes that scope's override so
future parent changes flow through. Explicit board overrides survive a site reset.
Autoplay, starting muted, looping, and neighbor preloading affect real viewer
consumers, and settings are included in backups. Android feed previews remain
static; iOS inline previews respect effective playback preferences.

## Accessibility and verification

Controls have at least 48dp touch targets. The five tab icons expose labels and
selected semantics while retaining the reference’s icon-only layout at enlarged text sizes. Headers and content clear
safe areas, and measured tab height determines scrolling clearance. Accommodate
2× text, narrow windows, landscape, tablets, and foldables. Toggle rows own one
checked action; their visual switch stays passive when the row owns the toggle.

The [current iOS reference audit](instagram-reference.md) records the pinned reference, layout targets, and the limits of pixel-level verification.

Tests cover source identity collisions, source filtering, favorite/open actions,
settings navigation and search, playback inheritance/reset/persistence, and tab
semantics. Screenshot fixtures are deterministic component examples; regenerate
and inspect affected baselines after a visual change, then verify them:

```bash
./gradlew :app:assembleDebug :ui-next:testAndroidHostTest
./gradlew recordRoborazziDebug recordRoborazziAndroidHostTest
./gradlew verifyRoborazziDebug verifyRoborazziAndroidHostTest
bash scripts/check.sh
```

Native iOS execution and simulator tests require macOS and Xcode. Screenshots do
not substitute for device checks of video, scrolling, large text, and download
flows.
