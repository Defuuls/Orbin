# Orbin reader design

This is the normative design for Orbin's board catalog and thread reader. **The app does not implement it yet.** The shipped screens, and the Roborazzi baselines under `ui-next`, still show the previous design until they are migrated to this one.

The design comes from a set of catalog and thread mockups and from the resources of the Clover 3.0.2 Android app they imitate. Where those sources disagreed, this document records the choice and the reason under [Source record](#source-record).

A visual companion, [schematic.html](schematic.html), shows the same diagrams and tokens on one page. If the page and this document ever differ, this document is correct.

## Scope

Covered here:

- the board catalog in list mode and grid mode;
- the thread reader;
- the two-pane layout that shows both;
- loading, empty and error states;
- light, dark and AMOLED themes.

Orbin is read-only. This design has no compose button, no reply box and no per-post reply control, and none may be added under it.

Not covered: Home, Watched, Saved, Search results, Settings and the media viewer. Those screens keep their shipped behavior until this document specifies them.

## Tokens

Tokens are the only source of values. Layout rules and diagrams refer to tokens by name. This set is independent of the tokens in `ui-next` and `core/designsystem`; it does not reuse or alias them.

Units: `dp` for layout, `sp` for type so text follows the reader's font scale.

### Color

| Token | Light | Dark | AMOLED | Use |
| --- | --- | --- | --- | --- |
| `color.appBar` | `#2E7D32` | `#1E1E1E` | `#000000` | App bar and status bar ground |
| `color.onAppBar` | `#FFFFFF` | `#E8EAED` | `#E8EAED` | App bar title and icons |
| `color.background` | `#FFFFFF` | `#121212` | `#000000` | Screen, row, post, and card ground |
| `color.surfaceVariant` | `#F1F3F4` | `#2A2A2A` | `#1A1A1A` | Thumbnail placeholder, count pill, skeleton bars |
| `color.selected` | `#E8F5E9` | `#1B3A1F` | `#12261A` | Open thread's row in the two-pane catalog |
| `color.ink` | `#1F1F1F` | `#E8EAED` | `#E8EAED` | Titles, post bodies, pill count |
| `color.inkMuted` | `#5F6368` | `#BDC1C6` | `#BDC1C6` | Post numbers, dates, file details, helper text |
| `color.hairline` | `#DADCE0` | `#3C4043` | `#2A2A2A` | Row, post, and pane dividers; card outline |
| `color.accent` | `#00796B` | `#80CBC4` | `#80CBC4` | Progress bar and text buttons |
| `color.onAccent` | `#FFFFFF` | `#003731` | `#003731` | Text on an accent-filled control |
| `color.quote` | `#2E7D32` | `#81C784` | `#81C784` | Quoted (greentext) lines |
| `color.author` | `#117743` | `#81C784` | `#81C784` | Post author name |
| `color.quoteLink` | `#0B57D0` | `#A8C7FA` | `#A8C7FA` | Post reference links and backlinks |
| `color.error` | `#B3261E` | `#F2B8B5` | `#F2B8B5` | Error state title |

The app bar green is darker than the source app's `#4CAF50`. White text on `#4CAF50` has a contrast ratio of about 2.8:1; on `color.appBar` it is above 5:1. In the dark and AMOLED themes the app bar takes the ground color and the green moves to quoted text and author names.

### Contrast

Every pair below must meet its minimum ratio in all three themes. 4.5 is the WCAG AA minimum for text; 3.0 applies to non-text marks.

| Foreground | Background | Minimum ratio |
| --- | --- | --- |
| `color.onAppBar` | `color.appBar` | 4.5 |
| `color.ink` | `color.background` | 4.5 |
| `color.inkMuted` | `color.background` | 4.5 |
| `color.ink` | `color.surfaceVariant` | 4.5 |
| `color.inkMuted` | `color.surfaceVariant` | 4.5 |
| `color.ink` | `color.selected` | 4.5 |
| `color.inkMuted` | `color.selected` | 4.5 |
| `color.quote` | `color.background` | 4.5 |
| `color.quote` | `color.selected` | 4.5 |
| `color.author` | `color.background` | 4.5 |
| `color.quoteLink` | `color.background` | 4.5 |
| `color.error` | `color.background` | 4.5 |
| `color.accent` | `color.background` | 3.0 |
| `color.onAccent` | `color.accent` | 4.5 |

`color.hairline` is decorative and has no minimum.

### Type

The family is the platform's default sans serif: Roboto on Android, San Francisco on iOS.

| Token | Size | Line height | Weight | Use |
| --- | --- | --- | --- | --- |
| `type.appBarTitle` | 20sp | 28sp | 500 | App bar title |
| `type.appBarSubtitle` | 14sp | 20sp | 400 | Board line under a thread title |
| `type.rowTitle` | 16sp | 22sp | 700 | Catalog row title |
| `type.meta` | 12sp | 16sp | 400 | Post number, date, file details, backlinks |
| `type.preview` | 14sp | 20sp | 400 | Catalog row preview line |
| `type.body` | 15sp | 22sp | 400 | Post body |
| `type.cardTitle` | 14sp | 20sp | 700 | Grid card title |
| `type.postAuthor` | 12sp | 16sp | 700 | Post author name |
| `type.pill` | 14sp | 20sp | 500 | Reply count in the pill |
| `type.stateTitle` | 24sp | 32sp | 400 | Empty and error state title |
| `type.stateBody` | 16sp | 24sp | 400 | State helper text and the empty detail pane message |
| `type.label` | 14sp | 20sp | 500 | Text buttons |

### Spacing

| Token | Value | Use |
| --- | --- | --- |
| `space.gutter` | 16dp | Left and right screen edge inset |
| `space.rowPadY` | 12dp | Top and bottom padding of a catalog row |
| `space.postPadY` | 12dp | Top and bottom padding of a post |
| `space.cardPad` | 8dp | Padding around the text block of a grid card |
| `space.gridGap` | 4dp | Between grid cards and around the grid |
| `space.gap` | 12dp | Thumbnail to text column |
| `space.gapSmall` | 8dp | Text to pill, and between blocks inside a post |
| `space.line` | 4dp | Between title, meta line, and preview in a row |

### Size

| Token | Value | Use |
| --- | --- | --- |
| `size.appBar` | 56dp | App bar height |
| `size.touchTarget` | 48dp | Minimum size of anything that can be tapped |
| `size.icon` | 24dp | App bar icon |
| `size.thumbnail` | 72dp | Cell thumbnail width and height in a window that is not wide |
| `size.thumbnailWide` | 100dp | Cell thumbnail width and height in a wide window |
| `size.cellMin` | 96dp | Minimum height of a cell that has a thumbnail |
| `size.cellMinWide` | 124dp | The same minimum in a wide window |
| `size.gridCardMin` | 120dp | Grid column count is the pane width divided by this, rounded down |
| `size.gridCardText` | 76dp | Height of the text block under a grid card image |
| `size.threadStatus` | 88dp | Thread footer that reports reply and image counts |
| `size.pillHeight` | 36dp | Reply count pill height |
| `size.pillMinWidth` | 48dp | Reply count pill minimum width |
| `size.hairline` | 1dp | Row and post divider thickness |
| `size.progress` | 4dp | Refresh progress bar height |
| `size.readingMax` | 640dp | Maximum post text width in a wide pane |
| `size.drawerMax` | 320dp | Maximum navigation drawer width |
| `size.wideMin` | 600dp | Both window sides must reach this to count as wide |
| `size.twoPaneMinWidth` | 800dp | Wide windows at least this wide show two panes |
| `size.listPaneMax` | 420dp | Maximum catalog pane width in two panes |
| `size.paneDivider` | 1dp | Divider between the two panes |

### Radius

| Token | Value | Use |
| --- | --- | --- |
| `radius.small` | 4dp | Thumbnails and skeleton bars |
| `radius.card` | 2dp | Grid card |
| `radius.pill` | 18dp | Reply count pill |

## Shared cell

A catalog row in list mode and a post in a thread are the same cell: gutter, optional thumbnail, gap, text column. A catalog row adds a reply count pill at the trailing edge.

![Shared cell: catalog row and post](diagrams/cell-anatomy.svg)

Rules for both:

- The thumbnail is square: `size.thumbnail` in a window that is not wide, `size.thumbnailWide` in a wide window. Its corners use `radius.small`. The image is center-cropped to fill.
- The thumbnail sits `space.gutter` from the leading edge and its top is one vertical padding below the top of the cell.
- The text column starts `space.gap` after the thumbnail. With no thumbnail the text column starts at the gutter and takes the freed width.
- A `size.hairline` line in `color.hairline` closes the cell across its full width.
- A cell with a thumbnail is never shorter than `size.cellMin`, or `size.cellMinWide` in a wide window. It grows when the text is taller than the thumbnail.

### Catalog row

Top to bottom in the text column, `space.line` apart:

1. Title in `type.rowTitle`, `color.ink`, two lines at most, then an ellipsis. A thread with no subject uses the first line of its opening post.
2. Post number and date in `type.meta`, `color.inkMuted`, one line.
3. Preview of the opening post in `type.preview`, two lines at most. Quoted lines use `color.quote`; other lines use `color.ink`.

The reply count pill is `size.pillHeight` tall and at least `size.pillMinWidth` wide, with `radius.pill` corners on a `color.surfaceVariant` ground. The count uses `type.pill` in `color.ink`. The pill is centered vertically in the row and sits `space.gapSmall` after the text column. It is a label and has no action of its own; the whole row opens the thread.

Vertical padding is `space.rowPadY` above and below.

### Post

Top to bottom in the text column:

1. Author in `type.postAuthor`, `color.author`, then the post number in `type.meta`, `color.inkMuted`. The opening post adds `OP` after its number.
2. `space.line` below: date and time, then file name and size when the post has media, in `type.meta`, `color.inkMuted`, one line. A long file name is shortened in the middle.
3. `space.gapSmall` below: the body in `type.body`. References to other posts use `color.quoteLink` and are underlined. Quoted lines use `color.quote`. Other text uses `color.ink`.
4. `space.gapSmall` below, when other posts reply to this one: their numbers in `type.meta`, `color.quoteLink`, underlined.

Vertical padding is `space.postPadY` above and below. Body text is never wider than `size.readingMax`.

A thread ends with a footer `size.threadStatus` tall that reports reply count, image count and the last update time in `type.meta`, `color.inkMuted`, centered.

## Phone catalog

### List mode

![Phone catalog, list mode](diagrams/phone-catalog-list.svg)

Horizontal budget of a row in a 360dp window:

| Part | Token | Width |
| --- | --- | --- |
| Gutter | `space.gutter` | 16dp |
| Thumbnail | `size.thumbnail` | 72dp |
| Gap | `space.gap` | 12dp |
| Text column | remainder | 188dp |
| Gap | `space.gapSmall` | 8dp |
| Count pill | `size.pillMinWidth` | 48dp |
| Gutter | `space.gutter` | 16dp |

The text column takes whatever width remains, so it widens with the window.

### Grid mode

![Phone catalog, grid mode](diagrams/phone-catalog-grid.svg)

- Column count is the pane width divided by `size.gridCardMin`, rounded down, and never less than one.
- `space.gridGap` separates cards and surrounds the grid. Card width is the pane width, minus one gap more than the column count, divided by the column count.
- A card is a square image the width of the card above a text block `size.gridCardText` tall. Card height is therefore card width plus `size.gridCardText`.
- The text block has `space.cardPad` on all sides and holds a title in `type.cardTitle`, `color.ink`, two lines at most, then `space.line`, then the reply count in `type.meta`, `color.inkMuted`.
- The card has a `size.hairline` outline in `color.hairline` and `radius.card` corners on a `color.background` ground.

| Pane width | Columns | Card width |
| --- | --- | --- |
| 360dp | 3 | 114.67dp |
| 399dp | 3 | 127.67dp |
| 420dp | 3 | 134.67dp |
| 600dp | 5 | 115.20dp |

The reader chooses list or grid in the catalog's overflow menu. The choice is remembered per device, not per board.

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
| Gutter | `space.gutter` | 16dp |
| Thumbnail | `size.thumbnailWide` | 100dp |
| Gap | `space.gap` | 12dp |
| Text column | remainder | 199dp |
| Gap | `space.gapSmall` | 8dp |
| Count pill | `size.pillMinWidth` | 48dp |
| Gutter | `space.gutter` | 16dp |

The two width conditions, the ratio and the cap match what `core/model/FeedLayout.kt` and `app/navigation/BoardDetailTwoPane.kt` do today. The divider there is 0.5dp.

## Navigation

The app bar is `size.appBar` tall on `color.appBar`, with content in `color.onAppBar`. Icons are `size.icon` inside a `size.touchTarget` target. The title uses `type.appBarTitle`; a thread adds its board in `type.appBarSubtitle` beneath.

| Control | Where | Action in Orbin |
| --- | --- | --- |
| Menu | Catalog app bar, leading | Opens the navigation drawer |
| Drawer entries | Drawer | Home, Watched, Boards, Search, Saved, then Settings below a divider |
| Board title with arrow | Catalog app bar | Opens the board picker, grouped by site |
| Reload | Catalog and thread app bars | Reloads the catalog or the thread |
| Search | Catalog app bar | Filters the loaded catalog by title and text |
| Overflow | Catalog app bar | List or grid mode, sort order, open in a browser |
| Back | Thread app bar, leading | Returns to the catalog, or closes the thread pane |
| Bookmark | Thread app bar | Adds the thread to Watched, or removes it |
| Overflow | Thread app bar | Files, download all, share, open in a browser, jump to top or bottom |
| Thumbnail | Post | Opens the media viewer at that file |
| Post reference | Post body and reply list | Scrolls to that post, or shows it inline |

The drawer is as wide as the window less `size.appBar`, and never wider than `size.drawerMax`.

Controls in the source material that Orbin does not have: the compose button, the reply box and its options, and the reply arrow on each post. They are omitted, and nothing takes their place.

## Themes

![Themes](diagrams/themes.svg)

Light, dark and AMOLED differ only in the color table. Sizes, spacing and type do not change with the theme. AMOLED uses pure black for every ground so that an OLED screen turns those pixels off.

## States

![States](diagrams/states.svg)

| State | What shows |
| --- | --- |
| First load | Skeleton cells in `color.surfaceVariant` with `radius.small` corners, at the size of real cells, and a `size.progress` bar in `color.accent` under the app bar |
| Reload | The loaded content stays; only the progress bar shows |
| Empty | A title in `type.stateTitle`, `color.ink`; a helper line in `type.stateBody`, `color.inkMuted`; a Reload text button in `type.label`, `color.accent` |
| Error | The cause as the title in `type.stateTitle`, `color.error`; a helper line in `type.stateBody`, `color.inkMuted`; a Retry text button in `type.label`, `color.accent` |
| Reload fails with content showing | The content stays and a message at the bottom offers Retry |
| No thread open, two panes | "Select a thread" in `type.stateBody`, `color.inkMuted`, centered in the thread pane |

Error titles name the cause: "Network error", "Not found", "Server error". State content is centered in the area below the app bar. A filled button, where one is used, has a `color.accent` ground and a `color.onAccent` label.

## Edge cases

- **Long titles.** Two lines, then an ellipsis. The cell grows to fit.
- **No image.** The cell has no thumbnail and no placeholder; text starts at the gutter.
- **Spoiler image.** The thumbnail shows a `color.surfaceVariant` cover with the word "Spoiler" until the reader taps it. A tap reveals the image and does not open the viewer.
- **Several files in one post.** The thumbnail shows the first file with a count badge; the viewer pages through them.
- **Large text.** At a font scale of 1.3 or more the count pill moves under the preview, aligned to the start of the text column. At 2.0 the thumbnail moves above the text. Nothing shrinks and nothing is clipped.
- **Large counts.** The pill widens to fit; counts of 1000 or more are abbreviated, as in "1.2k".
- **Sticky and closed threads.** A pin or lock icon follows the title, in `color.inkMuted`.
- **Right-to-left.** The cell mirrors: thumbnail at the right, pill at the left.
- **Short wide windows.** A phone in landscape is wide in one direction only, so it stays single-pane and uses `size.thumbnail`.

## Accessibility

- Every control that can be tapped is at least `size.touchTarget` in both directions, whatever its drawn size.
- A catalog row is one item to a screen reader: title, then reply count, then date. The pill is not announced separately.
- A post announces author, number, date, then the body. References are separate links.
- Selection in two panes is announced as selected and does not rely on `color.selected` alone.
- Icons in the app bar have text labels for screen readers.

## Performance contract

- Lists request the provider's thumbnail, never the full file, and decode it at the drawn size for the screen density.
- The thumbnail's space is reserved before the image arrives, so cells do not change height when images load.
- List items have stable keys made of provider, board and post number, so two sites with the same board code never collide.
- Catalog rows and grid cards in one mode all have the same structure, so the list can reuse them.
- In a single-pane window the screen that is not showing does not fetch.
- In two panes, opening a thread does not reload the catalog, and the catalog keeps its scroll position.
- Reload replaces content in place. It does not clear the list first.

## Source record

### Values taken from the app

| Item | App value | This design |
| --- | --- | --- |
| App bar height | 56dp | `size.appBar` |
| Thumbnail, default | 72dp | `size.thumbnail` |
| Thumbnail, larger-screen variant | 100dp | `size.thumbnailWide` |
| Grid card width | 120dp | `size.gridCardMin` |
| Grid card corner, larger-screen variant | 2dp | `radius.card` |
| Divider | 1dp | `size.hairline` |
| Thread footer | 88dp | `size.threadStatus` |
| Error title | 24sp | `type.stateTitle` |
| Empty pane message | 16sp | `type.stateBody` |
| Primary green | `#4CAF50` | Darkened for contrast; see [Color](#color) |
| Teal accent | `#009688` | Darkened to `color.accent` so text buttons reach 4.5:1 |
| Post layout | Thumbnail left of text | Same |

Dark theme colors and pane widths could not be read from the app's resources. The dark and AMOLED palettes here are derived, and the pane rules follow Orbin's existing code.

### Corrections to the wireframes

| Wireframe | As drawn | Problem | Here |
| --- | --- | --- | --- |
| Phone | Thumbnail labelled 344dp wide under an 80 × 80dp box | The two labels contradict each other | 72dp square |
| Phone | Board title drawn above the app bar | The title belongs inside the bar | Inside the bar |
| Tablet | Panes 384dp and 576dp with an 8dp divider in a 960dp window | The three add up to 968dp | 399dp, 1dp, 560dp |
| Tablet | 150dp thumbnail inside a 104dp row | The thumbnail is taller than its row | 100dp in a 124dp row |
| Tablet | 600dp image inside a 576dp pane | The image is wider than its pane | Thumbnail, opened in the viewer |

### Alternatives not adopted

- **80dp thumbnail** from the phone wireframe. The app uses 72dp and 100dp.
- **Media above the post text** from the tablet wireframe. The app puts the thumbnail beside the text, which lets a row and a post share one cell.
- **Equal pane widths** from the two tablet renders. They leave the thread, the screen being read, less room than Orbin's current ratio.
- **8dp pane divider** from the tablet wireframe. A gap that wide costs content width and separates nothing a hairline does not.
- **"N replies" text chips** and a bottom "Catalog" bar from two of the phone renders. Each appeared in one render only.
- **Poster ID line on grid cards** from one tablet render. Most boards do not have poster IDs.

## Verification

```bash
python3 -m unittest scripts/tests/test_design_spec.py
node scripts/design_docs_visual.cjs
```

The unit test reads the tables in this document. It checks that the arithmetic holds, that every color pair meets its contrast minimum in all three themes, that every token named anywhere is defined, that the diagrams are well formed and use only token colors, and that `schematic.html` repeats the token values exactly.

The visual check renders every diagram and the companion page in light and dark at phone and desktop widths and compares them with the images in `baselines/`. After an intended change, regenerate them with `node scripts/design_docs_visual.cjs --update` and review the difference. It needs Playwright 1.63.0 and its Chromium build; the `Design docs` workflow installs both and runs only when these files change.

Neither check says anything about the app. Screenshot coverage for the implemented design is added with the implementation.
