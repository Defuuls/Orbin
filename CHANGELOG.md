# Changelog

All notable changes to Orbin are documented here. The format is based on
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and the project adheres to
[Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Fixed
- iOS: full-screen native and WebM videos now have an accessible mute/unmute button, defaulting to muted and remembering the last audio choice for the app session.

## [175-Oca] - 2026-10-24

### Fixed
- Android: importing a backup cannot suppress an enabled app lock through onboarding settings.
- iOS: relocking cancels pending backup operations and dismisses their system sheets; stale callbacks cannot restore data.
- iOS: oversized media downloads are rejected before copying native data into a Kotlin byte array.


## [174-Jicama] - 2026-10-23

### Added
- iOS: a first-run board picker, the same one Android shows.
- iOS: each board's media wall, opened from its catalog.
- iOS: home-screen quick actions for Your Feed, Saved Media and Browse Boards.
- iOS: picture in picture when you leave the app while a video plays.
- iOS: safe mode after two startup crashes in a row, with crash details you can share.

### Changed
- iOS indents thread replies and counts them the way Android does, saved threads included.
- iOS applies the built-in content filter to boards, catalogs and threads, as Android does.
- Feed, search, thread layout, downloads and onboarding now share one implementation on Android and iOS.
- Sites behind a proof-of-work gate are solved once per site instead of once per image on a fresh session.
- Android targets SDK 37.

### Fixed
- A board that fails during search no longer hides the other boards' results.
- iOS shows progress for queued downloads.
- Leftover sync logins from earlier versions are removed from the device.


## [173-Sunchoke] - 2026-10-23

### Removed
- Nearby device sync over Wi-Fi, Bluetooth and the relay, with its pairing screen and QR codes. It didn't connect reliably between Android and iOS.
- WebDAV sync and its setup QR code. Reading history and bookmarks now stay on each device; any saved WebDAV login is no longer used.
- Camera, Bluetooth and local network permissions, which only sync used.


## [172-Fiddlehead] - 2026-10-22

### Added
- Nearby devices has its own screen, opened from Settings: live status, this device's Wi-Fi address to compare with the other device's, a step-by-step pairing guide, your paired devices, troubleshooting tips and a copyable activity log.

### Fixed
- Nearby pairing codes now carry every local network address of the device that made them, so the other device no longer gets stuck trying an address it can't reach.
- Leaving Orbin while a pairing code is showing now says the code was cancelled instead of it silently stopping working.


## [171-Celeriac] - 2026-10-21

### Added
- Nearby devices: a Status row with a progress bar shows each step of pairing and syncing (connecting over Wi-Fi, Bluetooth or the relay, checking the pairing, sending, merging), then the result.

### Fixed
- Nearby sync failures now say which connection failed and what to check, in red, instead of one generic message, and Sync now stays busy until every device has finished.
- iOS: Orbin asks for Local Network access as soon as nearby sync starts, so an iPhone no longer silently refuses an Android device's connection.


## [170-Salsify] - 2026-10-21

### Added
- QR code pairing: pair nearby devices by scanning a QR code instead of typing the one-use pairing code.
- Bluetooth LE sync (opt-in): sync with nearby devices over Bluetooth when they are not on the same Wi-Fi, including between Android and iOS, still end-to-end encrypted with the pair key.
- Sync setup by QR code: copy WebDAV sync settings to another device by scanning a QR code.
- Internet sync through a self-hostable relay (relay/, docs/relay.md): paired devices sync over the Internet through a relay you run yourself; frames stay end-to-end encrypted, so the relay never sees plaintext or keys. There is no public relay.
- iOS: show and scan pairing QR codes, Bluetooth sync, relay server and sync setup QR settings.

### Changed
- Joining and Sync now try local Wi-Fi first, then Bluetooth, then a configured relay, falling back to another route if the first fails.
- Phone hotspot interfaces now count as the local network, so two phones on mobile data can sync through one phone's hotspot.
- Bluetooth pairing between Android and iOS needs this version on both devices.


### Changed
- Nearby Bluetooth advertisements now carry the beacon in a service UUID so iOS can advertise and read them; Bluetooth pairing needs this version on both devices.

### Added
- Nearby devices (Android): **Add a device** now shows the pairing code as a QR code, and **Join another device → Scan QR code** reads it with the camera. Copying and pasting the code still works.
- Nearby devices: optional **Also use Bluetooth** pairs and syncs over Bluetooth LE when the devices aren't on the same Wi-Fi, with the same end-to-end encryption. A phone's own hotspot now also counts as a local network.
- Nearby devices: optional **Relay server** setting for pairing and syncing over the Internet through a self-hosted Orbin relay (new `relay/` server, Docker image, see `docs/relay.md`). The relay only forwards end-to-end encrypted frames, needs no accounts and stores nothing; the pairing QR code carries its URL. Empty by default.
- iOS: pairing and sync setup QR codes are shown and scanned with the camera, and **Also use Bluetooth** syncs over Bluetooth LE, including with Android devices.
- WebDAV sync: **Set up another device** shows the folder and login as a QR code, and **Copy setup from another device** scans it, for syncing over the Internet or mobile data without retyping the password.

## [169-Kohlrabi] - 2026-10-20

### Added
- Nearby devices: pair Android and iOS devices with a one-use ten-minute code and sync reading history, positions, bookmarks and watched threads directly over local Wi-Fi, end-to-end encrypted, with no account, server or Internet connection.
- Trust a paired device for automatic sync: when both devices trust each other and share the same local network, they sync on their own while Orbin is open. New pairings stay manual-only.

### Changed
- iOS: WebDAV sync passwords move into device-only Keychain storage, migrated automatically from the old location.

### Fixed
- Android: corrupted or deleted settings no longer switch off a saved app lock; Orbin recovers them locked.
- Sync now no longer silently does nothing when it collides with the other device's automatic sync.


## [168-Romanesco] - 2026-10-19

### Fixed
- Android and iOS now draw with the same forest-green palette. Android had kept Material's default purple for its accents and bars while its containers were green, so screens mixed the two; every Material control now takes its colour from Orbin's palette.
- One type scale everywhere: Material controls and Orbin's own text use the same fonts, sizes and weights (the platform's system font on each), and the last hand-sized labels use it too.


