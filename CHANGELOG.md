# Changelog

All notable changes to Orbin are documented here. The format is based on
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and the project adheres to
[Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [122-Haruka] - 2026-09-03

### Changed
- Removed List as a selectable feed and board-catalog layout, keeping Grid as the primary thread presentation and Images as the media-first alternate.
- Improved compact-phone grid readability with wider minimum cards, stronger thread-title hierarchy, more title lines, clearer metadata spacing, and consistent preview proportions.
- Reworked the README and GitHub Wiki around the current interface, architecture, provider model, privacy behavior, troubleshooting guidance, and release process.

### Fixed
- Hardened LynxChan/BBW Chan parsing for inactive boards, absolute and relative media URLs, missing media paths, and catalog creation-time fallbacks.
- Normalized legacy saved List layout state to Grid so upgrades do not leave users on a removed presentation mode.

### Reliability
- Extended UI semantics and screenshot coverage for the grid-only layout and compact-screen readability changes.
- Made release preparation update README release metadata, Wiki Home, and the README hero SVG version/codename together, with repository validation failing if those values drift from gradle.properties.
- Added stable machine-readable release markers to the README hero SVG so future releases can update its visible badge and accessible description automatically.


## [121-Yuki] - 2026-09-03

### Added
- Added an image size control to the media wall, switching tiles between Small and Large.
- Added a feed size slider that scales list rows across the feed layouts.
- Added an optional Reddit-style thread scroll arrow that jumps to the next post, toggled from Settings and off by default.

### Changed
- Replaced the per-release cutter workflows and the New Version dispatch form with a single manifest-driven Cut Release workflow, so a release is described once in release/next.toml.

### Reliability
- Made the media wall scrolling test deterministic and kept the screenshot fixtures stable across the new size controls.


## [120-Sora] - 2026-09-02

### Fixed
- Restored BBW-Chan image, animated-media, and video loading by sending same-origin media referrers instead of missing or hard-coded 4chan referrers.
- Synchronized thread scroll with the gallery's active media page so returning from full-screen media lands on the post that owns the viewed attachment.

### Reliability
- Added regression coverage for media request headers and gallery-to-thread media index mapping.


## [119-Aiko] - 2026-09-02

### Performance
- Bounded complete-thread caching to stabilize memory during long browsing sessions.
- Removed whole-feed minute-tick rebuilds and limited feed autoplay to one visible video player.
- Scoped visited-history queries, reduced encrypted scroll-position writes, and consolidated thread presentation indexes.
- Added low-RAM image-cache tuning, foreground-aware media preloading, and expanded startup/feed Macrobenchmark coverage with CI performance gates.

### Fixed
- Preserved exact thread scroll position around media viewing while reducing persistence overhead.


## [118-Hotaru] - 2026-09-01

### Added
- Added a dedicated Settings shortcut beside Search on the subscribed-feed rail, providing direct access to Settings even when search or command-sheet behavior is unavailable.


## [117-Nao] - 2026-09-01

### Changed
- Removed Orbin Minimal from this repository's build and release surface now that it is maintained as a separate repository. Future Orbin releases build and publish only the main client.
- Cleaned current release documentation so removed Minimal modules and release automation are no longer advertised as part of this codebase.


## [116-Miku] - 2026-09-01

### Changed
- Advanced the signed release train after the completed Orbin and Orbin Minimal audit rollout. Application behavior is unchanged from 115-Misaki / Minimal 10-Ayaka; this release advances version metadata and signed artifacts only.


## [115-Misaki] - 2026-09-01

### Added
- Subscribed-feed sorting with board A–Z as the default, plus subject-aware catalog sorting.
- Live per-file download progress, image-cache usage and clear-cache controls, thread jump controls, and recent-thread command shortcuts.

### Changed
- Settings, command, feed and thread layouts adapt more cleanly to large text and narrow screens, with expanded accessibility and screenshot coverage.
- Orbin Minimal now has an explicit minimal experience profile, first-run board selection, clearer refresh/subscription feedback, and its own baseline-profile benchmark target.

### Performance
- Board bookmark lookups moved to scoped database queries, feed limits can be loaded in bulk, presentation work is moved off the main thread, and media helpers use injected IO dispatchers without production `runBlocking`.
- Minimal board state now serializes refreshes, cancels stale provider work, avoids unnecessary re-sorts, and trims unused dependency surface.


## [114-Yui] - 2026-08-31

### Added
- **A setting for the everyday shock words the built-in filter leaves alone.** The permanent filter
  is always on and stays that way; "Filter everyday shock words" is a separate opt-in for the
  broader terms — death, violence and the like — that catch ordinary discussion as readily as the
  things the permanent filter exists for. It applies to the subscribed feed, the board catalog and
  the thread reader, so turning it on filters what you browse and what you open from it.
- Catalog sort, wired to the chip that was already drawn: bump order, created, replies, images or
  latest.
- Watched threads carry their unread count on the catalog row.
- Obtainium and F-Droid metadata, so the APK can be tracked for updates without the Play Store.

## [113-Nagisa] - 2026-08-31

### Fixed
- **Hiding the rail as you scroll had stopped working entirely.** The audit's tidy-up of the
  scroll-direction tracker moved the previous-position bookkeeping out of the derived-state
  computation and into an effect keyed on the position — which reads better and is wrong: the
  effect sets the previous position equal to the current one, the derivation concludes the list has
  not moved, and the rail never goes away. It is a collector holding the previous position in its
  own locals now, which is neither a side effect in a read nor self-defeating.

  Nothing caught this because nothing tested it. `RailHidesOnScrollTest` does: it scrolls each
  screen and asserts the rail leaves, and it is what found the regression.

### Changed
- **Full-screen browsing covers the catalog and the media wall, not just the feed.** The setting
  reached only the subscribed feed, so a reader who turned it on watched the rail slide away there
  and stay pinned on a board catalog drawn from the identical row in the identical layouts. All
  three screens now hide the rail together and take the system bars with them. The setting is
  renamed from "Full-screen feed" to say so; its stored key is untouched, so nobody's preference
  resets.

### Fixed
- **Secondary text was below the accessibility contrast floor in every theme.** The palette's
  `muted` and `faint` were set by eye, and measured 3.85:1 and 2.18:1 on the light ground against
  WCAG AA's 4.5:1 requirement — with `faint` also failing in dark (2.83:1) and AMOLED (2.66:1).
  Those two tokens carry every timestamp, every reply and file count, every subtitle and every read
  thread, so most of the text on most screens was under the floor. The alphas are now derived from
  the measurement, keeping a visible step between the three tiers, and one board hue that measured
  3.61:1 was darkened. `PaletteContrastTest` recomputes every ratio from the palette constants and
  fails if one drops, so the floor is enforced rather than remembered.

- **The layout switcher never said which layout was active.** List / Grid / Images is a
  single-choice control, but each option was a plain button whose selected state existed only as a
  colour, so a screen reader announced three identical buttons. They are a selectable group now.

- **The image layouts could not be used without sight.** An image cell draws a picture and a board
  badge and nothing else, so a reader heard an attachment's filename and a board name and could not
  tell one thread from another. The cells carry the subject as a description now, without drawing
  it. The media wall's tiles had the same gap.

- **Feed and grid rows were not buttons.** They had a tap action with no role and no label, so the
  row announced as an unlabelled target. Rows on the board picker declared a switch but had nowhere
  to put its position; they are proper toggles now.

- **The rail clipped its label at large text sizes, and long subjects lost words.** The one
  large-text screenshot was captured at 1.2× while Android goes to 2.0×, so the fixed 52dp rail had
  never been drawn holding text that size. The rail takes that as a floor rather than a fixed
  height, a subject gets more lines as the type grows instead of the same two, and there is a 2.0×
  golden.

- **Board colours only existed for five boards.** `boardHue` matched five 4chan board names and
  returned the accent for everything else, so across two providers and dozens of boards almost
  every row came out the same terracotta — the "colour per board" idea worked in the screenshots
  and nowhere else. Boards hash into a table of ten hues, all of which clear the contrast floor on
  both grounds. The five that shipped with a fixed colour keep it.

- **A layout switch scrolled the feed back to the top.** The scroll-to-top effect was keyed on the
  layout as well as the request, so anyone who had used scroll-to-top once was sent back to the top
  every time they changed layout thereafter.

- **The rail documented a dismissal gesture that was never built.** Its comment said layers "are
  dismissed by dragging them down"; nothing in the module has ever implemented a drag. The comment
  now says what actually dismisses a layer — the system back gesture — and records what building
  the drag would take.

### Changed
- **The interface can be translated.** `:ui-next` had no resources directory, so every word in the
  redesigned interface was an English literal in a composable — a regression against the rest of
  the project, where `:media` and the feature modules keep their strings in `strings.xml` precisely
  so they can be translated. All of them have moved, and the counts that were built by appending an
  "s" to a noun (which read "1 replies") are plural resources.

- **Grids gain columns on wider screens instead of stretching.** All five grids were fixed at two
  or three columns, while the screens they replaced size themselves adaptively — so the redesign
  had quietly dropped tablet and landscape layout. The minimums are the widths the fixed counts
  produced on a phone, so phones are laid out exactly as before.

- **Every screen shares one scaffold.** The ground, the rail and the bottom-padding arithmetic were
  written out separately in four screens, with the same unexplained constant copied into each.
  `NextScaffold` owns them, which also makes hide-on-scroll available to the board catalog and the
  media wall rather than to the feed alone.

- Rows animate into place when the feed reorders, instead of jumping.

### Performance
- The feed rebuilt its row list on every recomposition, so the screen could never skip and its
  whole lazy content re-ran; the image layout re-filtered the feed inside the grid's content lambda
  for the same reason. Both are remembered now.
- Filtering the feed built a lowercased copy of every post's full markup, for every thread, on
  every keystroke. It checks the fields in turn and stops at the first match.
- The two identical scroll-direction trackers are one function, and it no longer writes state from
  inside a derived-state computation.

## [112-Kaede] - 2026-08-30

### Changed
- **The list-view thumbnail is landscape now, not square.** 111-Riko stopped list rows cropping
  their attachment, which meant a wide file was drawn whole but small: a 16:9 image came out 68x38
  inside a 68dp square, with empty bands above and below it. The tile is 88x68, so that same image
  is drawn 88x50 — about a third larger in each direction — and a 4:3 file nearly fills the tile.

  A tall or square file is drawn no smaller than it was; it sits between side gutters instead of
  bands. The extra 20dp comes out of the text column, and 88 is where that stops being worth it:
  at 96 a typical subject wraps onto a third line, which makes every row taller and fits fewer
  threads on screen. The tile is still 68dp tall, so row height and scroll position are
  unchanged.

  The feed and the board catalog share one row, so both move together. Grid and image-wall layouts
  are untouched. The eight screenshot goldens that show list rows were re-recorded; the grid and
  image-wall goldens did not change, which is the check that this stayed in the list layout.

## [111-Riko] - 2026-08-30

### Fixed
- **List view cut the sides off every attachment.** A list row puts its thumbnail in a fixed 68dp
  square, and the thumbnail filled that square whatever shape the file was. Imageboard attachments
  are almost never square, so a wide image lost its left and right edges and a tall one lost its top
  and bottom — a panel, a screenshot's text, a face — leaving whatever happened to be in the middle.
  In list view the thumbnail now fits inside the tile instead of filling it, so the whole image is
  there. Wide files therefore render shorter than the tile is tall, with the row's own background
  above and below them; the tile stays 68dp, so row heights and scroll position are unchanged.

  Grid and image-wall layouts still fill their tiles. There the tile *is* the content rather than a
  marker beside text, and fitting would put gaps through the wall. Video previews were never
  affected — the player has always letterboxed.

  `MediaThumbnailScaleTest` renders the tile and reads the pixels back, since the scale is not
  visible anywhere else: same layout, same semantics, different raster. It fails if a fitted tile
  crops, and equally if the default stops cropping, which the grid and the wall depend on.

## [110-Nanami] - 2026-08-30

### Fixed
- **Orbin would not open for anyone who had ever chosen a non-default app icon.** 109-Koharu
  removed the icon picker by deleting four of its five launcher aliases and keeping the fifth. That
  looked safe and was not: the manager it removed had spent every launch writing *explicit*
  component enabled states — the chosen alias enabled, the rest disabled — and those survive an app
  update. Such an install carried an explicit "disabled" on the alias that survived, with the alias
  it was actually using deleted, so the app had no enabled launcher component: no icon, no way in.

  The launcher entry now lives on the activity itself, which is the one component whose enabled
  state nothing has ever written, so the manifest decides on every install regardless of what
  happened before. **Updating fixes an affected install** — no reinstall, and nothing local is
  lost. A home-screen icon placed before this update needs adding again from the app drawer, since
  the component behind it is gone.

  `LauncherEntryManifestTest` now asserts the invariant that prevents this: exactly one launcher
  entry, on a plain activity, with no aliases at all. It fails against 109-Koharu's manifest, which
  is how it was checked. Orbin Minimal was never affected — its launcher entry has always been on
  its activity, and it never had aliases.


### Fixed
- **The app's only launch smoke test runs again.** It had been disabled through two releases for
  two different reasons. The first was the app-icon picker's alias writes, removed in 109-Koharu.
  The second, which that removal exposed, is that `MainActivity` asks for the notification
  permission as soon as it is ready: the system dialog opens over it and pauses the activity about
  50ms after its first frame, leaving nothing for the test to query. The API 31 run passing while
  API 35 failed on the same commit is what named it — `POST_NOTIFICATIONS` did not exist before
  API 33. The test now grants the permission before the activity starts, which changes nothing
  about what a real first launch does.


## [109-Koharu] - 2026-08-30

### Removed
- **The app-icon picker is gone, and with it the alternate icons.** Orbin has one mark now, so a
  setting offering five was offering four ways to make the app look like something it is not. The
  choice cost more than it looked: five activity aliases in the manifest, a manager that toggled
  their enabled state on every launch, and a package-change broadcast each time — which is what had
  been tearing the app's only launch smoke test down mid-run and left it disabled. One alias stays,
  permanently enabled and drawing no icon of its own, because it is the component a launcher pins.
- **Ten launcher resources that nothing referenced** went with it — alternate icons for variants
  that had no manifest entry at all, sitting beside the ones that did and looking equally live.

  Existing installs keep their launcher entry. One alias survives, permanently enabled, because it
  is the component a launcher pins — deleting it would break every pinned shortcut belonging to
  someone on the default icon, which is nearly everyone. Anyone who had picked a non-default icon
  gets the new mark. Backups that still carry an icon choice import fine; the field is ignored.

### Changed
- **A new launcher icon: an orbit, and the one body on it.** Two shapes in one terracotta, on the
  dark palette's own ground. What it replaced was seven paths of hairline strokes and four alpha
  levels drawn in a 256 viewport — around half a pixel wide by the time a launcher scaled it, so
  most of the drawing was not there at the size anyone actually saw. It is drawn in the accent the
  rest of the app uses; the old mark was still in the blue and purple of a palette nothing else
  has used since the interface was rebuilt.
- **The default icon is an adaptive icon**, so the launcher masks it to its own shape and Android
  13 can draw it as a themed icon. It was a bare vector, which gets neither. The "Orbital Orb"
  choice in Settings is now called "Orbit", since it is no longer an orb.
- **Both apps draw the mark from one file** in `:core:designsystem`, rather than from a copy each
  that described itself in a comment as being the same as the other.

### Fixed
- **The theme settings govern the interface again.** Theme, AMOLED black and font size reached the
  gallery and the onboarding wizard but stopped at the edge of every redesigned screen, because
  each screen wraps itself in its own theme and that theme read none of them. Dark mode was the
  visible half of it — 108-Ichika made the screens follow the *system*, which is not the same as
  following the setting, and a reader who had chosen Dark on a light phone still got a light app.
  The shell now states all three once and the screens inherit them: Light and Dark are obeyed,
  AMOLED black draws a true-black ground behind the same ink and accent, and the font size scales
  every screen rather than only the Material ones.
- **Font size multiplies the system's own setting rather than replacing it**, so enlarging text
  system-wide is no longer undone by opening the app — and it is applied once, not once per screen,
  which is what an interface where every screen carries a theme would otherwise do.

### Changed
- **Dynamic color and the ported imageboard skins deliberately stop at the same edge.** The
  redesigned screens keep their own palette — warm ink on warm paper, one terracotta accent, a
  colour per board — because recolouring that from the wallpaper would be the interface they
  replaced wearing their layout. Both settings still govern the gallery, the onboarding wizard,
  dialogs and snackbars.

## [108-Ichika] - 2026-08-29

### Changed
- **Orbin Minimal draws the same interface as the full client.** It had a Material feed and a
  Material board picker of its own — a top bar, a list icon, an overflow, checkboxes — which is the
  interface the full client was rebuilt to get rid of, kept alive in a second app that could only
  drift further from the first. Its feed *is* the full client's feed now, from `ui-next`: the same
  rows, the same three layouts, the same pull-to-refresh, the same floating rail. The rail's one
  affordance is Boards rather than Search, because the board picker is the only other place this
  app has, and it is the whole of its navigation — which also means the empty and failed states
  carry the rail, so a fresh install with nothing subscribed is not a screen you cannot leave.
- **The board picker is a list of boards that are on or off**, drawn the way the settings list is
  drawn — a board's name, its title under that, and where it stands on the right in the accent
  colour. It was a checkbox per row, which read as a form to submit rather than switches that take
  effect as you press them. Its title scrolls away with the list, and Refresh is a word under the
  title rather than an icon pinned in a bar.
- **Orbin Minimal's blocked-downgrade screen** is drawn in the same vocabulary as everything else
  rather than as a Material dialog, which was the last screen in that app still looking like what
  it replaced.
- **Feed autoplay in Orbin Minimal is off**, as it is in the full client until you turn it on. It
  used to be forced on there, on the grounds that an app with no settings screen cannot offer the
  choice — but on a feed spanning every board you follow that spent mobile data without asking.
  Videos still play when you open the thread.

### Fixed
- **Dark mode works.** `NextTheme` defaulted to light, and every screen wraps itself in it, so a
  theme chosen around a screen was overwritten by the screen inside it — the whole redesigned
  interface was light whatever the system said. It now inherits an enclosing choice, and follows
  the system when there is nothing to inherit. The goldens could not catch it: a capture that asked
  for dark got a light screen back and looked like a light capture.
- **Orbin Minimal's board list no longer reports an empty provider when a refresh fails** with an
  exception carrying no message. The failure became a null message, which the screen could not tell
  from "this provider has no boards".

## [107-Tsumugi] - 2026-08-29

### Fixed
- **The feed, board catalogue and media wall now scroll their headers away**, which is what the
  thread reader and the settings list already did and what the documentation has claimed all along.
  All three pinned the title and the layout switch in a band above the list instead, spending a
  strip of every screen on furniture — the exact thing the interface was rebuilt to stop doing. The
  screenshot goldens could not see it, because at rest a pinned header and a first-item header are
  the same picture; there is now a test that scrolls, checked against the pinned layout to confirm
  it fails on it.
- **The feed header no longer stacks on top of itself** when it is not the direct child of a
  column. It emitted its title, its layout switch and its separator as siblings with no parent
  layout of its own, so moving it into the grid made all three overlap.

## [106-Yuna] - 2026-08-28

### Changed
- **Settings are editable where they stand — all of them.** Eleven rows still handed you back to the
  category screens the list replaced, so pressing a setting could drop you into the interface this
  one was built to get rid of. Every row is now edited in place: font size, the two timeouts and the
  cache limit open their options under the row (they were always fixed lists, never free-form), and
  tags, the user agent and quiet hours open a text field under the row, written on Save rather than
  on every keystroke. The built-in content filter and HTTPS-only state their guarantee and cannot be
  pressed at all.
- **Actions that need the system open the system, not another screen.** The saved-media folder, the
  backup export and import, the crash-details file and the update check run from their own row, with
  the platform's own picker over this screen. Each says what it will do before you press it, because
  "merges rather than replaces" is not something to find out afterwards.
- **A settings result from Search lands on the setting.** It used to open the category screen the
  setting lived on. The list is lazy now and search results carry a row's id, so it scrolls to the
  row itself.

### Removed
- **The seven settings category screens.** Content & Feed, Notifications, Appearance, Media &
  Playback, Privacy & Network, Storage & Backup and Advanced, along with their routes and the row
  components they shared. Everything they held is in the one list.

### Fixed
- Quiet hours were shown even with thread-watch notifications off, and the DNS-privacy notice —
  the only way you learn your lookups have stopped being encrypted — existed only on the deleted
  privacy screen. Both are back where they belong, in the list.

## [105-Himari] - 2026-08-28

### Fixed
- **Every screen was cut off at the top and the bottom.** The interface draws edge to edge, and the
  top bar and bottom navigation bar it replaced were what had been keeping content out of the status
  bar and the gesture handle — nothing took that job over, so the first row of each screen sat under
  the clock and the rail sat behind the navigation bar. Content is now held clear of both, while the
  background still runs to the edges and lists still scroll underneath. The command sheet also moves
  with the keyboard rather than leaving its results behind it, and the offline banner clears the
  status bar.
- **A screenshot golden could not have caught it:** Robolectric renders with no system bars unless a
  test puts them there. There is now a test that dispatches real insets and asserts content clears
  them, checked against the broken layout to confirm it fails on it.

## [104-Akari] - 2026-08-28

### Changed
- **The interface is rebuilt.** Every screen now comes from `:ui-next`: a feed, thread reader,
  board catalog, settings list and media wall drawn from one small vocabulary — one bar, one
  separator, actions set as words. The old interface spent a top bar and a bottom navigation bar on
  every screen before drawing a single post, reached twenty-one destinations through those bars and
  their overflow menus, and boxed every row in an elevated card. What replaced each part is listed
  below; nothing here is a reskin of the old screen.

- **One floating bar replaces both the top bar and the bottom navigation bar.** It says where you
  are and offers one affordance — Search. There is no back button, because layers are dismissed by
  dragging them down, and no tabs, because there are no longer tabs. It slides away as you scroll
  down and returns on any scroll up, which is what the "full-screen feed" setting now drives.

- **A command surface replaces navigation.** One list holding every board, the threads you were
  last reading, all fifty-nine settings, the other destinations and three actions, filtered by what
  you type. To someone typing `auto` there is no meaningful difference between a screen called
  Media & Playback and the toggle inside it they actually wanted, so both are in the same list. An
  empty query offers places and actions rather than the whole catalogue, and a label that starts
  with the query outranks one that merely mentions it.

- **The subscribed feed is one list, ordered by activity.** It was a list of boards each containing
  threads, so a quiet board's stale thread outranked a busy board's live one purely on alphabetical
  board order. Every subscribed board is merged and sorted by when each thread last moved. Board
  headers and per-board collapsing go with that grouping; a colour per board replaces them, so a
  mixed feed is still sortable by eye.

- **Settings are one scrolling list.** Fifty-nine of them under the seven headings the category
  screens used, which are waypoints now rather than destinations: you can flick past a heading, and
  you cannot flick past a screen. Toggles flip where they stand and choices open under their own
  row. Twelve settings that need a keyboard, a time picker or a file picker still open the screen
  that has the right editor, so the trip is the exception rather than the rule.

- **A read thread loses its weight rather than being dimmed.** Dimming made a thread you had
  already opened harder to read; losing the bold makes it easy to skip without making it hard to
  read.

- **Actions are words placed beside what they act on**, rather than icons in a bar. Each carries a
  button role and a 48dp touch target, which a clickable label does not get for free.

### Added
- **A reply's depth is visible in the thread reader.** The reader has always been one flat
  chronological list, leaving the reader to follow quote links by hand to see what answers what. A
  reply now steps in once per link in the chain it hangs off, marked with a hairline in the board's
  colour. Depth is derived from quote links: quoting only the opening post is not nesting, because
  that is how people address the thread itself; a post quoting several takes the shallowest, because
  a reply rejoining a top-level conversation belongs to the one it rejoins; and a quote pointing
  forward or out of the thread is ignored, so depth stays well-defined. Order is never changed.

- **A thread's reply counts survive on a saved copy**, because they are counted from quote links
  rather than read from the backlinks the data layer fills in, which a saved copy may not carry. A
  saved copy also says so in the reader's subtitle — otherwise a thread that has moved on since
  simply looks quiet.

### Fixed
- **A post with more than one attachment showed only the first.** Most engines allow one file per
  post, so this stayed invisible; not all of them do. Every attachment now renders, swiped through
  in place when "media scroll in thread" is on and stacked when it is off.
- **A long board id ran its badge off the edge of its tile** on the media wall and the feed's
  image-only cells.
- **An action's ripple was not clipped to the shape it belonged to**, because the click arrived as
  a modifier from the caller and landed before the component's own clip.

### Removed
- **The settings hub and the settings search screen.** The hub was seven doors; the search screen
  existed to find your way back through them, and its index feeds the command surface now. Both
  were what one list plus a command surface is for, so keeping them would have been keeping the
  problem.
- **The previous feed, thread reader and media wall**, once everything they carried had a home.
  The tablet feed dock goes with the feed — its refresh, scroll-to-top and settings buttons are
  commands now.
- **Two session-only overrides**: the feed's and the thread reader's thumbnail-size controls. The
  Appearance setting still governs thumbnail size, including how dense the thread's file wall is.

### Not carried over
- The gallery's return-scroll round-trip in the thread reader, which used to carry the viewer's
  page back so the thread scrolled to the attachment you had been looking at.
- The board catalog draws no sort control, because its stream is fixed to bump order and the view
  model offers no alternative — a chip that does nothing is worse than no chip.

## [103-Hina] - 2026-08-27

### Security
- **Netty moves to 4.1.137.Final.** CVE-2026-59903: `CorsHandler` overwrote a `Vary` header the
  application had already set, so a cache in front of it could serve one user's response to
  another. The pin sat at 4.1.136.Final, the last affected release. Netty reaches this project on
  the Gradle plugin and test classpath rather than either APK, and nothing in an Android client
  runs a `CorsHandler`, so this closes an alert rather than an exposure.


### Fixed
- **The Orbin Minimal asset version lost its `v`.** Hoisting it into the step that derives the
  release title reused that step's version string, which strips `minimal-v` to get the bare number
  for the title — where asset names need only `minimal-` stripped. It republished
  `minimal-v6-Rin`'s notes pointing at `orbin-minimal-6-Rin.apk`, a file that does not exist, and
  the next release would have dropped the `v` from the APK itself.


### Added
- **Orbin Minimal's release workflow can regenerate the notes of a release it already published.**
  `notes_only` skips the build and the upload entirely and rewrites just the notes, which is how
  `minimal-v6-Rin` gets the changes list it should have had. The notes come from the same step a
  real release uses, so a repaired release and the next one cannot disagree about what counts as
  a change — and the asset version moved to a step that runs either way, so the checksum command
  still names the right file when nothing is being built.


### Fixed
- **Orbin Minimal's release notes listed changes to its front end only.** The notes filtered the
  log to `app-minimal`, which describes the screen rather than the app: `minimal-v6-Rin` shipped a
  real fix to the image copy, in `:media`, and published an empty changes list — which reads as
  "nothing changed", and is worse than no list at all. The notes now cover every module the app
  actually ships, read out of `app-minimal`'s own build file so that adding a module widens them
  automatically rather than waiting for someone to notice two lists disagree.


## [102-Mei] - 2026-08-27

### Added
- **`retitle-release.yml` gained a backfill mode and a title override.** `from_number`/`to_number`
  retitles a range of full-client releases in one dispatch; `title` sets an exact string for one
  release. It also carries four codenames a tag cannot express — `TRAPPIST-1` and `Luyten 726-8`,
  whose hyphens belong to the star designation rather than separating words, and Barnard's and Van
  Maanen's Star, whose apostrophes no tag ever carried.
- **A workflow that applies the release-title convention to an already-published release.**
  Titles are derived from the tag from now on, but releases published before that kept whatever
  was typed into the dispatch form — v101-Hana is titled "Hana". `retitle-release.yml` re-derives
  the title with the same rule and sets it, changing the title and nothing else: not the body, not
  the tag, not one asset. Re-running the release workflow would also fix a title, but it would
  rebuild and re-upload the APK under the same URL and change the checksum of a file people may
  already have downloaded.

### Changed
- **The clipboard copy moved from the gallery feature into `:media`.** That is the layer which
  already holds a network client; a feature module reaching for OkHttp directly is how the
  `feature → domain → model` boundary erodes. The screen asks its ViewModel, which asks an injected
  `ImageClipboard`.
- **`retitle-release.yml` can rewrite a release body's leading heading.** Opt-in, single-release
  only, and never during a backfill: the body is prose someone published, and the only part of it
  this workflow has any business touching is the heading that repeats the title.
- **The full client's back catalogue is titled to the convention.** Releases v27 through v100 read
  "Orbin 57 - Arcturus" rather than a bare "Arcturus", matching the current releases and the Orbin
  Minimal line. Where an older title carried a summary after a colon, the summary is kept — it is
  worth more to a reader scanning the list than uniformity is.
- **The dotted-version era (v23.8 to v25.2.1) is deliberately left alone.** Those tags carry no
  codename, so the name lives only in the release title: retitling `v25.2.1` from "Cleopatra" would
  not reformat that codename, it would delete it.
- **Both apps' releases are titled the same way.** The full client published as "Hana" while
  Orbin Minimal published as "Orbin Minimal 5 - Aoi" — on a releases page carrying both apps, one
  of those says what it is and the other does not. Titles are now derived from the tag rather than
  typed into the dispatch form: `v101-Hana` becomes "Orbin 101 - Hana" and `minimal-v5-Aoi`
  becomes "Orbin Minimal 5 - Aoi". A tag with no codename (`minimal-v3`) reads "Orbin Minimal 3",
  and a codename hyphenated in the tag (`v48-Sirius-B`) is spaced back out.
- **Dispatching a release takes one input.** The free-text `tag_message` is gone from both release
  workflows; the derived title annotates the tag too, so the tag message and the release title
  cannot say different things. That free-text field is how the two lines drifted apart.

### Fixed
- **The release-heading rewrite could not reach a release whose title was already correct.**
  `retitle_one` returned as soon as the title matched, and the heading rewrite sat below that
  return — so the one release it was built for, whose title had been fixed an hour earlier, was
  skipped and the run still reported success. Skipping the title no longer skips the body.

### Security
- **Copying an image no longer goes around the app's own network stack.** The clipboard copy
  opened a raw `HttpURLConnection`. That still spoke HTTPS — the platform enforces it — but it
  bypassed everything the shared client exists to provide: the request resolved through the system
  resolver rather than DNS-over-HTTPS, announcing to the network which host was being read from at
  the moment of the copy, and it carried the JVM's default user-agent instead of the configured
  one, making that single request identifiably different from every other request the app sends.
  One copy was enough to undo settings deliberately switched on. It goes through the injected
  client now, and inherits the configured timeouts along with the rest.

## [101-Hana] - 2026-08-26

### Fixed
- **A spoilered video no longer plays itself in the subscribed feed.** Autoplay checked that the
  attachment was a video but not that the poster had marked it, so the one file in a thread that
  was meant to stay hidden until tapped started playing on its own as the row scrolled into view —
  and kept playing. Orbin Minimal already had this guard; the full client, which is where autoplay
  originated, did not.
- **The feed drew no spoiler blackout at all.** Even paused, a spoilered attachment's thumbnail was
  shown in the clear in the subscribed feed: the preview reached for the raw image rather than the
  thumbnail component that knows what a spoiler is.
- **Spoilers are hidden in a thread's media grid.** The grid that shows every attachment in a
  thread at a glance drew the play badge for video and nothing for a spoiler, so the whole point of
  opening that view — seeing everything at once — included the files marked not to be seen.

### Changed
- **One spoiler blackout, drawn identically everywhere.** Three screens each drew their own, at two
  different opacities, and a fourth drew none — which is how a missing one stayed invisible. There
  is a single `SpoilerOverlay` now, and the board catalog's lighter 72% scrim moves up to the 85%
  the rest of the app uses.
- **Both apps ask one function whether an attachment may autoplay.** The rule was written out in
  each feed, and the two answers had already diverged. `canAutoplayInFeed` is covered by unit
  tests, and the thread media grid has a screenshot golden showing a spoilered tile blacked out.


### Changed
- **Orbin Minimal's release assets no longer say "minimal" twice.** The workflow named each file
  after the tag, and the tag already begins with `minimal-`, so `minimal-v4-Yui` shipped as
  `orbin-minimal-minimal-v4-Yui.apk`. Assets now read `orbin-minimal-v4-Yui.apk`, alongside the
  full client's `orbin-v100-Sakura.apk`. The checksum command quoted in the release notes is
  generated from the same string, so it names the file that is actually attached.
- **Orbin Minimal's release notes describe the app rather than the app it used to be.** The blurb
  was written before the feed had media in it, so it defined the app entirely by what it lacks —
  and never mentioned the previews and scroll-triggered video that are now the reason to install
  it. It leads with what the app does, and still says plainly that always-on autoplay will use
  mobile data without asking.

### Security
- **The ten security version pins are written once, not twice.** They have to be installed on both
  the Gradle plugin classpath and every project configuration, and a Kotlin build script runs its
  `buildscript` block before any top-level declaration exists — so the rules had been spelled out
  longhand in both places. The table now lives in `gradle/security-pins.txt` as data, and the rule
  that applies it is built once, in the scope that runs first, and reused by the other. Under the
  old arrangement the Commons IO pin had already drifted to two different versions, fixed by hand
  in 100-Sakura — this removes the place the next one would have come from.
- **A pin that was never reaching project configurations now does.** The project-side rule for
  Commons IO matched group `org.apache.commons`, which has published no release of it since 1.3.2
  — the real coordinate is `commons-io:commons-io`. It matched nothing, so project configurations
  were not pinned at all; the typo was invisible because the plugin classpath had it right.
- **A pin is a floor now, not an exact version.** `useVersion` forces in both directions, so the
  httpclient entry — pinned at the advisory's fixed 4.5.13 while the dependency tree asked for
  4.5.14 — was downgrading a build every time it ran, in the name of security. A pin below what
  already resolved is left alone.

## [100-Sakura] - 2026-08-26

### Changed
- **Release codenames are now popular Japanese female names**, for both apps, from this release
  and `minimal-v4` onward. They replace the pasta names used for v91-v99, which replaced star
  names before that. Both lines draw from one pool and a name is never reused, so a codename
  identifies exactly one release across the two.
- **Orbin Minimal's tags carry a codename for the first time.** `minimal-v1` through `minimal-v3`
  were bare numbers; from `minimal-v4-Yui` the shape matches the full client's. Its `versionName`
  follows suit — `4-Yui` rather than `4`.

### Security
- **A security version pin had drifted between the two scopes that apply it.** Commons IO was
  forced to 2.20.0 on the plugin classpath but 2.18.0 on project configurations — the same CVE,
  two answers. Netty and protobuf are read from `gradle.properties` precisely so the two scopes
  cannot disagree; the other eight pins are still written twice, and this is the first to drift.
- **Removed the unused `jsoup` version-catalog entry.** Nothing references it — the providers parse
  markup themselves — so it produced dependency alerts and update PRs for a library that has never
  shipped in either APK.

- **Debug logging is stripped from release builds.** Preloading logs the media URL it is fetching
  at debug level, and nothing removed it when minifying — so an app that encrypts everything it
  stores was writing what a reader is looking at into the system log. Warnings and errors stay:
  they carry no browsing content and are what makes a crash report legible.

### Fixed
- **Orbin Minimal: copying an image from the viewer works.** It never could. The gallery hands the
  file over through a `FileProvider`, which that build did not declare, so every copy fell back to
  copying the URL and reported "Image unavailable" — blaming the image for a missing manifest
  entry.
- **A watched-thread notification opens the app when tapped.** It carried no content intent, so a
  tap only dismissed it: a notification about a thread could not take you to the thread, or
  anywhere.
- **"1 new reply", not "1 new replies".** The notification body was English assembled in code,
  which also meant it could not be translated. It is a plural resource now.

## [99-Rotini] - 2026-08-26

### Added
- **Orbin Minimal: previews in the feed, and video that plays as you scroll.** Each row now carries
  the opening post's first attachment, and a row whose attachment is a video plays it — muted —
  while the row is on screen, stopping and releasing the player as it scrolls away. This is the
  behaviour the full client offers as an opt-in setting; because this build has no settings screen
  it is always on, which on a feed spanning every board you follow will use mobile data without
  asking. A spoilered video never autoplays: it stays blacked out until tapped, since starting
  playback would hand over exactly what the spoiler is hiding.

### Changed
- **The board list no longer recomputes on every recomposition.** Filtering, sorting and splitting
  the boards into sections ran again on every scroll, keystroke and favourite toggle — and the
  filter builds and lowercases a string per board. It is computed once per change of input now.
- **The watched-thread notifier no longer blocks a worker thread.** It read settings with
  `runBlocking` from inside a coroutine, once per notification posted.

## [98-Ziti] - 2026-08-25

### Fixed
- **The all-media wall no longer reports a partial sweep as a complete one.** Boards whose catalog
  could not be fetched were counted internally but never shown, so "9 files from 70 boards" read
  exactly the same whether every board answered or a third of them failed. The count of boards that
  could not be loaded now appears beneath the summary.
- **A thumbnail that fails to load is readable again.** The tile draws "Image unavailable" across
  its bottom edge and the board badge sat on top of it — two runs of white text over each other,
  neither legible. The badge has moved to the top of the tile. This is a common sight rather than a
  rare one: the wall asks every board for thumbnails at once and can be rate limited.
- **Orbin Minimal:** a thread with one reply reads "1 reply" rather than "1 replies".
- **Orbin Minimal:** the board picker is no longer a dead end. An empty list was drawn as a
  spinner, so a provider that returned no boards — or a fetch that failed — left it turning
  forever with no explanation and no way out. Loading, empty and failed are now three different
  screens, and the last offers a retry.
- **Orbin Minimal:** opening the board picker fetches the board list. Nothing ever asked for it, so
  on a fresh install there was nothing to pick from and no way to make it appear. There is a
  refresh action in the bar as well.
- **Orbin Minimal:** "Back" and "No subject" are translatable, rather than English baked into the
  code.
- **Release notes name the right previous version.** Both APKs are tagged on the same branch, so
  the full client's notes diffed against whichever tag was nearest — v97-Penne's read "Changes
  since minimal-v1" because Orbin Minimal happened to release in between.

### Added
- **Screenshot coverage for Orbin Minimal and the all-media wall.** Neither screen had ever been
  drawn in a test; both were covered only by logic tests over their view models. Every state a
  reader can land on is now captured and compared on each pull request, which is how the defects
  above were found.

### Changed
- **The repository checks its own consistency on every pull request.** The shipped version is
  written in `gradle.properties` and quoted in four other files, and nothing held them together —
  v85 through v89 all shipped versionCode 107, and the README has been left a release behind more
  than once. `scripts/validate_repo.py` also verifies the changelog's links resolve to tags that
  exist; 38 of 102 entries had no link at all.

## [97-Penne] - 2026-08-25

### Changed
- **Orbin Minimal versions and releases on its own line**, starting at **1** rather than inheriting
  the full client's number. It is a separate app with its own cadence, and a first release labelled
  114 would say something untrue about it. Tags are `minimal-v*`, built and published by their own
  `release-minimal.yml`; the full client's release no longer carries the minimal APK, since
  publishing one APK under two version schemes is how a download gets mistaken for the other app.

### Added
- **Orbin Minimal — a second, separate APK.** A pared-back reader whose entire interface is your
  subscribed boards as one flat feed: every thread across every board you follow, merged and sorted
  by latest activity, with a board tag, a title and a reply count per row. Tap to read, tap an
  image to view it full screen. That is all of it — no bottom navigation, no settings hub, no
  search, gallery, downloads, bookmarks, history, notifications, app-lock or themes. It installs
  alongside the full client rather than replacing it, and is published on each release as
  `orbin-minimal-<tag>.apk` with its own checksum.

  It is a different front end over the same layers, not a second implementation: identical
  providers, caching, encrypted storage, board filters and the always-on content filter. Because
  Android sandboxes the two applicationIds separately, its subscriptions are its own — pick boards
  from the list icon in its top bar.

### Changed
- **The shipped version lives in `gradle.properties`** (`orbin.versionCode` / `orbin.versionName`)
  instead of `app/build.gradle.kts`, so one edit moves both APKs. Two build files each holding a
  literal version is how a bump gets half-applied — which has already happened here once, with
  v85 through v89 all shipping versionCode 107.
- **Release signing is configured once** in the build-logic convention plugin rather than inline in
  `app/build.gradle.kts`. Two copies of that logic would drift, and the way they would drift is one
  APK silently shipping debug-signed.

## [96-Linguine] - 2026-08-22

### Added
- **Deep scan for reply media (opt-in).** The all-media wall reads board catalogs, which reach each
  thread's opening post but not the files attached to its replies. Switching this on follows the
  catalog sweep with a second pass that walks every thread it found and adds what it finds inside.
  Off by default, and worth understanding before switching on: it is one request per *thread*
  rather than per board — thousands of them — so it is trickled out at about one a second to stay
  inside what providers ask of a client. It fills over hours rather than minutes, runs only while
  the all-media screen is open, and stops the moment you switch it off or leave. New files append
  at the end, so nothing already on screen moves. Toggle it from Settings → Content & Feed or the
  compass button on the wall itself; the wall reports "Deep scan: 120/3,412 threads" while it runs.
  A thread the wall's filters dropped is not walked, so the deep scan cannot reintroduce it.

## [95-Farfalle] - 2026-08-21

### Added
- **"All media" — every file on every board in one grid.** A new screen that sweeps every board's
  catalog and pours every image and video it finds into a single continuous grid, with no board to
  pick and no thread to open first. It fills board by board as the sweep runs, so there is
  something to look at within a second or two rather than a spinner until every board is in, and
  what is already on screen never reshuffles underneath you. Reachable from Settings →
  Content & Feed, and from the grid button in the Gallery tab's top bar. Pull down to re-sweep.
  Your board filters, hidden tags and the always-on content filter all apply, and a file posted in
  two places appears once.
- **The fast scrollbar now works on grids, not just lists.** The all-media wall runs to thousands
  of tiles, where flinging never gets anywhere; press or drag the bar to cross it in one gesture.

### Changed
- **Tapping a file opens the gallery at that exact file.** The gallery could previously only be
  opened at a position in a thread's media, which the all-media wall has no way to know — it holds
  a catalog's files, a different list from the thread's. It now accepts the file itself and finds
  it, falling back to the position if the file has since been deleted from the thread.

### Known limitations
- The all-media sweep reads board catalogs, so it reaches every thread's opening post plus whatever
  teaser replies a catalog carries — not every reply in every thread. Reaching those would mean one
  request per thread, thousands per sweep, which is the traffic pattern that gets a reader
  rate-limited by the provider.

## [94-Fusilli] - 2026-08-21

### Added
- **"All boards" in Settings → Content & Feed.** Opens a full-screen gallery of every board the
  provider offers. The gallery itself is not new, but it was only reachable from the empty
  subscribed feed, so it disappeared the moment you subscribed to anything and there was no way
  back to it. Now there is.

### Fixed
- **The board gallery no longer bypasses the built-in content filter.** Board lists were filtered
  when read from the cache but not when returned straight from a refresh, and the gallery uses the
  latter — so it listed boards the always-on filter exists to remove. A hole that was invisible
  only because the screen was so hard to reach.
- **The board gallery honours your own board filters.** "Hide NSFW boards" and hidden tags applied
  to the home board list but not here, which would have made a gallery of every board the one place
  those settings did nothing. The random-board button also drew from the unfiltered list, so it
  could drop you into a board you had hidden; it now picks only from what is shown. A gallery left
  empty by filters says so, rather than claiming the provider returned no boards.

## [93-Orecchiette] - 2026-08-20

### Added
- **A fast scrollbar down the right edge of a thread.** Press the track to jump straight to that
  point, or drag the thumb to sweep the whole thread under your finger. Flinging moves by whatever
  momentum it happens to carry, which makes reaching the middle of a long thread a matter of
  flinging repeatedly and overshooting. The bar positions by post rather than by pixel — a lazy
  list only knows the height of what it has already measured — so a thread of very uneven posts
  tracks slightly unevenly. It is absent rather than greyed out on a thread that already fits on
  screen, so it never eats a tap meant for a post.

### Fixed
- **A thread stops rewinding to where you opened it.** 92-Rigatoni made the scroll restore run
  whenever the list was rebuilt, which fixed reopening the app but introduced a worse problem: the
  position it restored *to* was a snapshot taken when the thread was opened and never updated
  afterwards. Rotating, or a refresh that passed through the loading state, therefore threw the
  reader back to the start of the thread — and the save that followed wrote that stale position
  over the real one, so the place you had actually read to was lost. The restore target now moves
  with you.

## [92-Rigatoni] - 2026-08-17

### Added
- **A scroll-to-bottom button in the thread top bar.** The bar could already be tapped to jump to
  the top, but that gesture is invisible and had no opposite, so reaching the newest replies in a
  long thread meant flinging. Works in both the post list and the thumbnail grid, and lands on the
  true end of the thread rather than the top of the final post.

### Fixed
- **A thread reopens where you left off after leaving the app.** Two faults had to be fixed for the
  position to survive. It was only written 600ms after scrolling stopped, so leaving inside that
  window and having the process killed later — reclaimed in the background, or a device restart —
  lost the write entirely; it is now also flushed when the app is stopped. And the guard that stops
  a pull-to-refresh from yanking you back was itself saved across process death, so on a cold start
  it suppressed the very restore it should have allowed: the position was read from the database
  and then ignored.
- **Rotating a thread no longer jumps to the top.** The scroll-to-top request was saved across
  configuration changes and never reset, so it re-fired on every rotation and overrode the
  scroll-position restore.

## [91-Bucatini] - 2026-08-15

### Changed
- **Release naming:** pasta codenames replace star codenames for release milestones. The star era
  ran from v30 and closed with v90 — Vega; from v91 the codenames are types of pasta, returning to
  the food themes used for v4–v22. Only forward-looking documentation changed — the historical
  record of past eras is left as it was — and the tag format `v<number>-<Codename>` is unaffected.
- **No functional changes.** This release carries documentation, two code comments and the version
  bump only. The app behaves exactly as 90-Vega does; there is no reason to update for it beyond
  keeping the version number current.

## [90-Vega] - 2026-08-15

### Added
- **A built-in content filter that cannot be turned off.** Gore, shock, abuse and violent content
  is removed from every surface that shows post text, board metadata or a filename: board
  catalogs, threads and their replies, the subscribed feed, search, the gallery, reading history,
  bookmarks and downloads. A thread whose opening post matches is not rendered at all, and is kept
  out of history. There is no setting behind it — it survives a settings reset, a restored backup
  and a fresh install — and Settings lists it read-only so a reader who notices something missing
  can find out why.

  The term list is deliberately blunt. Alongside unambiguous terms it filters the bare words
  `death`, `gun`, `murder`, `violence`, `hanging`, `suicide` and `rent`, so ordinary threads about
  death metal, gun control, murder mysteries, hanging out, suicide prevention or the cost of rent
  are removed too. Matching is whole-word, which is what keeps `shotgun`, `parent`, `current`,
  `torrent`, `deathmatch` and `necromancer` readable. Being a keyword filter over text the engine
  provides, it cannot inspect images: it reduces exposure rather than eliminating it.

- **Older builds refuse to run once a newer one has.** After a version launches successfully on a
  device, no earlier version will start there; it shows which build it is and which is expected,
  then exits. The high-water mark is raised only by a launch that has actually got somewhere, so a
  build that crash-loops on startup cannot lock you out of the working build you came from.

  Scope worth knowing: Android already refuses to install a lower `versionCode` over an existing
  app, so this covers what gets past that — `adb install -d`, and installs that keep app data while
  replacing the APK. It does not survive uninstall-then-reinstall, which wipes the stored mark
  along with the rest of the app's data.

### Fixed
- **`versionCode` and `versionName` are correct again.** Every release from 86-Alula through
  89-Albireo shipped the metadata of 85-Tania — `versionCode` 107 and the name `85-Tania` — because
  `app/build.gradle.kts` was never bumped after v85. Builds were indistinguishable to Android and
  to Orbin's own version display and update check. This release moves to `versionCode` 108 and the
  matching name.

## [86-Alula] - 2026-08-13

### Changed
- **Every reading surface's text now lives in string resources, ready for translation.** The feed,
  board catalogs, the thread viewer, gallery, search, history, downloads, the app lock and safe
  mode had every label compiled into code; they now draw from per-module `strings.xml`, so
  translating Orbin becomes contributing a `values-xx` folder rather than editing Kotlin. The
  settings and onboarding screens, and messages composed in view models, still carry literals —
  each is coupled to code in a way that deserves its own change.
- **Java-Kotlin static security scanning is restored.** Kotlin was temporarily upgraded to 2.4.20-Beta1
  to fix a build-cache deserialization vulnerability, but this exceeded CodeQL's Kotlin extractor
  version ceiling, silently disabling code analysis on every PR. CodeQL support is required before
  moving to 2.4.20; Kotlin is reverted to 2.4.10 and the build-machine vulnerability is deferred
  until 2.4.20 stable releases (Q4 2026).

### Fixed
- **Thread and catalog state that was only visible is now spoken.** TalkBack now announces whether
  a post is collapsed or expanded (and what tapping its header will do), whether a feed thread is
  muted, and whether a thread in the feed or a board catalog has already been read — all states
  that previously existed only as dimming or a chevron. One limitation is recorded rather than
  hidden: a blacked-out spoiler is still spoken immediately by screen readers, because the
  tap-to-reveal masking is purely visual; fixing that requires restructuring how comments render.
- **Hidden tags now apply where you actually read.** They filtered the subscribed feed and the
  board list, but board catalogs and thread replies ignored them entirely — so a keyword you had
  hidden vanished from the feed and reappeared the moment you opened the board it was posted on.
  Catalogs now drop matching threads and threads now drop matching replies. An opening post is
  never hidden inside a thread you opened deliberately.
- **Some hidden tags matched every thread in the feed.** The filter compared your tags against a
  debug rendering of the post rather than its text, so tags like `raw`, `nodes` or `quotelink`
  matched everything and emptied the feed. Matching now uses the comment's own text.

### Added
- **Save a thread's text, so a pruned thread survives.** Downloads have always covered a thread's
  media; its words were never kept. **Save thread** in the thread's overflow menu stores every post
  — subject, comment, poster and attachment links — and when that thread later can't be fetched,
  because it was pruned or deleted, Orbin shows your saved copy with a banner saying so instead of
  an error you can do nothing about. Saving again captures replies posted since; nothing refreshes
  a saved copy on its own. A saved thread reads as plain text: the formatting and quote links come
  from a provider's parser, which isn't re-run on stored posts.
- **Hide by who posted, not just what was posted.** A hidden or muted tag prefixed with `name:`,
  `cap:`, `trip:` or `id:` matches the poster instead of the post. Names and capcodes match as
  substrings; tripcodes and per-thread poster ids must match in full, since a partial match on an
  opaque identifier would quietly catch unrelated posters.

## [85-Tania] - 2026-08-13

### Added
- **Browse images only or videos only.** A new **Show media** setting under Settings → Media &
  Playback restricts every view that shows post media — the subscribed feed, board catalogs, thread
  view, the thread gallery and the gallery browser — to one kind of attachment. Threads left with
  nothing to show drop out of the feed and catalogs rather than appearing as blank tiles, since a
  catalog cell is its OP's thumbnail. GIFs and APNGs count as images; audio and unrecognised files
  are hidden by either filter. The choice also governs what "download all media" pulls from a
  thread and what the gallery browser preloads, so neither spends data on files that are not being
  shown. Defaults to all media, and is included in backups.

## [84-Talitha] - 2026-08-13

### Changed
- **Orbin now runs on Android 12 and newer, instead of Android 15 and newer.** Nothing in the app
  required Android 15: the entire codebase contained a single SDK-level check, and it guarded for
  Android 13 and below — a branch that could never run at the old floor. Lowering `minSdk` to 31
  keeps every feature intact, since Material You dynamic color and the app's other Android 12-era
  foundations start there. Two behaviours degrade gracefully below Android 13, as the platform
  intends: predictive back gestures don't engage, and the notification-permission prompt doesn't
  apply because those versions grant it at install time. Instrumentation tests now run against both
  ends of the supported range rather than only the top.

## [83-Megrez] - 2026-08-13

### Fixed
- **The board list survives closing the app and is readable offline.** It was cached in memory for
  the lifetime of the process only, so the offline banner would appear over an empty board list
  after a restart with no connection. Each provider's boards are now stored in the encrypted
  database, in the order the provider returned them, and refreshed in the background when the cache
  is empty or more than a day old. A failed refresh keeps showing the stored list rather than
  emptying it.

### Changed
- **Android Lint now runs in CI.** ktlint and detekt both read Kotlin as a language; neither knows
  what an API level, a manifest or a resource is, so that entire category of check had never run.
  Existing findings are recorded in per-module baselines and new ones fail the build.
- Updated kotlinx.serialization, Compose, Media3, Paging, Roborazzi and Benchmark to current
  releases.

### Added
- **An on-device test for the encrypted database open path**, covering the failure that shipped in
  82-Alioth: reopening with the same passphrase, opening a second pooled connection, recovering a
  database written under the old zeroed key, and recreating a file that cannot be decrypted at all.
  SQLCipher's native library cannot load under Robolectric, so this path was previously untestable
  and untested.

## [82-Alioth-p2] - 2026-08-13

### Fixed
- **The app crashed on launch with "file is not a database" for everyone upgrading to 82-Alioth.**
  The heap-hygiene step added in 82-Alioth zeroed the SQLCipher passphrase immediately after handing
  it to Room's open-helper factory — but Room opens the database lazily, on whichever background
  thread first touches a DAO, and SQLCipher keeps the passphrase by reference rather than copying
  it. Every open therefore used 32 zero bytes instead of the real key, which SQLCipher reports as
  "file is not a database" against an existing encrypted `orbin.db`. The passphrase now lives as
  long as the database does. Databases *created* by 82-Alioth are keyed with those zero bytes: they
  are detected on the failing open and rekeyed to the real passphrase, so bookmarks, history and
  downloads survive. Any database that still cannot be opened is recreated empty rather than
  crashing the app on every launch.

## [82-Alioth-p1] - 2026-08-12

### Fixed
- **Hardened passphrase recovery against a stored key that can no longer be read.** A SQLCipher
  passphrase whose stored form is malformed, or whose Android Keystore key is gone, no longer
  throws out of app startup: it is treated as "no usable passphrase", and a fresh one is generated
  with the old database dropped so it can be recreated. A failure to write the new passphrase back
  is likewise tolerated rather than fatal.

### Known issues
- **This release does not fix the 82-Alioth launch crash it was cut for.** The passphrase was never
  the unreadable part — it was being zeroed before SQLCipher read it — so affected users kept
  crashing on launch until 82-Alioth-p2.

## [82-Alioth] - 2026-08-12

### Fixed
- **A thread whose response contained no posts crashed the app** instead of reporting that the
  thread was gone. Threads that have been pruned or deleted now surface as a normal "not found"
  error the UI can show and retry from.
- **Reading history could lose an update when two entries were recorded at once.** Recording an
  entry read the existing row, merged it, and wrote it back as three separate steps, so concurrent
  recordings could overwrite each other's scroll positions; the read-modify-write now happens in a
  single database transaction.
- **Pull-to-refresh on a board catalog could skip posts.** Paging's initial load defaulted to three
  pages' worth of items while subsequent pages fetched one, so refreshing left a gap where the
  offsets did not line up. The initial load now matches the page size.
- **Watching a thread you had already bookmarked discarded the bookmark's other details.** Watching
  now flips the watched flag on the existing bookmark instead of replacing it wholesale, and only
  creates a new bookmark when there wasn't one.
- **Switching providers carried the previous provider's subscribed-feed state across.** Scroll
  position and which boards were collapsed are now tracked per provider, so each provider's feed
  keeps its own state instead of inheriting whatever the last one was showing.

### Security
- **The app lock no longer offers an unauthenticated way past itself.** When biometric
  authentication is unavailable — no enrollment, or enrollment deleted — the lock screen used to
  offer "Continue without lock", which let anyone holding the device walk straight in. It now falls
  back to the device credential (PIN, pattern or password), and if the device has neither biometric
  nor device lock configured it refuses to unlock and asks the user to set a device lock up.
- **The Keystore key protecting local data is now StrongBox-backed where the device supports it**,
  falling back to the TEE otherwise. Both keep key material off the heap and out of a copied data
  directory; StrongBox additionally isolates it in dedicated secure hardware.

### Known issues
- **This release crashes on launch for anyone upgrading with an existing database.** A heap-hygiene
  change zeroed the SQLCipher passphrase before the database was ever opened, so every open failed
  with "file is not a database". 82-Alioth-p1 did not fix it; 82-Alioth-p2 does, and recovers the
  databases this release created.

## [81-Phecda] - 2026-08-09

### Changed
- **Removed dead certificate-pinning scaffolding from the network layer.** An empty
  `CertificatePinner` was wired into the shared OkHttp client with no pins ever configured — a
  no-op that changed nothing about how connections are validated, but whose comment misleadingly
  implied pinning was active. The app's actual TLS behavior (system trust-store validation over
  modern TLS, HTTPS-only) is unchanged.

