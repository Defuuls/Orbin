# Redesign review: findings, decisions and implementation order

Reviewed 2026-11-15 (Chicago time) against `main` at `d6418e6` (212-Albite).

This records a review of the NekoSurf-inspired redesign of the reader: what was found, the
options considered for each finding, the option chosen, and the order in which the work is
proposed. Nothing in this document has been implemented yet.

## Scope and evidence

- **Visual review:** 16 of the 180 committed screenshot baselines in
  `ui-next/src/androidHostTest/screenshots` (feed, catalog, thread, settings, drawer, saved,
  error state; light, dark and AMOLED; 200% text; right-to-left; narrow width), plus the code
  behind each finding.
- **Implementation review:** the shared UI module `ui-next` (about 19,000 lines), its tests
  (about 14,000 lines, 434 tests), the two host apps' navigation and row mapping, and the
  benchmark module.
- **Limits:** the baselines are Android renders of fixture data, not a device. No iOS-native
  screen was viewed. Nothing was built, run or measured; performance findings come from reading
  code.

## Summary of decisions

| # | Finding | Chosen option |
|---|---|---|
| 1.1 | Board chip renders as `//p//` | One shared board-label function |
| 1.2 | Grid titles unreadable over light images | Stronger gradient plus a contrast test |
| 1.3 | Two navigation systems with different names | Single bottom-bar navigation on phones with a Library tab |
| 1.4 | Large text breaks the chrome | Icon-only bar when labels don't fit; app bar keeps two actions |
| 1.5 | Smaller notes (unread looks, layout button, settings icon) | All in scope |
| 2.1 | Navigation has no single definition | One shared navigation spec, done first |
| 2.2 | Presentation logic written twice | Move mappers into `core:reader` incrementally |
| 2.3 | Flat module with three naming generations | Package split plus an architecture rule, done last |
| 3.1 | Design tokens bypassed | Literal check with a baseline; replace in touched files |
| 3.2 | Small components implemented several times | One pill component, one unread label |
| 3.3 | Screen entry points take about 50 parameters | State and actions objects; split `CatalogCard` |
| 4.1 | Screenshots approve whatever is rendered | A rule-based test per fix, written first |
| 4.2 | iOS rendering almost untested | Fixture-backed native test behind a test-only launch flag; live smoke test non-blocking |
| 4.3 | Test and baseline names describe retired designs | Rename by screen and state |
| 5.1 | Card and grid views download original images | "Sharp previews" setting, default Wi-Fi only, plus a size cap |
| 5.2 | Benchmarks miss the redesign's main screens | Add journeys and record fresh numbers; no gate yet |
| 5.3 | Minor rendering hygiene | Fix in touched files |

---

## Section 1: Visual and UX

The redesign is coherent: one card language, consistent rounded chrome, 48dp touch targets, and
real coverage of themes and text sizes.

### 1.1 Board chip renders as `//p//`

- **Problem:** In card and grid views the board chip reads `//p//` and `//g//`. Both apps already
  pass the board as `/p/` (`app-ios/src/commonMain/kotlin/com/orbin/ios/Rows.kt:59`,
  `feature/home/src/main/kotlin/com/orbin/feature/home/NextFeedScreen.kt:447`), and
  `ui-next/.../BoardCatalog.kt:841` wraps it in slashes again. The text line under the author
  name is correct because `FeedRows.kt:173` trims first.
- **Why it matters:** A visible defect on the main screen, recorded in the baselines as correct.
  The board path is formatted in five separate places, which is how they drifted apart.

| Option | Effort | Risk | Impact | Maintenance |
|---|---|---|---|---|
| A. One shared board-label function used by all five sites; re-record baselines | Small | Low | Fixes it everywhere | Lower |
| B. Trim at the one broken call site | Trivial | Low | Fixes the chip only | Same duplication |
| C. Do nothing | — | — | Defect ships | — |

**Decision: A.**

### 1.2 Grid card titles are unreadable over light images

- **Problem:** Grid cards draw white titles over the thumbnail. The darkening gradient is 2%
  black at 40% of the card height and reaches 78% only at the bottom edge
  (`BoardCatalog.kt:821`). Titles sit in the middle, so they wash out over pale or bright images.
- **Why it matters:** Thread titles are the content of that screen, and real thumbnails are
  often light.