## [167-Guava] - 2026-10-19

### Added
- iOS: Settings links to Orbin's privacy policy and to the project's issues page for contact and feedback.

### Changed
- Updated Kotlin to 2.4.21, Okio to 3.18.2, the Gradle wrapper to 9.8.1 and the AndroidX libraries.

### Fixed
- iOS: lists scroll smoothly again. Video thumbnails are read natively instead of through a web view each, only one catalog preview loops at a time, and pictures no longer redraw on every frame while scrolling.
- iOS: pulling to refresh fetches the page from the site instead of a copy up to a minute old.


## [166-Dragon-Fruit] - 2026-10-13

### Added
- Save a whole thread as a page: "Save page" in a thread's Files view writes a .zip with an index.html that opens in any browser, its stylesheet, and every image, video and thumbnail in the thread (Downloads/Orbin on Android, the share sheet and Orbin's folder in Files on iOS).


## [165-Currant] - 2026-10-10

### Added
- Sync thread progress between devices: set a WebDAV folder, username and password under Settings > Sync, and read threads, where you stopped reading, and bookmarks (with watch state) follow you across Android and iOS.


## [164-Cranberry] - 2026-10-05

### Changed
- Dark mode now sits on a deep aubergine background with matching tinted surfaces instead of plain black; the AMOLED option still uses true black.


## [163-Jabuticaba] - 2026-10-05

### Added
- Gallery top bar auto-hide after 3 seconds of inactivity, for fully uninterrupted media viewing.

### Changed
- New cinematic night theater brand showcase SVG for the app redesign.


## [162-Cantaloupe] - 2026-10-01