## [80-Merak] - 2026-08-09

### Fixed
- **The subscribed feed's failsafe lock button could sit right at the edge of a center-mounted
  punch-hole camera's touch dead zone**, making it hard to tap on some devices. It's now nudged
  down by however much the display cutout exceeds the status bar on that specific device — a
  no-op on the common case where the status bar already clears the cutout.

### Changed
- **Video autoplay in the subscribed feed now always starts muted**, regardless of the "Mute by
  default" setting — ambient background motion the user didn't choose to watch no longer starts
  playing with sound. Tapping the video still reveals the usual controls to unmute it.

## [79-Mizar] - 2026-08-09

### Fixed
- **The board catalog showed a blank screen when its catalog failed to load.** A failed refresh
  now shows a retry view with the failure message instead of leaving the screen empty with no way
  to recover short of leaving and coming back.
- **Backup import silently accepted entries from a provider the build doesn't ship**, writing
  subscription and bookmark rows keyed to a provider id nothing could resolve. Those entries are
  now skipped, and the import summary reports how many were skipped.

### Added
- **Confirmation before destructive actions.** Clearing reading history, clearing download
  history, deleting a saved search, and removing a bookmark now ask for confirmation before
  applying, instead of taking effect on the first tap.
- **Offline banner.** A banner now appears across the app whenever the device loses its network
  connection, and clears automatically once connectivity is back.