| Option | Effort | Risk | Impact | Maintenance |
|---|---|---|---|---|
| A. Start the gradient higher and darker, so the title always sits on at least about 50% black | Small | Low; darkens thumbnails slightly | Readable on any image | None |
| B. Move title and counts onto a solid panel below the image | Medium | Medium; changes card proportions and every grid baseline | Strongest legibility | None |
| C. Do nothing | — | — | Stays unreadable on light media | — |

**Decision: A**, with a contrast test over a white image.

### 1.3 Two navigation systems with different names

- **Problem:** A phone shows a drawer (Home, Watched, Boards, Search, Saved, Settings) and a
  bottom bar (Boards, Feed, Downloads, Settings) at once. The same screen is "Home" in the
  drawer and title but "Feed" in the bar. Downloads is only in the bar; Watched and Saved are
  only in the drawer; Search is in the drawer and the top bar.
- **Why it matters:** Users have to learn two maps of the app, and neither is complete.

| Option | Effort | Risk | Impact | Maintenance |
|---|---|---|---|---|
| A. Bottom bar becomes the only primary navigation on phones: Boards, Feed, Library (Watched, Saved, Downloads), Settings. Drawer stays for wide windows only | Large | Medium; touches both hosts, navigation tests and many baselines | One complete map | Lower |
| B. Keep both; make the names match and give each list every destination | Small | Low | Removes the naming conflict, keeps the redundancy | Same |
| C. Do nothing | — | — | Confusion stays | — |

**Decision: A** (the full change, not B as an interim step).

### 1.4 Large text breaks the chrome

- **Problem:** At 200% text on a narrow window the bottom bar wraps labels mid-word
  ("Dow / nloa / ds", "Setti / ngs") and the badge collides with the label. Separately, app bar
  titles truncate at the default size ("/g/ - Technolo…", "Weekend wor…") because three or four
  action buttons take the space.
- **Why it matters:** The large-text case is committed as an accepted baseline. The title
  truncation affects every user on the two most-used screens.

| Option | Effort | Risk | Impact | Maintenance |
|---|---|---|---|---|
| A. Bottom bar goes icon-only (labels kept for screen readers) when labels do not fit on one line; app bar keeps two actions and moves the rest into the existing "more" menu | Medium | Low | Fixes both | None |
| B. Cap text scaling inside the bottom bar only | Small | Medium; partly ignores the user's text-size setting | Fixes wrapping, not titles | None |
| C. Do nothing | — | — | Stays broken at large sizes | — |

**Decision: A.**

### 1.5 Smaller notes (all in scope)

- Unread counts have three looks: a solid pill (card), a cyan pill (grid) and plain coloured
  text (list).
- The layout button shows the icon of the next layout, not the current one, so it reads as a
  status indicator.
- In settings, "Theme" and "True black" share the same icon.

---

## Section 2: Architecture

The module boundaries are sound: `ui-next` depends only on the small design-token module and
Compose, every feature depends on it and not the reverse, and
`scripts/validate_architecture.py` checks those rules on every pull request. Nothing to flag on
security boundaries: this layer holds no credentials and does no network or storage access.

### 2.1 Navigation has no single definition

- **Problem:** The destination list exists in four hand-maintained places: the
  `NextDestination` enum (7 entries, `Surface.kt:71`), the bottom bar's hard-coded 4
  (`OrbinStyle.kt:182`), the drawer's hard-coded 6 (`ReaderNavigation.kt:310`), and a separate
  destination-to-route mapping in each app (`app-ios/.../PrimaryNavigation.kt`,
  `app/.../navigation/OrbinNavHost.kt:443`). A deprecated stub at `Surface.kt:81` still says the
  bottom bar "has been replaced by the drawer".
- **Why it matters:** This is why the bar and drawer disagree. A Library tab on top of it means
  editing all four places on two platforms.

| Option | Effort | Risk | Impact | Maintenance |
|---|---|---|---|---|
| A. One shared navigation spec in `ui-next`: ordered destinations with label, icon, and which surface shows each by window size. Bar and drawer render from it; each app keeps only destination-to-route. Delete the stub | Medium | Low; pure refactor, covered by existing navigation tests | Makes the Library change a one-place edit | Lower |
| B. Edit the four lists by hand for the Library change | Small now | Medium; the lists drift again | Ships the change, keeps the cause | Higher |

**Decision: A, done first** as its own step with no visible change.

### 2.2 Presentation logic is written twice