### Changed
- Android: the entire UI is migrated to Material Design 3 — screens, scaffolds, buttons, controls and color, type and spacing tokens now follow M3 components and guidelines.


## [161-Blueberry] - 2026-09-30

### Changed
- Android: media downloads run through WorkManager instead of the system DownloadManager. They keep going when Orbin is backgrounded or restarted, write into Downloads/Orbin as before, and no longer need a broadcast receiver for completion.
- Android: feed, boards and thread text use denser type and spacing tokens (tighter gutters, denser body and footnote sizes), so list rows and cards pack a little closer without changing the layout structure.
- Android: feed and boards empty states, search, and thread counts use localized strings in the ui-next design system instead of hard-coded English.
- Android: sliders announce their value and percent to TalkBack, and support the accessibility set-progress action.

### Fixed
- Android: copying an image to the clipboard clears it after one minute, so the temporary URI grant does not linger. Background clipboard reads are already blocked by the OS on newer Android; this clears the grant on every supported version (minSdk 31).
- Android: notification and shortcut deep links are parsed through a typed IntentRoute with length limits on provider, board and title extras, instead of reading those strings unbound.


## [160-Blackberry] - 2026-09-29

### Fixed
- Android: one video refused by the media CDN (HTTP 429) no longer blocks every video. Only that video waits, and only for 15 seconds unless the server asks for longer (at most a minute); the whole server is held back only when several videos are refused within 30 seconds. When the wait ends the video loads again on its own. Before, returning to the app after a while could leave every video showing "rate limited" for five minutes.
- Android: reopening a thread now loads its latest replies. It showed the copy kept from the last visit, up to half an hour old, until pulled to refresh; that copy now only fills the screen while the thread loads. Watched-thread checks always ask the site too, so a copy held while the app was open can no longer hide new replies from them.


## [159-Apricot] - 2026-09-28

### Added
- Android: Galaxy Z Fold8 and Fold8 Ultra layouts. Unfolded, the Fold8's 4:3 screen held landscape and the Ultra's near-square screen either way up show a board's catalog beside the open thread (two panes from 800dp wide, down from 840dp).
- iOS: Settings shows a **Version** row with the whole release name, as Android names it ("159-Apricot"), and the TestFlight build number under it. TestFlight's own version field only takes numbers ("159").
- TestFlight builds come with **What to Test** filled in from the changelog: the release's own notes, or what is not yet released for a build made between releases.

### Changed
- Feed and catalog column choices apply only to a window at least 600dp both ways. A foldable's cover screen turned sideways (a Fold8's is about 751 x 475dp, a Fold8 Ultra's about 960 x 411dp) now stays one column instead of taking the unfolded column count.
- iOS: videos in the feed no longer play by themselves; they show their picture and a play badge until the thread is opened, as on Android. Board catalogs keep their looping previews.
- iOS: on iPad, a thread sits beside its catalog only in a window with room for both (at least 800 x 600 points), such as an iPad Pro 11-inch (M4) full screen either way up; a narrow Split View, Slide Over or Stage Manager window shows the thread alone.

### Fixed
- Android: folding or unfolding no longer restarts the screen when the cover and inner displays use different densities; the app lays itself out again in place.
- iOS: the feed, catalogs and threads keep their scroll position when you go into a thread or the media viewer and come back, and a recently read thread reopens where you left it.


## [158-Yuzu] - 2026-09-27

### Added
- iOS: on iPad, a thread opens beside its board's catalog.
- iOS: videos in board catalogs play as muted, looping previews.
- iOS: the video player can loop and continue in picture-in-picture, with a loop toggle.
- iOS: saving media keeps downloading in the background, and finished downloads are picked up when Orbin is relaunched.

### Fixed
- iOS: the app no longer crashes on launch after updating to 157. 157 moved the shared database to schema 8 (new indexes), and the iOS app had no step to upgrade the copy it already had, so it refused to open it. Both platforms now share the migration steps, so iOS upgrades its copy in place, keeping history, watched threads and downloads; a test fails if the schema changes again without a step.