- **Settings search.** A search icon on the Settings hub finds any individual setting by name (or
  by its section) across all seven sub-screens and jumps straight to it.
- The onboarding privacy step now mentions that local data (history, bookmarks, downloads,
  settings) is encrypted at rest, alongside the existing HTTPS-only and DNS-over-HTTPS signals.

## [78-Alioth] - 2026-08-09

### Fixed
- **A single dead or pruned subscribed board could wipe out the entire subscribed feed.** Any
  settings change forces a fresh network fetch of every subscribed board's catalog, and those
  fetches ran concurrently: one board 404ing cancelled every other board's load and replaced the
  whole feed with a blanket "Resource not found" error. Each board's catalog now loads and fails
  independently — a dead board just comes back empty, and the rest of the feed loads normally.

### Added
- **New setting: download folder structure** (Settings → Storage & Backup). Downloaded media can
  now be organized Flat (unchanged default), By board, By board then thread, or By thread, for
  both the default downloads folder and a custom saved-media folder. A retry always reuses the
  folder a download was originally saved to, even if the setting changes afterward.

## [77-Dubhe] - 2026-08-09

### Added
- **Thread scroll position is now saved.** Reopening a thread — even after fully closing the app —
  resumes at the post you were last reading instead of jumping back to the top. The reading
  position (last-read post plus its exact scroll offset) is stored per thread as part of reading
  history and updates automatically as you scroll.
