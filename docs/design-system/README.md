# Orbin reader design

This is the normative design for Orbin's Home, board catalog and thread reader. **Android and iOS share this implementation.** Implementation links, automated checks, screenshot and profile status, and the device checklist are tracked in [Implementation and verification](IMPLEMENTATION.md). That record distinguishes code being present from a passing check or a shipped release.

The Orbin Coral design specifies the current chrome, rounded cards, pill search, four-destination primary navigation and card-stack identity. Clover 3.0.2 remains the historical reference for compact post metrics, typography and reader interactions. Coral is an intentional adaptation with Orbin's subscriptions, sources, saved/watch tools and media playback. The reference diagrams and compact-grid arithmetic below record that provenance; they are not screenshots of the current Coral card layouts. [Source record](#source-record) identifies the exact APK and retained evidence.

A visual companion, [schematic.html](schematic.html), shows the same diagrams and tokens on one page. If the page and this document ever differ, this document is correct.

## Scope

Covered here:

- Home in List, Grid and Card modes, including source selection;
- the board catalog in List, Grid and Card modes;
- the thread reader;
- the two-pane layout that shows both;
- Boards, Search, Downloads, stored-thread collections, Settings and its sub-pages;
- first-run setup, app lock, safe mode, dialogs, release notes and archive pages;
- the media wall and explicit media viewer;
- loading, empty, error and retained-content refresh states;
- light, dark and AMOLED themes.

Orbin is read-only. This design has no compose button, no reply box and no per-post reply control, and none may be added under it.