## [157-Xigua] - 2026-09-27

### Added
- Android: launcher shortcuts. Long-press the Orbin icon to jump straight to Feed, Boards or Downloads.
- Android: videos can keep playing in picture-in-picture when you leave the app, and otherwise pause when it goes to the background.
- Android: watched-thread alerts have their own **Watched threads** notification channel, and tapping one opens the thread.
- Android: a download that finishes while Orbin is in the background still updates its entry in Downloads.

### Changed
- Android: refined haptic feedback on controls, and database indexes (a migration to schema 8) for quicker lookups.

### Fixed
- TestFlight build numbers always increase: they are the version code and the upload's date and time in Chicago time. A manual build used the workflow's own run count, which is far below a release's, so TestFlight listed an older release build as the latest (174.189 above 174.11).
- TestFlight uploads no longer create an Apple Development certificate on every run; after a dozen builds Apple's certificate limit stopped them. The archive is built unsigned and signed once at upload with the cloud-managed distribution certificate.
- iOS: images in a thread are sharp too. They showed the site's small thumbnail stretched to the post's width, as the feed's cards did before 156.


## [156-Watermelon] - 2026-09-27

### Added
- Tablets (iPad and Android): Settings → **Catalog columns** picks how many columns a board's catalog shows, 2–8, starting at 4. Elsewhere, and in a narrow split-screen window, the catalog still fits its tiles to the width. Carried in backups.
- iOS: board catalogs have Android's sort control (Bump, Created, Replies, Images, Latest, A-Z), starting in bump order on each board.
- iOS: the feed and the Boards list show one site at a time with Android's site switcher (4chan | BBW Chan) at the top, instead of every site's boards and threads mixed together. The choice is shared by both screens and remembered, in the same setting Android uses.
- iOS: the app runs natively on iPad (it was an iPhone app shown at iPhone size) in every orientation, Split View and Stage Manager. The feed shows 1–4 columns on an iPad's full width, chosen in Settings → **Feed columns** as on Android tablets, and one column in a narrow window.
- BBW Chan's boards show a symbol for their topic in the Boards list (a camera for BBW Real, a brush for BBW Drawn, a paw for BB Furries and so on), with the board's path moved to the line under its name. Other boards keep their path on the tile.

### Fixed
- iOS: feed and catalog cards show still images at full resolution instead of stretching the site's small thumbnail, which looked blurry on big cards (one or two feed columns on iPad).
- iOS: a site that answers with no boards at all now counts as unreachable instead of silently vanishing, and the Boards title gives the reason each site couldn't be reached (an HTTP status or network error).
- iOS: 4chan no longer disappears on networks that block it through DNS, such as some mobile carriers. The app now uses encrypted DNS (Cloudflare) for every request, as the Android app does.
- iOS: a site that can't be reached is named under the Boards title instead of silently missing from the list, and is tried again when you reopen Boards.


## [155-Ugli] - 2026-09-26

### Changed
- Releases attach only the APK and its checksum. The R8 mapping file, used to decode crash reports, is kept as a private build artifact for 90 days instead of a release download.


## [154-Tangerine] - 2026-09-26

### Changed
- The middle tab is now **Downloads**, listing everything you saved with its progress and a retry for failed files. It replaces the Media tab and its All media wall; each board's own media wall stays, reached from its catalog, and its **Saved ›** opens Downloads.


## [153-Strawberry] - 2026-09-26

### Added
- **Cover violent media**, on by default in Settings. Images and videos in posts whose subject, text or filename mentions violence, accidents or graphic footage (fight, car crash, bodycam, nsfl and similar) show a spoiler cover until you open them, and never autoplay. When a thread's opening post is labelled, every file in the thread is covered. It reads text only; clearly labelled gore is still hidden outright.


## [152-Raspberry] - 2026-09-26