- **Already-read threads are now visually distinct.** Thread titles in the board catalog and the
  subscribed feed dim once a thread has been opened, in both the List and Grid layouts — a
  visited-link-style cue for spotting new threads at a glance.

## [76-Mirfak] - 2026-08-09

### Fixed
- **The v75 failsafe lock button was centered on the wrong element.** It was centered within the
  title slot's own allocated width instead of the top bar as a whole, so on narrower title slots
  it landed on top of the "Orbin" branding text rather than the true middle of the bar. It's now
  centered on the bar's actual full width regardless of how much space the branding or action
  icons end up taking.

## [75-Alkaid] - 2026-08-08

### Added
- **Failsafe lock button in the subscribed feed's top bar.** Tapping the lock icon, centered at
  the top of the feed, instantly covers the app and demands re-authentication — no need to
  background and re-open it to trigger a re-lock. Only shown when biometric app-lock is enabled,
  since there is nothing to lock otherwise.

## [74-Avior] - 2026-08-08

### Added
- **New setting: autoplay videos in the subscribed feed** (Settings → Media & Playback). When on,
  each thread's *first* attachment plays inline, muted per "Mute by default", as its row scrolls
  into view in List or Grid feed layout — no need to open the thread. Playback starts and stops
  automatically as rows enter and leave the screen, and only ever the first attachment; a video
  behind a swipe (with "Media scroll in board" also on) still needs a tap to open. Off by default.

