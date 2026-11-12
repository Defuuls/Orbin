# iOS reader device checklist

**Status: pending for the Clover layout, fonts, Settings, subscriptions and playback correction. No physical iPhone or iPad checks have been recorded for this change.**

Use the [normative reader design](design-system/README.md) as the expected behavior. Simulator,
host and screenshot tests complement this pass; they do not mark these manual checks complete.
Keep an item unchecked until its expected result has been observed on the recorded build. Record
a failure or unavailable prerequisite beside the item, together with evidence and any issue link.

## Reference and validation scope

Clover 3.0.2 is the sole layout/control target. Orbin retains its name/icon and the user's chosen green branding with neutral Light/Dark/Black surfaces; the reference's fresh Yotsuba red/peach colors are not the expected Orbin palette. Use the [reference record](design-system/README.md#source-record), [installed captures](https://github.com/Defuuls/Orbin/actions/runs/43237685017) and [packaged font hashes/licenses](../ui-next/src/commonMain/composeResources/files/licenses/roboto_provenance.json).

Version 199/200 screenshots and green checks are historical. Record the corrective PR/commit and release/TestFlight receipt for this pass. Current-source automated checks and app screenshots are still being completed; no checklist item or distribution result is completed by this document.

## Record the pass

| Field | iPhone | iPad |
| --- | --- | --- |
| Device model | Pending | Pending |
| iOS / iPadOS version | Pending | Pending |
| App version, build and commit | Pending | Pending |
| Tester and date | Pending | Pending |
| Installation source | Pending | Pending |
| Display size / window sizes tested | Pending | Pending |
| Dynamic Type sizes and resolved scales | Pending | Pending |
| Languages and layout directions | Pending | Pending |
| Evidence / issues | Pending | Pending |

Use non-sensitive test data. Have a populated catalog, a thread with replies and several files,
a spoiler image, a thread without an image, watched unread replies and a saved thread available.
Prepare an empty catalog or a test dataset, an unavailable thread, a current-format backup and
an older backup containing a color theme. If a case cannot be produced, record it as pending.

## First run and navigation

- [ ] On a clean install, follow at least one board and explicitly choose Start browsing. Setup
  cannot be completed without a persisted subscription; cold relaunch returns to the initialized reader.
- [ ] Home, Watched, Boards, Search and Saved have their screen title and Menu in the app bar.
  The navigation drawer lists those entries in that order, then Settings below a divider.
- [ ] Menu opens the drawer. Its width is the window width less 56dp, capped at 336dp; the
  selected entry is visible and choosing an entry closes the drawer and changes screen.
- [ ] The iOS leading-edge swipe does not open the drawer. On a detail screen it goes back;
  Back with the drawer open dismisses the drawer before changing the underlying screen.
- [ ] Thread, Settings, Downloads and History show Back instead of Menu. Return to each calling
  screen and check its search query, selected source and scroll position are preserved.
- [ ] Open Settings from the drawer and Downloads from an available host action. Return using
  both the Back button and the system gesture without a duplicate destination appearing.
- [ ] Home uses reader List rows by default, with the fixed Home app bar, provider thumbnails and
  the normal Light, Dark or AMOLED screen colors. No full-screen pager, floating action rail or
  inline autoplay remains. Verify this after setup, cold relaunch and updates from versions 199 and 200.
- [ ] Source controls remain below the app bar and announce selection; Home has no social feed tabs.
  Switch sources and List/Grid after scrolling; each source returns to its own thread and offset.
- [ ] Change Catalog view between List and Grid, return to Home and cold relaunch. Home follows
  the remembered choice without losing its current reading position.
- [ ] A row opens its thread; the separate media action opens the existing viewer explicitly.
  Long-press a row/card or its preview to open Watch, Save, Share, board and thread videos/comments
  actions without a per-item three-dot box. A normal tap retains its thread/media action; the videos/comments setting retains its effect. Spoilers reveal before opening media.

- [ ] Home's Reload action and pull refresh retain the selected source and return to the
  same thread when loaded content updates. Retry remains reachable after a refresh failure.
- [ ] Saved presents stored thread titles, source/board, saved time and available unread counts
  as reader rows; selecting identical board/thread numbers on different sites opens the correct source.
- [ ] In the media viewer, insertion or reordering of live thread files keeps the selected
  attachment where it still exists. Save and Close report that file, and returning releases playback.

## iPhone, iPad and window changes

- [ ] On iPhone in portrait and landscape, a window whose shorter side is below 600 points stays
  single-pane and uses 72dp thumbnails; a long landscape width alone does not enable two panes.
- [ ] On iPad, test portrait and landscape, then supported Split View and Stage Manager sizes.
  Two panes appear only when width is at least 800 points and both sides reach 600 points.
- [ ] At 800, 960 and 1280 points wide, where those sizes are available, the catalog / divider /
  thread widths follow 332 / 1 / 467, 399 / 1 / 560 and 420 / 1 / 859dp. Record unavailable sizes.
- [ ] Wide catalog/posts use 100dp leading thumbnails, 10dp text insets, regular 16sp body and
  12sp details. Height follows content. Each pane has its own app bar and one status inset.
- [ ] Scroll a catalog, open a thread and select another thread. The catalog remains in place
  without a reload, and its selected row changes and remains visible.
- [ ] Back in the thread pane closes it and centers "Select a thread" in the detail pane.
  Back with no thread open leaves the board.
- [ ] Scroll a thread, resize across the two-pane boundary, rotate and return through the media
  viewer. Catalog and thread reading positions remain associated with the same content.
- [ ] Switch List / Grid through both Settings and catalog overflow. The choice persists across
  boards and cold relaunch; columns follow pane width / 120dp with a minimum of one. Check
  2dp outer margins, 4dp gaps, 9:18 cards, 16:13 images and 2dp corners/elevation.

## Text size, direction and VoiceOver

The current iOS mapping uses Large = 1.0×, Extra Extra Extra Large = 1.35× and Accessibility
Extra Large = 2.35×. The latter two cross the 1.3× and 2.0× layout thresholds; iOS does not expose
those exact scales as Dynamic Type choices. Exact threshold boundaries belong in automated
layout coverage, while this pass checks the real system settings and their updates.

- [ ] Use system Dynamic Type at default size, a size resolving to at least 1.3× and a size
  resolving to at least 2.0×. Record the actual sizes and resolved scales for each device.
- [ ] Plain totals stay inside the text column at 1.3×; at 2.0× the thumbnail stacks above it.
  Long titles, large counts, app bars, drawer entries and state actions remain readable and
  operable without shrinking or clipping; content can scroll when needed.
- [ ] Use a right-to-left system language and relaunch. Catalog cells mirror with the thumbnail
  at the right with mirrored text insets/counts; drawer alignment, app-bar navigation and system back work.
- [ ] With VoiceOver, the drawer's current entry and the open thread's catalog row announce
  selection. App-bar icons announce meaningful action labels and every control remains tappable.
- [ ] A catalog row announces title, replies/files, unread state and date/source as one item.
  A post announces author, number, date and body; references remain separate actionable links.
- [ ] Follow a reference into a collapsed group. It expands before the jump, focus reaches the
  intended content, and reply nesting remains readable with large text and right-to-left layout.

- [ ] Verify bundled Roboto rather than a platform-default or old display face: phone body/subjects
  14sp regular/details 10sp; wide body 16sp/details 12sp. Medium toolbar labels stay distinct.
  Long counts wrap without ellipsis or font shrinking; subject/title colors remain legible.

## Subscriptions and Home

- [ ] Favorite a board without subscribing. It remains unselected in board setup and absent from
  Home. Subscribe to a different board and confirm only that explicit subscription enters Home.
- [ ] Switch sites where board IDs overlap. Subscription markers stay associated with the
  correct site during loading, and toggling one site does not change the other site's board.
- [ ] Unfollow a still-favorited board: its favorite survives, but it leaves Home and no longer
  satisfies setup. Subscribing to another board does not create a favorite.
- [ ] Home has no Following or For You controls, including after an update and cold relaunch.

## Settings continuity

- [ ] Compare Clover-style categories, compact title/summary rows, trailing values/switches
  and choice dialogs with the reference while retaining Orbin's real labels and options.
- [ ] Change Theme, AMOLED and List/Grid; cancel/confirm choices. Stored values, selection,
  Back gestures, search and scroll stay coherent after cold relaunch.
- [ ] Toggle the thread-comments preference. The row action opens thread videos by default
  and comments only when enabled, without changing subscriptions or sources.

## Themes and media

- [ ] Theme offers only System, Light and Dark; AMOLED black is a separate toggle. Defaults are
  System, AMOLED off and List. No color-theme or catalog-column picker is present.
- [ ] In System mode, change system appearance and return to the app. Check catalog, thread,
  drawer, Settings, state screens, sheets and dialogs in Light, Dark and AMOLED.
- [ ] AMOLED grounds are pure black while thumbnail placeholders and selected rows remain
  distinct. Text, quoted lines, author names, links and error titles stay legible in each theme.
- [ ] A post without media has no thumbnail or placeholder. A spoiler cover reveals its image
  on the first tap without opening the viewer; the next tap can open it.
- [ ] A multi-file post shows its first-file thumbnail and count badge. Open it, page through
  its files, zoom an image and return to the same reading position.
- [ ] Open consecutive videos and swipe forward and back. Supported video pages show a poster
  or loading state while preparing, without flashing the browser fallback.
- [ ] Check an image, GIF, supported native video and WebM on a compatible OS. Playback, mute,
  seek and pause-on-leave behave as documented; an unsupported format offers its browser path.
- [ ] Thread toolbar offers Files/Posts first and Watch second; Reload remains in overflow.
  Files cells use 1dp container padding, 1dp margins, square corners and a 32dp details strip.
  Open a tile, download files and use Save page when available.
  Confirm the iPad share sheet is anchored correctly and return without losing pane state.

- [ ] Viewer uses verified Clover toolbar/overflow and playback-control structure with Orbin's
  real save/share/download and mute/seek actions. Hide/show chrome, rotate and return;
  only the selected attachment plays and identity/reading position stay stable.

## Loading, refresh and failures

- [ ] With an uncached catalog and thread on a slow connection, first load shows cell-sized
  skeletons and the 4dp progress bar under the app bar; navigation remains available.
- [ ] Reload populated content. The existing rows/posts remain visible with progress only;
  arriving thumbnails do not change the reserved cell height.
- [ ] Go offline before first load. A centered "Network error" title, helper text and Retry
  appear below the app bar; reconnect and Retry reaches content.
- [ ] Check an unavailable thread and a server-failure fixture. Titles name "Not found" and
  "Server error" respectively; Retry works without resetting unrelated catalog content.
- [ ] Reload populated content while offline. Content stays visible and a bottom message offers
  Retry; reconnecting and retrying updates it in place without jumping the reading position.
- [ ] Open an empty catalog or filtered dataset. A centered title, helper and Reload action
  appear, and the action remains usable with large text and VoiceOver.

## Saved data and backups

- [ ] Save and watch a thread, navigate to Saved and Watched, and cold relaunch. Membership,
  watched unread state and reading history remain attached to the correct site and board.
- [ ] From Files or the viewer, save media and check Downloads progress, retry and completion;
  verify Photos / Files permissions and cancellation do not break return navigation.
- [ ] Export a current-format backup, alter a harmless setting and restore it. Verify Catalog
  view, Theme and AMOLED restore, supported data merges and the current app-lock policy remains.
- [ ] Import an older backup containing a color theme. It is refused whole with "This backup
  was made by an older version of Orbin and cannot be restored." Confirm settings, follows,
  bookmarks and saved searches remain unchanged.
- [ ] Upgrade a test installation with stored legacy color-theme and catalog-column choices.
  Those values are ignored and the current palette and List / Grid behavior remain available.

## Outcome

| Result | Record |
| --- | --- |
| iPhone checks completed | Pending |
| iPad checks completed | Pending |
| Failures and untested cases | Pending |
| Evidence reviewed by / date | Pending |

Keep unresolved checks explicit when reporting readiness. A checklist entry is evidence only
after the device, build, observed result and supporting record are supplied.