- **Problem:** Android has 15 ViewModels across `feature/*` (about 9,500 lines). iOS has none
  and does the same work in `app-ios` (about 15,900 lines). The shared presentation module,
  `core:reader`, is 536 lines. Feed rows are built separately at `app-ios/.../Rows.kt:52` and
  `feature/home/.../NextFeedScreen.kt:444`.
- **Why it matters:** Every behaviour change lands twice and can differ between platforms.

| Option | Effort | Risk | Impact | Maintenance |
|---|---|---|---|---|
| A. Move row and label mapping into `core:reader` incrementally, starting with what this work touches (feed row, board label, unread label) | Medium, spread out | Low; each move is small and testable once | Removes the duplication where it causes defects | Lower |
| B. Shared ViewModels for both platforms | Very large | High; rewrites iOS state handling and Android dependency injection | Removes it everywhere | Lowest, eventually |
| C. Do nothing | — | — | Double work continues | — |

**Decision: A.**

### 2.3 One flat 19,000-line module with three naming generations

- **Problem:** `ui-next` is a single package where primitives and whole screens sit side by
  side (`Thread.kt` 1,440 lines, `BoardCatalog.kt` 1,430). Names carry three eras: 75 `Reader*`
  symbols, 34 `Next*`, 22 `Orbin*`. Nothing says which is current.
- **Why it matters:** It is hard to tell which component to reuse, and nothing stops a
  primitive from depending on a screen.

| Option | Effort | Risk | Impact | Maintenance |
|---|---|---|---|---|
| A. Split into packages inside the module (tokens, components, screens), add a rule to the architecture script that components may not import screens, rename only what this work touches | Medium | Low; mechanical moves | Clear reuse path, enforced | Lower |
| B. Split into separate Gradle modules | Large | Medium; build and resource wiring | Same, plus faster incremental builds | Lower |
| C. Do nothing | — | — | Drift continues | — |

**Decision: A, done last.**

---

## Section 3: Code quality

No TODO or FIXME markers, 7 lint suppressions in 19,000 lines, and 4 of roughly 300 functions
exceed 150 lines. Error-handling paths were not reviewed line by line.

### 3.1 Design tokens exist but are widely bypassed

- **Problem:** Outside the token files there are 78 hard-coded corner radii, 68 inline text
  sizes, 77 one-off opacity tweaks, and about 50 hard-coded colours (not counting board-icon
  artwork). `RoundedCornerShape(22.dp)` is written out 14 times although the card-radius token
  is exactly 22dp. `scripts/tests/test_design_spec.py` checks token values, not token use.
- **Why it matters:** The next design change means hunting literals across 30 files, and
  near-miss values creep in: radii of 14, 15, 16 and 18dp are all in use.

| Option | Effort | Risk | Impact | Maintenance |
|---|---|---|---|---|
| A. Add a check that forbids these literals outside the token files, with a baseline of today's violations and an allowlist for artwork; replace literals in every file this work touches | Small, then incremental | Low; where a literal equals its token the screenshots stay byte-identical | Stops the growth, shrinks it steadily | Lower |
| B. Replace all of them in one pass | Large | Medium; near-miss values need a design decision each | Clean immediately | Lowest |
| C. Do nothing | — | — | Drift continues | — |

**Decision: A.**

### 3.2 Small components are implemented several times

- **Problem:** Seven pill or badge components: `Pill`, `MediaBadge` and `DestinationPill` in
  `Surface.kt`; `NekoCountPill`, `NekoTintedPill` and `CatalogThumbnailCornerBadge` in
  `BoardCatalog.kt`; `ThreadPill` in `Thread.kt`. The unread count is rendered at four separate
  places in `BoardCatalog.kt` (lines 700, 890, 1112, 1141).
- **Why it matters:** The direct cause of the three unread looks and the double-wrapped chip.

| Option | Effort | Risk | Impact | Maintenance |
|---|---|---|---|---|
| A. One pill component with named variants, one unread-label component, used everywhere; delete the rest | Medium | Low to medium; changes many baselines | One look, one place to change | Lower |
| B. Unify the unread label only | Small | Low | Fixes the visible inconsistency, keeps seven pills | Same |
| C. Do nothing | — | — | Inconsistency stays | — |

**Decision: A.**

### 3.3 Screen entry points take about 50 parameters

- **Problem:** The feed screen takes 49 parameters, `ThreadScreen` 47 and `BoardScreen` 28,
  mostly callbacks. `CatalogCard` is a single 323-line function.