Home and board catalogs share the device-wide List/Grid/Card preference. A missing preference defaults to Grid; existing List and Grid choices are retained. Card mode shows one full-width Coral card per row, with 16dp pane-edge gutters, 8dp between cards, 12dp content insets and a preview height of three quarters of the card width. It remains one column in a tablet catalog pane. The Home layout control cycles Grid → List → Card → Grid; overflow menus and Appearance settings offer direct selection. The preference survives relaunches and backup restoration. Coral cards, search controls and supporting screens use the same palette while thread posts retain the compact reader metrics. Saved, Watched and History show real stored records rather than synthesized profiles. [Other screens](#other-screens) describes the remaining surfaces and the dedicated playback experience.

## Tokens

The shared token implementation records the values below. Clover's verified compact geometry and typography remain reference metrics; Coral cards, chrome and contrast-safe action/link colors are intentional adaptations. Layout uses dp and type uses sp. Eight-digit colors in these tables use Android ARGB order (#AARRGGBB); SVG/CSS equivalents convert the byte order while preserving alpha. Bundled fonts and the existing single Dynamic Type scaling path remain shared by both platforms.

### Color

App-bar titles and icons use `color.onAppBar` over `color.appBar`; the status-bar ground uses `color.statusBar`.

| Token | Light | Dark | AMOLED | Use |
| --- | --- | --- | --- | --- |
| `color.appBar` | `#FFFFFF` | `#0B1021` | `#000000` | App bar ground |
| `color.statusBar` | `#FFFFFF` | `#000000` | `#000000` | Status bar ground |
| `color.onAppBar` | `#1A1A1A` | `#FFFFFF` | `#FFFFFF` | App bar labels and icons |
| `color.background` | `#FFFFFF` | `#0B1021` | `#000000` | Screen, row, post and card ground |
| `color.surfaceVariant` | `#F5F7FB` | `#141B33` | `#161616` | Secondary ground, placeholders and skeletons |
| `color.selected` | `#EEF4FF` | `#1A2547` | `#242424` | Open row in two panes |
| `color.ink` | `#DE000000` | `#FFFFFF` | `#FFFFFF` | Primary body text |
| `color.inkSecondary` | `#89000000` | `#B2FFFFFF` | `#B2FFFFFF` | Generic secondary UI text |
| `color.inkMuted` | `#5A6378` | `#9FB0CE` | `#B8B8B8` | Post numbers, dates and attachment details |
| `color.hairline` | `#1E000000` | `#1EFFFFFF` | `#1EFFFFFF` | Decorative row and post dividers |
| `color.accent` | `#4338CA` | `#00D4FF` | `#FFFFFF` | Orbin progress and actions |
| `color.onAccent` | `#FFFFFF` | `#06131F` | `#000000` | Text on an accent-filled control |
| `color.quote` | `#2E7D32` | `#34D399` | `#D4D4D4` | Quoted lines |
| `color.author` | `#1A1A1A` | `#8BE9FD` | `#E5E5E5` | Post author name |
| `color.subject` | `#1A1A1A` | `#8BE9FD` | `#FFFFFF` | Post and catalog subjects |
| `color.quoteLink` | `#0369A1` | `#38BDF8` | `#D4D4D4` | Post references and backlinks |
| `color.error` | `#B3261E` | `#F2B8B5` | `#F2B8B5` | Error titles |

The light toolbar is white with dark ink so the Midnight Electric indigo/cyan logo and accent can carry the brand. color.statusBar matches color.appBar in Light, and is black in Dark and AMOLED. Dark uses a #0B1021 midnight-navy toolbar and Midnight Electric cyan accents; AMOLED uses pure black and white monochrome (`#000000` / `#FFFFFF` with neutral `#161616` / `#242424` surfaces). Generic color.inkSecondary is distinct from color.inkMuted post metadata. Selected rows use a light indigo wash #EEF4FF in Light, #1A2547 in Dark, and neutral #242424 in AMOLED. Subjects and author names use dark ink in Light, light cyan #8BE9FD in Dark, and crisp white/silver in AMOLED. Post references use the electric cyan/sky accent in Light and Dark, and silver #D4D4D4 in AMOLED. Quoted (greentext) lines keep a contrast-safe green in Light and Dark and neutral silver in AMOLED. The supplied Clover fresh-install Yotsuba palette is recorded under Source record; it is not Orbin's selected palette.

### Contrast

The following pairs are checked after compositing alpha over the stated ground. Decorative color.hairline has no text-contrast minimum.

| Foreground | Background | Minimum ratio |
| --- | --- | --- |
| `color.onAppBar` | `color.appBar` | 4.5 |
| `color.onAppBar` | `color.statusBar` | 4.5 |
| `color.ink` | `color.background` | 4.5 |
| `color.inkSecondary` | `color.background` | 4.5 |
| `color.inkMuted` | `color.background` | 4.5 |
| `color.ink` | `color.surfaceVariant` | 4.5 |
| `color.inkMuted` | `color.surfaceVariant` | 4.5 |
| `color.ink` | `color.selected` | 4.5 |
| `color.inkMuted` | `color.selected` | 4.5 |
| `color.quote` | `color.background` | 4.5 |
| `color.quote` | `color.selected` | 4.5 |
| `color.subject` | `color.background` | 4.5 |
| `color.subject` | `color.selected` | 4.5 |
| `color.author` | `color.background` | 4.5 |
| `color.quoteLink` | `color.background` | 4.5 |
| `color.error` | `color.background` | 4.5 |
| `color.accent` | `color.background` | 3.0 |
| `color.onAccent` | `color.accent` | 4.5 |

### Type

The shared `OrbinType` API contains both chrome styles and the compact reader roles below. Chrome `OrbinType.body` retains 16sp/23sp; `OrbinType.readerBody` retains the separate 15sp/22sp helper. Pure reader metrics are resolved to the same bundled font at the rendering boundary, with no additional system text-size factor.

App bars use type.appBarTitle and, when present, type.appBarSubtitle for the board/source line. Optional thread summaries use type.meta with space.gapSmall above and below the summary.

Both platforms use bundled Roboto for the reader and shared default-family text. The exact open-source Medium, Medium Italic and Condensed Regular faces from the reference are retained; Regular/Bold/Italic variants are pinned to the actual API31 reference-runtime system Roboto because Clover delegates those faces to Android's system sans. See the packaged font provenance and Apache 2.0 notice in ui-next/src/commonMain/composeResources/files/licenses/. Natural line height means the font's metrics, with no invented fixed sp line height. Platform TextView/Compose baseline differences remain subject to native screenshot verification. The SVG diagrams embed diagram-only glyph subsets with the same original outlines and metrics; the application font assets remain complete and unmodified.

| Token | Size | Line height | Weight | Use |
| --- | --- | --- | --- | --- |
| `type.appBarTitle` | 20sp | 28sp | 500 | App bar title. |
| `type.appBarSubtitle` | 14sp | 20sp | 400 | Board line under a thread title. |
| `type.rowTitle` | 16sp | 22sp | 700 | Saved-record title; compatibility helper |
| `type.meta` | 12sp | 16sp | 400 | Shared helper and saved-record metadata |
| `type.preview` | 14sp | 20sp | 400 | Compatibility preview helper |
| `type.readerBody` | 15sp | 22sp | 400 | Compact body helper; posts use postBody |
| `type.cardTitle` | 14sp | 20sp | 700 | Loading skeleton helper; cards use postBody |
| `type.postAuthor` | 12sp | 16sp | 700 | Compatibility author helper |
| `type.postBody` | 14sp | natural | 400 | Clover's default post size; natural Roboto metrics supply its line spacing. |
| `type.postBodyWide` | 16sp | natural | 400 | The sw600dp post size from Clover's tablet resource configuration. |
| `type.postDetails` | 10sp | natural | 400 | Post number, time and attachment metadata are four sp smaller than the body. |
| `type.postDetailsWide` | 12sp | natural | 400 | Wide-window post metadata |
| `type.pill` | 14sp | 20sp | 500 | Compatibility control helper; catalog counters are plain text |
| `type.stateTitle` | 24sp | 32sp | 400 | Empty and error state title. |
| `type.stateBody` | 16sp | 24sp | 400 | State helper text and the empty detail pane message. |
| `type.label` | 14sp | 20sp | 500 | Text buttons. |

### Spacing

| Token | Value | Use |
| --- | --- | --- |
| `space.gutter` | 16dp | Left and right screen edge inset. |
| `space.rowPadY` | 12dp | Legacy shared-cell and skeleton vertical padding |
| `space.postPadY` | 12dp | Legacy shared-cell vertical padding |
| `space.postInset` | 8dp | Clover post text inset: the configured text size minus six dp. |
| `space.postInsetWide` | 10dp | Post text inset in wide windows |
| `space.albumPadding` | 1dp | Clover's album container padding and individual cell margin. |
| `space.albumCellMargin` | 1dp | Individual album cell margin |
| `space.cardPad` | 8dp | Padding around the text block of a grid card. |
| `space.gridGap` | 4dp | Between grid cards: the two adjacent 2dp card margins. |
| `space.gridOuter` | 2dp | Outside margin of catalog grid cards, matching Clover v3.0.2 on API 21+. |
| `space.gap` | 12dp | Reply nesting and generic control spacing |
| `space.gapSmall` | 8dp | Generic block and drawer padding |
| `space.line` | 4dp | Between title, meta line, and preview in a row. |

### Size

| Token | Value | Use |
| --- | --- | --- |
| `size.appBar` | 56dp | App bar height. |
| `size.appBarNavigation` | 56dp | Clover toolbar leading navigation control width. |
| `size.appBarAction` | 50dp | Clover toolbar regular action width. |
| `size.appBarOverflow` | 44dp | Clover toolbar overflow control width. |
| `size.touchTarget` | 48dp | Minimum size of anything that can be tapped. |
| `size.icon` | 24dp | App bar icon. |
| `size.thumbnail` | 72dp | Cell thumbnail width and height in a window that is not wide. |
| `size.thumbnailWide` | 100dp | Cell thumbnail width and height in a wide window. |
| `size.cellMin` | 96dp | Legacy shared-cell and loading minimum |
| `size.cellMinWide` | 124dp | Legacy shared-cell and loading minimum, wide |
| `size.gridCardMin` | 120dp | Thread Files columns are the pane width over this, rounded down; also the minimum grid image height. |
| `size.gridCardText` | 76dp | Minimum height of a grid card's text block, so cards on one line stay level. |
| `size.cardElevation` | 2dp | Catalog card elevation, matching Clover v3.0.2. |
| `size.threadStatus` | 88dp | Thread footer that reports reply and image counts. |
| `size.albumDetails` | 32dp | The album cell adds this metadata strip to its width to determine its height. |
| `size.pillHeight` | 36dp | Compatibility control height; no catalog count pill |
| `size.pillMinWidth` | 48dp | Compatibility control and skeleton width |
| `size.hairline` | 1dp | Row and post divider thickness. |
| `size.progress` | 4dp | Refresh progress bar height. |
| `size.readingMax` | 640dp | Maximum post text width in a wide pane. |
| `size.drawerMax` | 336dp | Maximum navigation drawer width. |
| `size.wideMin` | 600dp | Both window sides must reach this to count as wide. |
| `size.twoPaneMinWidth` | 800dp | Wide windows at least this wide show two panes. |
| `size.listPaneMax` | 420dp | Maximum catalog pane width in two panes. |
| `size.paneDivider` | 1dp | Divider between the two panes. |

### Radius

| Token | Value | Use |
| --- | --- | --- |
| `radius.compact` | 2dp | Retained compact reader thumbnail corners. |
| `radius.small` | 4dp | Skeleton bars and small helper corners. |
| `radius.compactCard` | 6dp | Compact grouped controls. |
| `radius.control` | 10dp | Form and inline controls. |
| `radius.thumbnail` | 12dp | Coral thumbnail tiles. |
| `radius.card` | 16dp | Coral content cards. |
| `radius.sheet` | 16dp | Dialog and sheet surfaces. |
| `radius.panel` | 20dp | Larger feature panels. |
| `radius.pill` | 20dp | Action and counter pills. |
| `radius.search` | 24dp | Pill search and filter fields. |

Coral cards use radius.card over color.background; larger feature panels use radius.panel. Thumbnail tiles use radius.thumbnail, while retained compact reader crops use radius.compact. Search fields use radius.search, form controls use radius.control, and compact grouped controls use radius.compactCard. Dialogs and bottom sheets use radius.sheet; action and counter pills use radius.pill. Skeleton bars and small helper shapes use radius.small. These semantic roles share one `OrbinRadius` namespace without changing the retained compact geometry.

### Ratios

| Token | Value | Use |
| --- | --- | --- |
| `ratio.gridImageHeight` | 0.5 | A grid card's image height as a fraction of the grid viewport's height: two lines per screen. |

Compact helpers remain available through the canonical namespace: type.rowTitle, type.meta, type.preview, type.readerBody, type.cardTitle, type.postAuthor and type.pill; space.rowPadY and space.postPadY; size.cellMin, size.cellMinWide, size.pillHeight and size.pillMinWidth; radius.compact and radius.small. Their values preserve compact reading geometry while Coral cards use radius.card and pills use radius.pill. Generic controls and loading fallbacks may still use the compact roles. The general space.gutter (16dp) aligns drawer/control edges; space.line (4dp) separates inline helper content.


## Shared cell

Thread posts and the compact List-mode content retain ReaderPostLayout's leading thumbnail and inset text column. The following measurements describe that compact reader structure. Current Coral grids use the separate card rule under [Phone catalog](#phone-catalog), and Home may wrap the compact list content in a Coral card. Cross-site metadata, unread state, subscriptions and read-only actions retain their real behavior.

![Shared cell: catalog row and post](diagrams/cell-anatomy.svg)

- The optional thumbnail starts at the leading edge with no outer gutter. It is size.thumbnail (72dp), or size.thumbnailWide (100dp) in a wide window, with radius.compact (2dp) corners. Provider thumbnail crop preferences remain functional.
- Text begins space.postInset (8dp) after the thumbnail, or at that inset from the edge when there is no image. Wide windows use space.postInsetWide (10dp).
- Text uses the same inset above, below and between major blocks. A cell grows to fit the larger of its thumbnail and text; the old size.cellMin rule does not govern real posts/catalog rows.
- The closing size.hairline divider in color.hairline is inset by the text inset at each screen edge, plus any reply indent.
- At 2x font scale the image moves above the text. Text and counts expand; RTL mirrors the leading thumbnail and text column.

### Catalog row

The subject uses type.postBody (14sp), or type.postBodyWide (16sp), regular weight, in color.subject; catalog list titles use at most two lines. Source/thread/date metadata uses type.postDetails (10sp) or type.postDetailsWide (12sp), color.inkMuted. The opening excerpt uses the body style, up to two lines; quotes use color.quote and normal text color.ink.

Reply and file totals are plain text below the excerpt, using the body style in color.inkMuted, such as “23 replies, 4 files”. There is no trailing badge and no reserved badge column. Watched unread counts remain a separate color.accent label. The row opens its thread; optional media and context actions retain their callbacks and accessible labels.

### Post

The subject appears first in color.subject using the natural body style. The compact header then shows a non-anonymous author in color.author and post number/time in the details style; the opening post retains its OP marker. Attachment details follow in the same smaller type. Anonymous metadata remains available to accessibility without an extra visible author line.

The comment uses type.postBody/type.postBodyWide. References use color.quoteLink and quoted lines use color.quote; remaining text uses color.ink. Referencing post numbers follow the body as underlined links. Each block is separated by the 8/10dp text inset. The type.postDetails/type.postDetailsWide styles carry numbers, dates and attachment metadata. The compatibility size.readingMax helper remains available to constrained callers; compact posts use the available pane width.

A thread ends with size.threadStatus (88dp minimum), space.postInset (8dp) padding, and real reply/file/update metadata. Its centered label uses type.postBody at Medium 500 in color.inkMuted, matching the verified 14sp footer.

### Reading state in the catalog

- A thread the reader has opened shows its title in `color.inkMuted`.
- A watched thread retains reply/file totals and adds the unread count as plain color.accent text.
- A muted thread shows the word "Muted" as its title in `color.inkMuted`, with no thumbnail and no preview.

### Reply nesting

- A reply is indented `space.gap` for each level below the post it answers, to three levels at most. Deeper replies stay at the third level.
- The indent moves the whole cell, thumbnail included. The hairline under the cell starts at the indent.
- Tapping a post's header line collapses the replies under it. A collapsed post shows the number of hidden replies in `type.meta`, `color.inkMuted`, where its body was.
- Jumping to a post inside a collapsed group expands the group first.

### Unread marker

- The first post the reader has not seen is preceded by a full-width line `size.hairline` thick in `color.accent`, with the label "New" in `type.meta`, `color.accent`, at the gutter above it.
- The retained unread-position pill jumps to the first unread post. Reopening uses the saved reading position when available, then the first unread post. Returning from a quote offers the real originating post, and tapping the thread title returns to the top.

### Files view

The thread toolbar's Files action opens Clover-style album cells. The album container uses space.albumPadding (1dp); each cell uses space.albumCellMargin (1dp), yielding a 2dp outside inset and 2dp between cells. Images have square corners. Cell height is its width plus size.albumDetails (32dp), which reserves the filename/size strip.

The compact Files album retains its own column arithmetic rather than the catalog's grid rule. Files opens the viewer at the selected attachment. The media wall uses the Coral album-card variant; both variants retain real attachment identities. The thread's Download action offers Videos only by default or Everything, while archive saving remains a separate read-only action.

## Phone catalog

### Current Coral grid

The current catalog and Home grids use `orbinCatalogGridSpec`, with 16dp outside gutters, 8dp gaps and a 152dp minimum card width. Ordinary phone widths produce two columns, very narrow panes can use one, and wide panes add columns. The image height is three quarters of its column width. This rule is independent of viewport height; it does not use the historical half-viewport calculation below.

Cards use the shared rounded Coral surface, a compact author/thread identity, a two-line subject, an inset thumbnail and actual reply/file counts. The selected thread has an accent border. Text grows with the system font size, thumbnail requests remain bounded, and muted/spoiler behavior remains functional. Catalog sort controls map to the existing Catalog, Bump order and Reply count values.

### Card mode

`orbinCatalogCardSpec` keeps one column at every pane width. Each card fills the pane between the same 16dp gutters, with 8dp vertical gaps and an image height of three quarters of the card width. The identity, subject, counts, selection, media actions and long-press options use the same card component as Grid. Loading reserves the same horizontal space. Changing layout retains the current thread and resets its within-card scroll offset so a tall preview cannot skip several compact rows.

### List mode

![Phone catalog, list mode](diagrams/phone-catalog-list.svg)

The compact List-mode reference retains the following horizontal budget in a 360dp window:

| Part | Token | Width |
| --- | --- | --- |
| Thumbnail | `size.thumbnail` | 72dp |
| Text start inset | `space.postInset` | 8dp |
| Text column | remainder | 272dp |
| Text end inset | `space.postInset` | 8dp |

There is one 288dp content column after the image, including both text insets; no width is reserved for a count badge.

### Historical compact-grid reference

![Phone catalog, grid mode](diagrams/phone-catalog-grid.svg)

The following helper, diagram and arithmetic are retained as measured compact-reader provenance. They describe `catalogGridSpec`, not the current Coral grid or its default column count.

- The grid viewport is the pane below the fixed app bar. Image height = max(viewport height × ratio.gridImageHeight, size.gridCardMin): half the viewport, so two lines of images fit on screen, with a 120dp floor for short split-screen windows.
- Columns are max(1, floor(viewport width / image height)). A portrait phone or tablet shows one full-width column; a landscape or wide window shows as many columns as keep each image at least as wide as it is tall (for example 4 on a 740 × 360dp phone, 3 on a 1200 × 800dp tablet).
- space.gridOuter (2dp) surrounds the grid. Adjacent cards are separated by space.gridGap (4dp).
- Card width = (pane width − 2 × gridOuter − (columns − 1) × gridGap) / columns.
- The image spans the card's width at that height and is cropped to fill it. A still image is drawn from its original file, decoded to the card's size over the provider thumbnail, because a thumbnail (about 250px) is too small to fill a card sharply; GIFs and videos keep the thumbnail. The text area sits below the image and is at least size.gridCardText (76dp) tall.
- The text area uses space.cardPad (8dp). Subject text uses the regular 14/16sp body style with at most three lines, followed by the opening excerpt and any real source metadata. Plain counters at the foot read, for example, “23R 4I”. Accessibility announces complete reply/file counts and unread metadata.
- Historical cards use radius.compact (2dp) and size.cardElevation (2dp), with no stroked outline. Text grows below the image at large font scales; the image height does not change.

| Viewport width | Viewport height | Columns | Image height | Card width |
| --- | --- | --- | --- | --- |
| 360dp | 560dp | 1 | 280dp | 356dp |
| 411dp | 731dp | 1 | 365.5dp | 407dp |
| 740dp | 304dp | 4 | 152dp | 181dp |
| 1200dp | 744dp | 3 | 372dp | 396dp |
| 360dp | 200dp | 3 | 120dp | 116dp |

The viewport is the window less the status bar and size.appBar, so a 360 × 640dp phone has a 560dp viewport. The first two rows are portrait phones, the third a landscape phone, the fourth a landscape tablet, and the last a short split-screen window held at the size.gridCardMin floor.

The List/Grid choice is remembered per device and shared by Home and board catalogs. Grid is the default only when no saved choice exists.

## Two panes

![Two panes: catalog and thread](diagrams/two-pane.svg)

- A window is wide when both of its sides are at least `size.wideMin`.
- A wide window that is at least `size.twoPaneMinWidth` across shows the catalog and the open thread side by side. Every other window shows one at a time.
- The catalog pane is five twelfths of the window width after the divider is removed, rounded down, and never wider than `size.listPaneMax`. The thread pane takes the rest.
- The divider is `size.paneDivider` wide in `color.hairline`.
- Each pane has its own app bar. The status bar spans both.
- The row of the open thread uses `color.selected` as its ground.
- Back in the thread pane closes the thread and shows the empty pane message. Back with no thread open leaves the board.

| Window width | Catalog pane | Divider | Thread pane |
| --- | --- | --- | --- |
| 800dp | 332dp | 1dp | 467dp |
| 960dp | 399dp | 1dp | 560dp |
| 1280dp | 420dp | 1dp | 859dp |

Horizontal budget of a list row in the 399dp catalog pane:

| Part | Token | Width |
| --- | --- | --- |
| Thumbnail | `size.thumbnailWide` | 100dp |
| Text start inset | `space.postInsetWide` | 10dp |
| Text column | remainder | 279dp |
| Text end inset | `space.postInsetWide` | 10dp |

The width conditions and the pane-width calculation live in `core/model/FeedLayout.kt`. Both platforms use the shared `ui-next/ReaderTwoPane.kt` component for the panes, the 1dp divider and the empty detail message.

## Navigation

Reader app bars retain size.appBar (56dp) height, size.icon (24dp) icons, size.appBarNavigation (56dp) leading navigation and size.appBarAction (50dp) normal actions. Clover's reference size.appBarOverflow is 44dp; the current Coral control reserves and renders at least size.touchTarget (48dp). Native headings and their stable identifiers remain part of the navigation contract: Home is titled Home, Boards exposes one Orbin wordmark heading, and secondary destinations retain their own titles.

| Control | Where | Action in Orbin |
| --- | --- | --- |
| Primary bar | Boards, Home/Feed, Downloads and Settings | Opens the four wired destinations; Downloads shows the active queue count |
| Menu | Reader root app bars and the Boards wordmark header | Opens the retained navigation drawer |
| Drawer entries | Drawer | Home, Watched, Boards, Search and Saved, with Settings separated below |
| Source chip | Home below the fixed app bar | Chooses the real source scope without covering content |
| Inline view toggle | Home beside the source chip | Changes the persisted device-wide List/Grid preference |
| Board title with arrow | Catalog app bar | Opens the source-grouped board picker |
| Reload and Search | Home/catalog app bar where wired | Refreshes retained content or filters/opens the real search scope |
| Catalog sort controls | Catalog chrome | Chooses Catalog, Bump order or Reply count |
| Back | Detail and secondary app bars | Returns through the retained reader hierarchy; closes an open drawer first |
| Files/Posts and Watch | Thread app bar | Switches attachments/posts or changes the real watch state |
| Download | Thread action | Offers Videos only by default or Everything |
| Unread and quote-return pills | Thread content | Jumps to the real unread or originating post without replacing its data |
| Thread title | Thread app bar | Returns to the top |
| Long-thread thumb | Posts view only | Drags to a position or exposes accessible progress; ordinary post taps and swipes remain available |
| Thumbnail or preview | Home, catalog, Files and media wall | Reveals a spoiler first, then opens the selected media attachment |
| Thread text | Home and catalog | Opens the source-qualified thread |
| Post reference | Post body and reply list | Scrolls to that post or opens its existing inline presentation |

The four-destination primary bar complements the drawer. It has no synthetic creation destination. Home keeps its bar outside the list, and loading/empty/error states retain navigation. Badge updates depend on active queue membership, not every downloaded byte.

The drawer retains its measured maximum width, selected-state announcement and source-qualified routes. Menu opens it on both platforms; Android also supports the existing leading-edge gesture. The closed iOS drawer does not capture the system Back edge. Selecting an entry closes the drawer before changing destination.

Settings and Downloads retain hierarchical Back while exposing primary navigation on their main pages. Switching between those two replaces the secondary page instead of growing duplicate stack entries; Back returns to the reader beneath them. Choice pages and details keep their own Back behavior. History remains reachable through the existing tools/settings routes.

Orbin remains read-only: saving, watching, sharing, downloading and following boards do not add posting controls.

## Other screens

All current surfaces use the Coral palette and shared fonts. Rounded cards are white in Light and use the raised neutral surface in Dark/AMOLED; pill search, provider chips and filled/text actions share the same language. Each screen retains its real data, identifiers and callbacks.

### Home / Feed

Home uses the catalog's compact List content or responsive Coral Grid cards. Grid is the unset default; an existing List choice survives. A fresh installation, update and cold launch reach this layout after required board setup.

- The fixed app bar remains titled Home. A compact source chip and an inline view toggle sit below it. Home has no Following/For You selector, full-screen pager or floating action rail.
- Only explicitly subscribed boards contribute to Home. Favorites do not imply subscriptions. Provider and board identities remain qualified, including when different sites share a board path or thread number.
- Thumbnails remain static and bounded; scrolling does not create inline players. An explicit preview opens the selected attachment in the media viewer, while the text area opens its thread. Spoilers reveal before opening media.
- Long-press a row/card/preview for the wired Watch, Save, Share, board and thread-video/comment actions. Home has no per-item three-dot box. The comment/video preference still controls that separate action.
- Each source retains its reading anchor. Switching List/Grid, returning from a viewer, refreshing and updating attachment data preserve stable identities and existing content where possible.
- The primary bar remains outside the collection and shows the real download badge. Loading, empty and failure states retain source controls and navigation; refresh failure keeps existing rows and offers Retry.

### Boards and stored-thread collections

Boards uses the card-stack wordmark, pill search, compact provider choice, local board icons and explicit follow controls. Board opening and following are separate targets. The following state comes from the provider-qualified subscription store.

Saved, Watched and History show keyed Coral cards for actual stored records: readable title, source, thread number, time and available unread metadata. Their lists remain lazy and preserve reading anchors. Rows open the complete stored identity; there are no synthesized profiles, social statistics or decorative media records.

### Search

Global Search describes its existing scope accurately: catalogs of followed boards across sites. Pill query entry, a separate submit action and source filters remain usable with large text. A selected source survives loading when it remains in the returned sources. Idle, loading, no-match and failure states use the shared Coral presentation, and Retry submits the real current query.

### Downloads

Downloads uses lazy, stably keyed Coral file cards with real filenames, status and progress. Retry is a separate accessible action and stacks below long names at large text sizes. Clearing history still requires confirmation; the empty state and save-location guidance remain available. Primary navigation and the badge use the same live queue as the download actions.

### Media wall and viewer

The media wall uses Coral album cards while retaining the existing source/board/media filtering and scan states. A selected file carries its provider, thread, attachment ID and thumbnail hint into the viewer; a missing or ambiguous selection is reported rather than silently opening the first file.

The full-screen viewer keeps its dark media surface and real player ownership. Its persistent Download action and visible Close control remain reachable independently of overflow. Double-tap saves the current media; image pinch/pan and ordinary vertical paging remain available. A deliberate horizontal swipe closes the viewer outside reserved system edges, toolbar and transport regions.

Video/audio speed offers 1x, 1.25x and 2x. Changing speed preserves pause state and attachment identity; the existing WebM path follows the same choice. Download rest of thread queues the other attachments in the current source thread, with the queue retaining its deduplication behavior. Supported media preparing or waiting for an active page remains a poster/loading state; unsupported-media behavior remains distinct.

### Foundation and archives

Mandatory first run requires a persisted eligible follow and an explicit Start action. Provider radio choices expose selection, saving disables controls, and failure remains on setup with a useful message. App lock, safe mode, downgrade notices, release notes, update/local-data dialogs, confirmations, action sheets and snackbars share Coral chrome without changing their stored decisions or destructive confirmations.

Archive pages use the same light/dark color roles, including distinct links, authors, quotations and errors. They remain plain HTML/CSS with escaped content and preserved file/quote links. Spoilers are initially closed native disclosures: colored descendants stay hidden, keyboard and pointer activation reveal them, and nested disclosures remain independent. No script is required.

## Settings

Appearance retains its persisted behavior inside Coral preference cards and sub-pages:

| Setting | Choices | Default |
| --- | --- | --- |
| Theme | System, Light, Dark | System |
| AMOLED black | On, Off | Off |
| Catalog view | List, Grid, Card | Grid when unset; preserve a saved choice |

Settings uses the shared title/Back contract, searchable categories, Coral cards and pill search, trailing values/switches and anchored choices. Long searchable scope choices retain their dedicated page. Orbin-only setting IDs, callbacks, validation and storage behavior stay intact.

A stored legacy color-theme key is migrated away without changing brightness or AMOLED. A backup containing an unsupported legacy color theme remains refused as a whole, with no partial application. The settings, backup and migration tests cover these separate contracts.

## Themes

![Themes](diagrams/themes.svg)

Light, Dark and AMOLED use the same geometry and type. AMOLED uses black screen and app-bar grounds, while raised Coral cards, sheets and dialogs retain the neutral `color.surfaceVariant` fill so their hierarchy remains visible. Compact post backgrounds, placeholders and selection use their documented semantic roles rather than a separate screen-specific palette.

## States

![States](diagrams/states.svg)

| State | What shows |
| --- | --- |
| First load | Skeletons reserve thumbnail/text space in color.surfaceVariant with radius.small helper corners; grid skeletons share the card ratios. A size.progress bar in color.accent appears below the app bar |
| Reload | The loaded content stays; only the progress bar shows |
| Empty | A title in `type.stateTitle`, `color.ink`; a helper line in `type.stateBody`, `color.inkMuted`; a Reload text button in `type.label`, `color.accent` |
| Error | The cause as the title in `type.stateTitle`, `color.error`; a helper line in `type.stateBody`, `color.inkMuted`; a Retry text button in `type.label`, `color.accent` |
| Reload fails with content showing | The content stays and a message at the bottom offers Retry |
| No thread open, two panes | "Select a thread" in `type.stateBody`, `color.inkMuted`, centered in the thread pane |

Error titles name the cause: "Network error", "Not found", "Server error". State content is centered in the area below the app bar. A filled button, where one is used, has a `color.accent` ground and a `color.onAccent` label.

## Edge cases

- **Long titles.** Catalog list titles and Coral card subjects use two lines, then an ellipsis. Supporting text and control areas grow at larger scales.
- **No image.** Compact rows omit the thumbnail. Coral cards do not invent an attachment or an active media action.
- **Spoiler image.** The thumbnail shows a `color.surfaceVariant` cover with the word "Spoiler" until the reader taps it. A tap reveals the image and does not open the viewer.
- **Several files in one post.** The thumbnail shows the first file with a count badge; the viewer pages through them.
- **Large text.** Plain totals stay in the text column at every font scale. At 2.0 the thumbnail moves above it. Card text areas expand rather than clipping complete counts.
- **Large counts.** Catalog totals remain exact, wrap when necessary, and are announced in full.
- **Sticky and closed threads.** A pin or lock icon follows the title, in `color.inkMuted`.
- **Right-to-left.** The cell mirrors: thumbnail at the right, inset text and counts on its leading-aligned text column.
- **Short wide windows.** A phone in landscape is wide in one direction only, so it stays single-pane and uses `size.thumbnail`.

## Accessibility

- General controls preserve size.touchTarget (48dp). Wordmarks and snackbar messages expose one accessible announcement; decorative duplicate text and glyphs are excluded. System text size is applied once on iOS.
- Coral raises the source-verified 44dp toolbar overflow width to a 48 × 56dp target; normal toolbar actions use 50 × 56dp and navigation 56 × 56dp.
- A catalog row is one screen-reader item: title, total replies, files, optional unread count, then date/source metadata. Plain counters are not repeated as a second announcement.
- A post announces author, number, date, then the body. References are separate links.
- Selection in two panes is announced as selected and does not rely on `color.selected` alone.
- Icons in the app bar have text labels for screen readers.

## Performance contract

- Lists and Home request bounded static previews decoded for the drawn size. Catalog still-image sharpening remains bounded to the card. Home does not preload video or own inline players; explicit media opening transfers playback to the viewer.
- The thumbnail's space is reserved before the image arrives, so cells do not change height when images load.
- List items have stable keys made of provider, board and post number, so two sites with the same board code never collide.
- Catalog rows and grid cards in one mode all have the same structure, so the list can reuse them.
- In a single-pane window the screen that is not showing does not fetch.
- In two panes, opening a thread does not reload the catalog, and the catalog keeps its scroll position.
- Reload replaces content in place. It does not clear the list first.
- Download badges deduplicate queue-count updates. Long-thread dragging uses a bounded scroll worker and claims only drags starting on its visible thumb.

## Source record

### Visual reference

**Clover 3.0.2** is the retained source reference for compact reader metrics and interactions; the current rounded-card/chrome adaptation is Orbin Coral. Its actual installed Settings root, Appearance page, choice popup and navigation drawer were captured on an accelerated API 31 emulator in [the reference inspection](https://github.com/Defuuls/Orbin/actions/runs/43237685017). The downloaded APK was verified against the supplied file's SHA-256 before installation; package inspection confirmed `org.floens.chan`, `v3.0.2`, code `30002`.

The observed fresh-install theme is **Yotsuba**, with a red toolbar and peach preference surfaces. Orbin deliberately uses its coral brand accent with Light, Dark and AMOLED surfaces. The reference tables preserve the observed metrics and interaction provenance. Current Coral cards, primary navigation and supporting screens are described above; Orbin-specific settings and read-only features retain their real IDs, callbacks and persisted values.

The application is implemented independently. Standard open-source Roboto font resources retain their license and provenance. Clover's name, logo and application code are not shipped as Orbin assets.

### Values taken from the app

| Item | App value | This design |
| --- | --- | --- |
| App bar height | 56dp | `size.appBar` |
| Thumbnail, default | 72dp | `size.thumbnail` |
| Thumbnail, larger-screen variant | 100dp | `size.thumbnailWide` |
| Grid card width | 120dp | Viewport width / columns; see the grid rule (Orbin sizes cards from the viewport, not Clover's fixed width) |
| Grid card corner / elevation / margin, API 21+ | 2dp each | radius.compact, size.cardElevation, space.gridOuter |
| Grid image height | 13/16 of card width | Half the grid viewport, `ratio.gridImageHeight`, at least `size.gridCardMin` |
| Post body, phone / wide | 14sp / 16sp | type.postBody / type.postBodyWide |
| Post details, phone / wide | 10sp / 12sp | type.postDetails / type.postDetailsWide |
| Post text inset, phone / wide | 8dp / 10dp | space.postInset / space.postInsetWide |
| Drawer maximum width | 336dp | size.drawerMax |
| Divider | 1dp | `size.hairline` |
| Thread footer | 88dp | `size.threadStatus` |
| Error title | 24sp | `type.stateTitle` |
| Empty pane message | 16sp | `type.stateBody` |
| Primary color, fresh Yotsuba | #F44336, status #D32F2F | Orbin Coral toolbar #FFFFFF with dark ink, accent #C73E4E |
| Teal accent | `#009688` | Darkened to `color.accent` so text buttons reach 4.5:1 |
| Post layout | Thumbnail left of text | Same |

The supplied reference APK is `org.floens.chan`, version `v3.0.2` (version code `30002`), SHA-256 `3dfc46508958c53b2eedeb48be22304c4c5d1debf9b19172b09eaa1709441300`.

Its dark theme colors are readable in the style resources. `Chan.Theme.Dark` defines `backcolor` as `#232323` and `backcolor_secondary` as `#303030`. `Chan.Theme.Black` inherits Dark and overrides those values with `#000000` and `#212121`. Orbin keeps comparable dark and AMOLED grounds (#1C1C1E, #000000) and documents its coral toolbar, selection, action, link and subject colors in the color table.

Pane widths were not established from the APK resources. The pane rules follow Orbin's existing code.

### Retained Orbin features

Multi-site source selection, explicit board subscriptions and first-run setup, board icons, source-qualified saved/watch records, unread markers, accessible large-text expansion, two-pane arithmetic and dedicated video playback are Orbin features. They retain their real behavior in the current Coral adaptation, with compact-post metrics derived from the verified reference. They are not represented as features observed in the reference APK.

## Verification

```bash
python3 -m unittest scripts/tests/test_design_spec.py
node scripts/design_docs_visual.cjs
```

The unit test reads the tables in this document. It checks that the arithmetic holds, that every color pair meets its contrast minimum in all three themes, that every token named anywhere is defined, that the diagrams are well formed and use only token colors, and that `schematic.html` repeats the token values exactly.

The visual check renders every diagram and the companion page in light and dark at phone and desktop widths and compares them with the images in `baselines/`. After an intended change, regenerate them with `node scripts/design_docs_visual.cjs --update` and review the difference. It needs Playwright 1.63.0 and its Chromium build; the `Design docs` workflow installs both and runs only when these files change.

Neither check renders the app. `scripts/tests/test_design_spec.py` compares `ui-next`'s canonical values in `ReaderTokens.kt`, `Tokens.kt` and `OrbinTypography.kt` with the tables here in both directions, and verifies that public `OrbinType` reader roles expose those exact metrics. App layout, behavior, screenshot, performance and iOS device evidence are separate checks listed in [Implementation and verification](IMPLEMENTATION.md); passing the document checks alone does not establish that the app follows every rule.


Pinned source evidence: [PostCell.java](https://github.com/chandevel/Clover/blob/7cbf5cd2929bac7fa5fe25dc7e7ec81d781cc4f8/Clover/app/src/main/java/org/floens/chan/ui/cell/PostCell.java), [CardPostCell.java](https://github.com/chandevel/Clover/blob/7cbf5cd2929bac7fa5fe25dc7e7ec81d781cc4f8/Clover/app/src/main/java/org/floens/chan/ui/cell/CardPostCell.java), and [DrawerWidthAdjustingLayout.java](https://github.com/chandevel/Clover/blob/7cbf5cd2929bac7fa5fe25dc7e7ec81d781cc4f8/Clover/app/src/main/java/org/floens/chan/ui/layout/DrawerWidthAdjustingLayout.java). The APK's resource/XML/DEX audit independently verifies the corresponding dimensions and text defaults. Layout measurements are implemented independently; reference source and branded image assets are not copied into Orbin.