## [73-Peacock] - 2026-08-08

### Added
- **The subscribed feed (the app's home screen) gets the same List/Grid/image-only layout
  toggle** already shipped for the single-board catalog in v72. Board grouping, per-board
  collapse/expand, and per-board thread-count overrides all keep working in every mode; grid
  modes just lose the sticky pin on board headers, since Compose's grid doesn't support that.
  The v72 board-catalog change didn't touch this screen, which is what most people mean by
  "board view" day to day — this was the actual gap.

## [72-Alnair] - 2026-08-08

### Added
- **Board catalog gets a third layout: image-only, no text.** It joins the existing List and Grid
  views, mirroring the thread viewer's own text/image split — cycle through all three from the
  same top-bar icon. Like the thread grid, it has an adjustable thumbnail size (Medium/Large/Fill)
  with its own size-cycle icon, and only pulls full-resolution images at Large/Fill to avoid the
  same bandwidth-contention regression fixed in the thread grid.

## [71-Elnath] - 2026-08-08

### Fixed
- **Thread grid-view thumbnails were reverted back to blurry — actually worse.** v70-Bellatrix
  made every grid tile fetch the full-resolution source, but the grid can hold hundreds of
  attachments at once; fetching originals for all of them contends for bandwidth and decode time
  while scrolling, so more tiles ended up stuck on the low-res placeholder than before the change
  even shipped. Medium is back to the small provider thumbnail; only Large and Fill pull full
  resolution, as before v70.

### Changed
- **The feed top bar's icon box now shows your selected app icon**, not a plain colored square.
  All five launcher-icon variants moved from `:app` into `core:designsystem` so feature modules
  can render them directly.

## [70-Bellatrix] - 2026-08-08

### Fixed
- **Blurry thumbnails in the thread's grid view.** Grid mode only fetched the full-resolution
  source image at the Large and Fill sizes; the default Medium size (96dp) used the provider's
  ~250px thumbnail, which upscales visibly on most modern phones. It now always requests full
  resolution, matching the reply-list view, which already did this — Coil still downsamples the
  decode to the cell, so this costs bandwidth, not memory.

## [69-Regulus] - 2026-08-08

### Changed
- **Board catalog and thread viewer are less cluttered.** The thread bar's five unlabeled icons
  are now three: Download all media and Export links move into a "More" (⋮) menu, since both take
  a moment to run and neither is reached for on every visit. Catalog cards no longer render reply
  count, media count, poster count, and closed/archived status as bordered `AssistChip`s that each
  duplicated the card's own tap-to-open action — they're now plain tonal labels, since a fact about
  a thread isn't a fifth or sixth button that does the exact same thing as tapping anywhere else on
  the card. Preview-reply rows lost the same redundant per-row click target. A collapsed post's
  header shows a real expand/collapse icon instead of a "[+]" text suffix, and long usernames
  ellipsize instead of crowding the post number and timestamp off the row.

## [68-Deneb] - 2026-08-07

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

[Unreleased]: https://github.com/Defuuls/Orbin/compare/v122-Haruka...HEAD
[122-Haruka]: https://github.com/Defuuls/Orbin/compare/v121-Yuki...v122-Haruka
[121-Yuki]: https://github.com/Defuuls/Orbin/compare/v120-Sora...v121-Yuki
[120-Sora]: https://github.com/Defuuls/Orbin/compare/v119-Aiko...v120-Sora
[119-Aiko]: https://github.com/Defuuls/Orbin/compare/v118-Hotaru...v119-Aiko
[118-Hotaru]: https://github.com/Defuuls/Orbin/compare/v117-Nao...v118-Hotaru
[117-Nao]: https://github.com/Defuuls/Orbin/compare/v116-Miku...v117-Nao
[116-Miku]: https://github.com/Defuuls/Orbin/compare/v115-Misaki...v116-Miku
[115-Misaki]: https://github.com/Defuuls/Orbin/compare/v114-Yui...v115-Misaki
[114-Yui]: https://github.com/Defuuls/Orbin/compare/v113-Nagisa...v114-Yui
[113-Nagisa]: https://github.com/Defuuls/Orbin/compare/v112-Kaede...v113-Nagisa
[112-Kaede]: https://github.com/Defuuls/Orbin/compare/v111-Riko...v112-Kaede
[111-Riko]: https://github.com/Defuuls/Orbin/compare/v110-Nanami...v111-Riko
[110-Nanami]: https://github.com/Defuuls/Orbin/compare/v109-Koharu...v110-Nanami
[109-Koharu]: https://github.com/Defuuls/Orbin/compare/v108-Ichika...v109-Koharu
[108-Ichika]: https://github.com/Defuuls/Orbin/compare/v107-Tsumugi...v108-Ichika
[107-Tsumugi]: https://github.com/Defuuls/Orbin/compare/v106-Yuna...v107-Tsumugi
[106-Yuna]: https://github.com/Defuuls/Orbin/compare/v105-Himari...v106-Yuna
[105-Himari]: https://github.com/Defuuls/Orbin/compare/v104-Akari...v105-Himari
[104-Akari]: https://github.com/Defuuls/Orbin/compare/v103-Hina...v104-Akari
[103-Hina]: https://github.com/Defuuls/Orbin/compare/v102-Mei...v103-Hina
[102-Mei]: https://github.com/Defuuls/Orbin/compare/v101-Hana...v102-Mei
[101-Hana]: https://github.com/Defuuls/Orbin/compare/v100-Sakura...v101-Hana
[100-Sakura]: https://github.com/Defuuls/Orbin/compare/v99-Rotini...v100-Sakura
[99-Rotini]: https://github.com/Defuuls/Orbin/compare/v98-Ziti...v99-Rotini
[98-Ziti]: https://github.com/Defuuls/Orbin/compare/v97-Penne...v98-Ziti
[97-Penne]: https://github.com/Defuuls/Orbin/compare/v96-Linguine...v97-Penne
[96-Linguine]: https://github.com/Defuuls/Orbin/compare/v95-Farfalle...v96-Linguine
[95-Farfalle]: https://github.com/Defuuls/Orbin/compare/v94-Fusilli...v95-Farfalle
[94-Fusilli]: https://github.com/Defuuls/Orbin/compare/v93-Orecchiette...v94-Fusilli
[93-Orecchiette]: https://github.com/Defuuls/Orbin/compare/v92-Rigatoni...v93-Orecchiette
[92-Rigatoni]: https://github.com/Defuuls/Orbin/compare/v91-Bucatini...v92-Rigatoni
[91-Bucatini]: https://github.com/Defuuls/Orbin/compare/v90-Vega...v91-Bucatini
[90-Vega]: https://github.com/Defuuls/Orbin/compare/v89-Albireo...v90-Vega
[86-Alula]: https://github.com/Defuuls/Orbin/compare/v85-Tania...v86-Alula
[85-Tania]: https://github.com/Defuuls/Orbin/compare/v84-Talitha...v85-Tania
[84-Talitha]: https://github.com/Defuuls/Orbin/compare/v83-Megrez...v84-Talitha
[83-Megrez]: https://github.com/Defuuls/Orbin/compare/v82-Alioth-p2...v83-Megrez
[82-Alioth-p2]: https://github.com/Defuuls/Orbin/compare/v82-Alioth-p1...v82-Alioth-p2
[82-Alioth-p1]: https://github.com/Defuuls/Orbin/compare/v82-Alioth...v82-Alioth-p1
[82-Alioth]: https://github.com/Defuuls/Orbin/compare/v81-Phecda...v82-Alioth
[81-Phecda]: https://github.com/Defuuls/Orbin/compare/v80-Merak...v81-Phecda
[80-Merak]: https://github.com/Defuuls/Orbin/compare/v79-Mizar...v80-Merak
[79-Mizar]: https://github.com/Defuuls/Orbin/compare/v78-Alioth...v79-Mizar
[78-Alioth]: https://github.com/Defuuls/Orbin/compare/v77-Dubhe...v78-Alioth
[77-Dubhe]: https://github.com/Defuuls/Orbin/compare/v76-Mirfak...v77-Dubhe
[76-Mirfak]: https://github.com/Defuuls/Orbin/compare/v75-Alkaid...v76-Mirfak
[75-Alkaid]: https://github.com/Defuuls/Orbin/compare/v74-Avior...v75-Alkaid
[74-Avior]: https://github.com/Defuuls/Orbin/compare/v73-Peacock...v74-Avior
[73-Peacock]: https://github.com/Defuuls/Orbin/compare/v72-Alnair...v73-Peacock
[72-Alnair]: https://github.com/Defuuls/Orbin/compare/v71-Elnath...v72-Alnair
[71-Elnath]: https://github.com/Defuuls/Orbin/compare/v70-Bellatrix...v71-Elnath
[70-Bellatrix]: https://github.com/Defuuls/Orbin/compare/v69-Regulus...v70-Bellatrix
[69-Regulus]: https://github.com/Defuuls/Orbin/compare/v68-Deneb...v69-Regulus
[68-Deneb]: https://github.com/Defuuls/Orbin/compare/v67-Pollux...v68-Deneb
[67-Pollux]: https://github.com/Defuuls/Orbin/compare/v66-Spica...v67-Pollux
[66-Spica]: https://github.com/Defuuls/Orbin/compare/v65-Antares...v66-Spica
[65-Antares]: https://github.com/Defuuls/Orbin/compare/v64-Aldebaran...v65-Antares
[64-Aldebaran]: https://github.com/Defuuls/Orbin/compare/v63-Acrux...v64-Aldebaran
[63-Acrux]: https://github.com/Defuuls/Orbin/compare/v62-Hadar...v63-Acrux
[62-Hadar]: https://github.com/Defuuls/Orbin/compare/v61-Achernar...v62-Hadar
[61-Achernar]: https://github.com/Defuuls/Orbin/compare/v60-Procyon...v61-Achernar
[60-Procyon]: https://github.com/Defuuls/Orbin/compare/v59-Betelgeuse...v60-Procyon
[59-Betelgeuse]: https://github.com/Defuuls/Orbin/compare/v58-Capella...v59-Betelgeuse
[58-Capella]: https://github.com/Defuuls/Orbin/compare/v57-Arcturus...v58-Capella
[57-Arcturus]: https://github.com/Defuuls/Orbin/compare/v56-Vega...v57-Arcturus
[56-Vega]: https://github.com/Defuuls/Orbin/compare/v55-Polaris...v56-Vega
[55-Polaris]: https://github.com/Defuuls/Orbin/compare/v54-Canopus...v55-Polaris
[54-Canopus]: https://github.com/Defuuls/Orbin/compare/v53-Sirius...v54-Canopus
[53-Sirius]: https://github.com/Defuuls/Orbin/compare/v52-Wolf-359...v53-Sirius
[52-Wolf 359]: https://github.com/Defuuls/Orbin/compare/v51-Rigel...v52-Wolf-359
[50-Fomalhaut]: https://github.com/Defuuls/Orbin/compare/v49-Altair...v50-Fomalhaut
[49-Altair]: https://github.com/Defuuls/Orbin/compare/v48-Sirius-B...v49-Altair
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
[23.1]: https://github.com/Defuuls/Orbin/compare/v23.0...v23.1
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
[12.0]: https://github.com/Defuuls/Orbin/compare/v11.0-coconut...v12.0
[11.0]: https://github.com/Defuuls/Orbin/compare/v10.0-tiramisu...v11.0-coconut
[10.0]: https://github.com/Defuuls/Orbin/compare/v9.0...v10.0-tiramisu
[9.0]: https://github.com/Defuuls/Orbin/releases/tag/v9.0
[8.0]: https://github.com/Defuuls/Orbin/releases/tag/v8.0
[7.0]: https://github.com/Defuuls/Orbin/releases/tag/v7.0
[6.0]: https://github.com/Defuuls/Orbin/releases/tag/v6.0
[5.0]: https://github.com/Defuuls/Orbin/releases/tag/v5.0
[4.0]: https://github.com/Defuuls/Orbin/releases/tag/v4.0