- **Why it matters:** Every new feature adds a parameter both apps must wire by hand, and a
  miswired callback compiles.

| Option | Effort | Risk | Impact | Maintenance |
|---|---|---|---|---|
| A. Group each screen's inputs into a state object and an actions object; split `CatalogCard` by layout. Start with feed and catalog; thread later | Medium | Low to medium; the compiler finds every call site | Fewer wiring mistakes, testable pieces | Lower |
| B. Split `CatalogCard` only | Small | Low | Helps one function | Same |
| C. Do nothing | — | — | Lists keep growing | — |

**Decision: A.**

---

## Section 4: Tests

434 tests in the shared UI module: 348 behavioural (interaction, accessibility, right-to-left,
text scaling, palette contrast) and 86 screenshot tests, with a CI job that fails on any pixel
change. The loading, empty, network-error, server-error, not-found and refresh-failed states
each have a behavioural test and baselines in all three themes.

### 4.1 Screenshots approve whatever is rendered

- **Problem:** Every defect in Section 1 is inside a committed baseline. A screenshot test only
  says "nothing changed"; with 180 images, review by eye is the only gate. The contrast tests
  check token pairs, not text drawn over an image.
- **Why it matters:** The fixes will regress the same way unless a test states the rule.

| Option | Effort | Risk | Impact | Maintenance |
|---|---|---|---|---|
| A. A rule-based test for each fix: board label has exactly one slash each side; grid title meets a contrast minimum over a white image; bottom-bar labels never wrap at any supported text size and width; bar and drawer are both derived from the navigation spec | Small | Low | Each fix is locked in | Low |
| B. Rely on re-recorded baselines | None | High; same blind spot | None | — |

**Decision: A**, each test written before its fix and seen to fail first.

### 4.2 iOS rendering of the redesign is almost untested

- **Problem:** All 180 baselines are Android renders. iOS uses the system typeface where
  Android uses bundled Roboto, so truncation and wrapping can differ. The only native iOS UI
  test is one startup smoke test (`iosApp/OrbinUITests/StartupSmokeTests.swift`) that loads the
  real board directory by design; it failed that way twice on 2026-11-15.
- **Why it matters:** Half the audience sees a render nothing checks, and the one native test
  fails for reasons unrelated to the code.

| Option | Effort | Risk | Impact | Maintenance |
|---|---|---|---|---|
| A. Keep the live smoke test but make it non-blocking; add a second native test that launches with a bundled board directory and captures the five main screens at default and largest text as CI artifacts | Medium | Low to medium; needs a test-only launch flag in the iOS app | Real iOS evidence, stable CI | Some |
| B. Only make the smoke test use a bundled directory | Small | Low; loses the live end-to-end check | Stable CI, no new coverage | Low |
| C. Do nothing | — | — | iOS stays unverified | — |

**Decision: A**, including the test-only launch flag. This reverses the smoke test's stated
choice to run "without app-specific test flags"; that was approved explicitly.

### 4.3 Test and baseline names describe retired designs

- **Problem:** Baselines are named `coral_`, `tiktok_` and `social_ios_`, and tests
  `SocialFeedInteractionTest`, `TikTokSearchInteractionTest`, `FeedCoralChromeTest`. They
  exercise current components under old names.
- **Why it matters:** A reviewer cannot tell whether a diff in `tiktok_settings_dark.png`
  matters.

| Option | Effort | Risk | Impact | Maintenance |
|---|---|---|---|---|
| A. Rename by screen and state as part of this work, since most baselines are re-recorded anyway | Small | Low; renames only | Diffs become readable | Lower |
| B. Do nothing | — | — | Confusion stays | — |

**Decision: A.**

---

## Section 5: Performance

Nothing was measured. The chrome uses almost no blur (2 uses) and no drop shadows, every
content list is keyed, and iOS decodes images to the size of the cell they fill.

### 5.1 Card and grid views download the original image for every still picture

- **Problem:** In the layouts that sharpen thumbnails, each still image loads its thumbnail and
  then the full original file (`cardImageUrl()` in `app-ios/.../Rows.kt:30`;
  `fullResolution = catalogMode.sharpensThumbnails` in `NextFeedScreen.kt:353` and
  `feature/board/.../NextBoardScreen.kt:245`). No check for a metered connection was found on
  this path, and there is no setting to turn it off.
- **Why it matters:** Originals are often several megabytes. Scrolling the default feed pulls
  all of them.