### Added
- Foldables and tablets are detected automatically. A foldable's front screen shows the feed in one column; unfolded, Settings → **Feed columns when unfolded** picks 1–3 columns. Tablets get **Feed columns**, 1–4. Both start at 2, and the setting only appears on those devices (#583).


## [151-Quince] - 2026-09-26

### Changed
- The feed is one column of full-width cards on a phone: each thread's image at full width, whole down to a 9:16 portrait, with up to eight lines of the opening post under it. It was a two-column grid with a two-line excerpt. Tablets fit more columns; board catalogs keep their grid (#581).
- The feed shows the opening post as readable text, with line breaks kept and characters such as apostrophes shown instead of HTML codes like `&#039;` (#581).


## [150-Papaya] - 2026-09-26

### Changed
- Nothing plays in the feed any more. GIFs and videos show their thumbnail until you open the thread; the feed used to play the card it was centred on (#579).


## [149-Orange] - 2026-09-26

### Added
- Orbin checks for a new release when it opens, at most once a day, and offers it in a popup. Update downloads the release, checks it against its published SHA-256 checksum and Orbin's signing key, and opens Android's installer to confirm. Settings → Check for updates offers the same, and "Tell me about new releases" turns the launch check off.

### Fixed
- The Boards list is sorted A–Z by board. It showed boards in the order the site lists them, which isn't alphabetical (#576).


## [148-Nectarine] - 2026-09-25

### Changed
- The feed is sorted by board, A–Z, under a heading for each board, with the most recently active threads first within each board. It was one list ordered by activity across every board (#560).


## [147-Mango] - 2026-09-25

### Added
- Double tap the left or right half of a video to skip back or forward 5 seconds. Repeated double taps add up ("+15s"), and screen readers get matching skip actions (#547).
- Media gestures, with no settings behind them: double tap an image to zoom on that spot; long-press any image or video, in the viewer or on the All media wall, to Save, Share or Copy link; pull past the first or last item to close the viewer; a scrub bubble shows the time while dragging a video's progress bar (#553).
- The feed card in view plays its media in place: a video loops silently and a GIF animates. Only one plays at a time, never a spoilered one, and tapping still opens the thread (#555).
- Longer videos resume where you left off when reopened, until Orbin closes (#555).
- Every board catalog has a Media wall action that opens that board's files as a wall (#556).
- Saved › under the All media title opens your downloads, and Search threads › under the Boards title opens search (#557).

### Changed
- The bottom bar is Feed · Media · Boards, and Settings opens from a gear in the Feed header. Boards, catalogs and All media are reachable again once you follow a board, and switching tabs no longer stacks screens (#548).
- Settings is one short list in two untitled cards: Hide NSFW boards, Theme, True black and App lock; then Clear local activity, Clear image cache, Check for updates, Export data and Import data (#551, #557).
- First run is one step: pick the boards to follow, then start browsing (#551).
- Video is simpler: play/pause, progress and mute. Clips up to 30 seconds loop, turning the phone sideways goes fullscreen, and a tap toggles the controls immediately. Video starts muted and an unmute carries over until Orbin closes (#551, #555).
- Search is one field over the boards you follow, and the feed is one list with the newest activity first (#552).
- The feed uses the same adaptive grid as board catalogs, so a phone shows two columns; very tall media is letterboxed at 4:5; every title starts on the same 16dp line; loading shows skeleton cards (#554).
- The thread header is a Posts / Files switch and a watch bell, with Download all and Save links as text above the file grid (#556).
- Board catalogs refresh by pulling down, and a catalog that fails to load says why: offline, too slow, rate-limited or gone (#556).
- Clear local activity confirms with a second tap in place instead of a dialog (#557).
- Removed the floating name bars and the thread reader's title bar and scroll arrow; jump buttons sit on a solid pill and grid cards inset their text evenly (#546).

### Accessibility
- Screen titles and section headers are marked as headings, spoilered media is labelled "Spoiler", and board labels on media use white text on a dark scrim (#554, #557).
- The offline banner floats over the screen instead of pushing the layout down (#557).

### Removed
- The command palette, the settings search index and the feed filter it drove (#549).
- About thirty stored preferences that nothing read or nothing could edit any more, now fixed at their defaults. Older backups that contain them still import (#549, #550, #551).
- The Color scheme, Text size, Mute by default, Downloads folder and In-app updates rows; the feed's sort menu; Search's filters and saved searches; onboarding's extra steps and favorite-board stars (#551, #552).


## [146-Lemon] - 2026-09-25

### Changed
- The subscribed feed only refreshes when you pull down. Returning to it keeps the feed as you left it instead of reloading every board, and the header's Refresh button is gone (#541).
- Removed the Library section from Settings, including its Boards, All media and Commands entries. Search and Downloads now sit in Privacy & Data, next to the image cache (#542).
- Removed the unused "personalized home feed" preference. It had no setting and no effect; older backups that include it still import (#542).


## [145-Kiwi] - 2026-09-24

### Changed
- Removed the List and Images layouts and the layout switcher; the subscribed feed and board catalogs now use the grid only (#538).
- Grid cards show the OP media uncropped at its own aspect ratio, loading the full-resolution image instead of a centre-cropped thumbnail (#538).


## [144-Jackfruit] - 2026-09-24

### Fixed
- Loaded the full catalog from each board: board paging now requests the whole catalog up front, and the subscribed feed defaults to all threads per board instead of twelve (#535).

### Changed
- Migrated the previous twelve-thread feed default to All threads per board; other explicit limits are kept (#535).


## [143-Honeydew] - 2026-09-23

### Fixed
- Stopped thread posts from requesting intrinsic height of HorizontalPager media, which crashed Compose with IllegalStateException on multi-attachment posts.

### Changed
- Restricted media playback to the thread reader and streamlined related settings.


## [142-Grape] - 2026-09-21

### Added
- Two new pale matte color themes: Banana (warm cream and harvest gold) and Apple (pale celadon and forest green).
- Exposed Color scheme selection in Display & Media settings with search index integration.

### Changed
- Streamlined feed interface by removing the floating top feed bar during scroll.
- Floating bottom navigation bar now automatically disappears when scrolling down and returns on scroll up by default.
- Defaulted Feed Grid and Images layouts to single-column full-size media presentation.


## [141-Fig] - 2026-09-21

### Added
- Cosmic eggplant planetary app icon featuring stylized orbital rings and deep space hues (#524).

### Changed
- Redesigned the UI shell with flat, matte Material 3 Expressive styling seeded from a soft eggplant palette (#526).
- Upgraded core toolchain and dependencies: Android Gradle Plugin 9.4.1, Media3 1.11.1, Robolectric 4.17, and Dependency Analysis 3.19.2 (#519, #520, #521, #522).

### Security
- Pinned patched runtime versions for Bouncy Castle and Wire to resolve upstream advisory alerts (#525).


## [140-Elderberry] - 2026-09-18

### Changed
- Removed the Go button from the bottom chrome. Search, Downloads and the command sheet now live in Settings › Library, alongside Boards and All media, leaving Feed and Settings the only things competing for the strip (#515).
- Removed settings rows that could no longer be reached, so the list and its search index describe only what the app actually offers (#514).

### Fixed
- Corrected the README, which advertised what was new in an old release beside a current one. The release manifest now owns that line, so it moves with each release or disappears rather than going stale (#512, #513).


## [139-Date] - 2026-09-18

### Changed
- Reduced the settings list to three categories — General, Display & Media, Privacy & Data — and to the rows most people actually reach for. Preferences behind rows that are no longer listed keep their stored values (#508, #509).
- Reduced the permanent bottom chrome to Feed and Settings. Boards and Media are reached from Command and now carry the context rail, like Search and Downloads (#508, #509).

### Fixed
- Restored the bottom chrome on Boards and Media, which the two-tab change had left with no rail at all on their list, loading, error and empty states (#509).
- Restored the Thread scroll arrow and Image cache usage rows, which were grafted onto settings headings that no longer existed and had stopped appearing (#509).
- Dropped settings search entries for rows the list no longer offers, so a search can no longer land on a setting that is not there (#509).



## [138-Cherry] - 2026-09-17

### Changed
- Made ui-next the authoritative iOS visual layer so legacy imageboard themes no longer override app chrome (#505).
- Kept legacy theme light/dark intent while preserving the explicit Android platform-theme option (#505).
- Stabilized hosted CI macrobenchmarks by gating deterministic cold and warm startup measurements (#505).


## [137-Banana] - 2026-09-17

### Added
- Added list, grid, and photo feed layouts with search, refresh, grouped boards, and sorting (#502).
- Added searchable board discovery and following controls (#502).

### Changed
- Aligned cards, typography, spacing, navigation motion, settings, threads, and controls with the new design (#502).
- Added a persisted iOS/Android platform theme setting with distinct visual controls (#502).


## [136-Apple] - 2026-09-17

### Fixed
- Cleared Boards compact-title overlap and restored Feed DestinationPill on loading / error / empty states (#493).

### Changed
- Softened Feed/Boards motion and presentation toward Apple-like transitions and chrome (#494).
- Extended Apple Next consistency across remaining destinations beyond Feed/Boards (#495).
- Switched forward release codenames to the fruit pool from v136 (#496).
- Replaced deferred Material leftovers (Snackbar/Scaffold, Slider, PullToRefresh, spinner, Modern* shells) with Next controls (#497).
- Migrated VideoPlayer chrome from Material3 to Next (#498).


## [135-Rhinovirus] - 2026-09-17

### Added
- Introduced an Apple-inspired Orbin Next visual language: calm neutrals with one blue accent, OLED-black dark surfaces, large titles, generous spacing, and continuous large radii (#488).
- Added a floating DestinationPill for Feed / Boards / Media / Settings, with Command via a refined Go sheet, and a new Boards destination screen (#488).
- Added Next dialog, empty/loading/error, and form primitives (NextConfirmDialog, NextLoading / NextEmpty / NextError, NextTextField / NextToggle / NextSelect) for consistent chrome (#490).

### Changed
- Restyled primary Next screens (Feed, Boards, All Media, Thread, Settings) and Command onto the new design tokens and surfaces (#488).
- Adopted compact collapsing title bars on primary Next screens so chrome stays cle…38481 tokens truncated…tring().padStart(2, '0')}"
}

/** "playing,position,duration,muted" as the WebM page's state script reports it, or null if the page is not ready. */
internal fun parseWebMState(text: String): VideoState? {
    val (playing, positionText, durationText, muted) =
        text.split(',').takeIf { it.size == WEBM_STATE_FIELDS }
            ?: return null
    if (playing !in listOf("0", "1") || muted !in listOf("0", "1")) return null
    val position = positionText.toDoubleOrNull()?.takeIf { it.isFinite() } ?: return null
    val duration = durationText.toDoubleOrNull()?.takeIf { it.isFinite() } ?: 0.0
    return VideoState(playing = playing == "1", muted = muted == "1", position = position, duration = duration)
}

/** Last viewer audio choice for this process; inline previews never use or change it. */
internal object SessionAudio {
    var muted: Boolean = true

    fun toggle(current: Boolean): Boolean = (!current).also { muted = it }
}

/** Apply the latest choice even if a toggle happened before the WebKit page finished loading. */
internal fun webMStateScript(muted: Boolean): String =
    "(function(){var v=document.getElementById('v');if(!v)return null;v.muted=$muted;" +
        "return (v.paused?'0':'1')+','+v.currentTime+','+v.duration+','+(v.muted?'1':'0')})()"

private const val WEBM_STATE_FIELDS = 4
private const val SKIP_SECONDS = 10.0
private const val SECONDS_PER_MINUTE = 60
private const val SCRIM = 0.4f
private val PLAY_SIZE = 64.dp