| Option | Effort | Risk | Impact | Maintenance |
|---|---|---|---|---|
| A. A "Sharp previews" setting (Always, Wi-Fi only, Never), default Wi-Fi only, plus a file-size cap above which the thumbnail is kept | Medium | Low; needs a connection-type check on both platforms | Bounded data use, user control | Low |
| B. File-size cap only, no setting | Small | Low | Removes the worst cases | Low |
| C. Do nothing | — | — | Unbounded downloads while scrolling | — |

**Decision: A.**

### 5.2 Benchmarks do not cover the redesign's main screens

- **Problem:** The benchmark journeys are startup, catalog list, catalog grid, thread, files and
  two-pane. The Home feed and Card view are not measured. `benchmark/validate_reports.py` has no
  timing threshold. The baseline profile is generated from the same journeys, and the numbers on
  record predate the redesign.
- **Why it matters:** There is no evidence on whether the redesign made scrolling slower.

| Option | Effort | Risk | Impact | Maintenance |
|---|---|---|---|---|
| A. Add Home feed (card) and catalog card journeys to the benchmark and baseline profile, record fresh numbers, and fail CI on a set regression | Medium | Medium; emulator timings are noisy | Evidence plus protection | Some |
| B. Same journeys and fresh numbers, no gate yet; decide on a gate once several runs show the variance | Medium | Low | Evidence now, protection later | Low |
| C. Do nothing | — | — | Performance stays unknown | — |

**Decision: B.**

### 5.3 Minor rendering hygiene

- **Problem:** 11 of 16 lazy lists declare no content type. None of the 12 gradient brushes is
  cached, including the one drawn over every grid cell.
- **Why it matters:** Probably small; unmeasured.

| Option | Effort | Risk | Impact | Maintenance |
|---|---|---|---|---|
| A. Fix in the files this work already touches, and confirm with the new benchmark | Small | Low | Small, measurable once 5.2 lands | None |
| B. Do nothing | — | — | — | — |

**Decision: A**, lowest priority.

---

## Proposed implementation order

Each step is its own pull request, with the rule-based test written first and seen to fail
before the fix.

| Step | Pull request | Decisions | Visible change |
|---|---|---|---|
| 1 | Board label and grid legibility: one shared board-label function in `core:reader` used by both apps and the UI; stronger grid gradient with a contrast test; distinct Theme and True black icons | 1.1, 1.2, 1.5 (icon), start of 2.2 | Yes, small |
| 2 | Shared navigation spec; bar and drawer render from it; delete the deprecated stub | 2.1 | None |
| 3 | Single navigation on phones: Boards, Feed, Library (Watched, Saved, Downloads), Settings; drawer only on wide windows; one name for the feed | 1.3 | Yes, large |
| 4 | Large text: icon-only bottom bar when labels don't fit; app bar keeps two actions; layout button shows the current layout | 1.4, 1.5 (layout button) | Yes |
| 5 | One pill component and one unread label; token check with a baseline; rename baselines and tests by screen and state | 3.1, 3.2, 4.3, 1.5 (unread) | Yes, most baselines re-recorded |
| 6 | State and actions objects for feed and catalog; split `CatalogCard`; content types and cached brushes | 3.3, 5.3, more of 2.2 | None |
| 7 | "Sharp previews" setting (default Wi-Fi only) with a file-size cap | 5.1 | Yes |
| 8 | iOS native test with a bundled board directory behind a test-only launch flag; live smoke test made non-blocking | 4.2 | None |
| 9 | Home feed and card journeys in the benchmark and baseline profile; fresh numbers, no gate | 5.2 | None |
| 10 | Package split inside `ui-next` and the components-may-not-import-screens rule | 2.3 | None |

Decision 4.1 (a rule-based test per fix) is spread across steps 1 to 4.

## Constraints

- **Build environment:** the cloud session used for this review has Java but no Android SDK.
  If the SDK cannot be installed there, screenshot baselines have to be re-recorded on a
  developer machine or in CI, and compilation is checked only by CI.
- **One branch at a time:** that session pushes to a single branch, so the pull requests go in
  sequence, each merged before the next starts.

## Open questions

1. Is the order above approved, or should it change?
2. Ten sequential pull requests, or fewer and larger ones? Ten is recommended: steps 3 and 5
   each change dozens of baselines, and mixing them makes the image diffs unreviewable.
3. May step 1 start, beginning with the Android SDK setup check?
