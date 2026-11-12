# The iOS app

Orbin's iOS app shares everything below the platform with Android through Kotlin Multiplatform:
the models (`core:model`), the domain, the image-board providers and their Ktor networking, the
design system and every `ui-next` screen. What is iOS-only is small:

| Part | Where | What it does |
| --- | --- | --- |
| `:app-ios` | `app-ios/` | The iOS composition root (Kotlin). Builds the providers over Ktor's Darwin engine, opens the shared database, holds navigation and loading state (`Browser`), and draws the shared screens. Linked into the app as the static `OrbinKit` framework. |
| Xcode project | `iosApp/` | A UIKit application/scene delegate that hosts `MainViewController()` full screen with controller-owned status-bar appearance, plus the app icon, launch mark (`LaunchMark` from AppIcon), launch background color, and `Info.plist`. Generated from `iosApp/project.yml` by [XcodeGen](https://github.com/yonaskolb/XcodeGen); the `.xcodeproj` is not committed. |

## Simulator tests

CI starts booting an available iPhone simulator at the beginning of the job, waits for its
system services just before the suites run, and shares that device between the app and storage
suites. Shared `:app-ios` logic tests run on the Android host in the CI unit-test job; the
simulator job keeps only tests that need UIKit, AVKit, or the real storage driver.

On a Mac, boot a device in Simulator and run the suites with its UDID in
`ORBIN_IOS_TEST_DEVICE` (preferred, and what CI uses) so the value is read when the test
task runs and does not bust Gradle's configuration cache:

```bash
export ORBIN_IOS_TEST_DEVICE=YOUR_BOOTED_SIMULATOR_UDID
./gradlew :app-ios:iosSimulatorArm64Test :storage:iosSimulatorArm64Test
```

The `orbin.iosTestDevice` Gradle property still works for local runs:

```bash
./gradlew :app-ios:iosSimulatorArm64Test :storage:iosSimulatorArm64Test \
  -Porbin.iosTestDevice=YOUR_BOOTED_SIMULATOR_UDID
```

Find the UDID with `xcrun simctl list devices available`. Either form selects that device and
disables standalone mode; Gradle leaves its lifecycle to you. Ordinary tests without an override
run in standalone mode with a placeholder device name. The lookup of an available simulator is
not done at configuration time, because starting `xcrun` then breaks Gradle's configuration cache.

## Reader behavior

The shared reader follows the [normative design](design-system/README.md). Physical iPhone and
iPad verification is tracked separately in the [reader device checklist](ios-reader-checklist.md);
the checklist is pending and simulator coverage does not establish physical-device behavior.

- Home, the start screen as on Android, with Following and For You feeds, source selection,
  per-board thread limits and the permanent filter. A board that fails to load leaves the others;
- boards from every site (the same two the Android app ships), one site at a time behind
  Android's site switcher (remembered in the same `activeProviderId` setting), with one site
  being down never hiding the other's boards, and a switch on each to follow it. A site that could not be reached
  is named under the Boards title and tried again when you reopen Boards;
- Search, from the navigation drawer: one query over the catalogs of every board you follow, on every
  site. It matches as Android's does (`matchesSearch` in `:core:model`, after the permanent filter),
  on the same shared `ui-next` screen. A board that fails to load leaves the others, and the query
  and results are still there when you come back from a thread;
- a board's catalog, with thumbnail list and grid modes. **Catalog view** in Appearance and the
  catalog overflow selects List or Grid, remembered per device. Grid columns follow the pane
  width divided by 120dp, rounded down with a minimum of one; there is no column-count setting;
- a thread's posts drawn by the same renderer as Android (`core:ui`'s `PostCommentText`):
  greentext, spoilers that reveal on tap, quotes that jump to the post they quote, and links
  that open in the browser when they pass the same https check Android uses;
- a full-screen viewer for a thread's files: swipe between them, pinch or double-tap to zoom.
  Video and audio play on their page in the system player (AVKit), with app controls for play, seek and mute,
  pausing when you swipe away. That covers MP4, QuickTime and the common audio
  formats. WebM plays inside the viewer through WebKit on iOS 17.4 and later; older iOS versions
  offer the browser link. Native and WebM viewer videos start muted and remember the last audio
  choice until the app process ends; inline previews always stay muted;
- watching a thread (the thread screen's watch action bookmarks it) and a reading history that
  marks the threads you have opened as read in the catalog. Both live in the same Room database
  as Android's (`:storage`), opened through the bundled SQLite driver in Application Support;
- unread counts on watched threads in their board's catalog, as on Android. `WatchedThreads`
  refreshes them while the app is open (at launch, on changing destinations and when the app
  comes to the front, at most once every five minutes), and iOS also wakes the app now and then in the
  background (`BackgroundRefresh`, a `BGAppRefreshTask`) to do the same, as Android's watch worker
  does. New replies are posted as a notification per thread; iOS asks to notify the first time you
  watch a thread. Opening a watched thread clears its count, and the thread screen offers a jump to
  the first reply you had not seen;
- saving from the viewer (Save), and **Downloads** listing every save with its progress, a
  retry for failures and Clear. Photos, GIFs and the videos iOS plays go into the Photos library
  (add-only access); everything else, WebM included, goes to Files › On My iPhone › Orbin, by board
  then thread. The list is the shared `downloads` table; the rules are Android's (https only, and
  nothing the permanent filter catches);
- backup and restore (Settings › Export data / Import data) in Android's format, through the share
  sheet and the document picker, so a file from either platform restores on the other. As on
  Android, an import merges and never restores the app lock. A backup containing an old color
  theme is refused before any data is applied, with the message "This backup was made by an
  older version of Orbin and cannot be restored.";
- encrypted DNS for every request, as Android's DNS-over-HTTPS, so a network that blocks a site
  through DNS (some mobile carriers do) does not hide it. The UIKit app delegate registers a
  process-wide applicator (`NWParameters.PrivacyContext.default`, iOS 16+) and applies Android's
  default at launch: Cloudflare, with system-DNS fallback allowed. Once settings load, the saved
  resolver (Cloudflare, Google, OpenDNS or NextDNS) and **Strict encrypted DNS** are applied the
  same way Android applies them. When strict is on, lookups fail instead of falling back; when
  it is off, the system may use ordinary DNS if DoH fails. iOS cannot show Android's fallback
  warning, because it cannot observe that the system resolver was used;
- iPhone and iPad, in every orientation on iPad and in Split View and Stage Manager windows. The
  screens size themselves to the window. Home uses one full-viewport vertical page on every
  device, with natural media aspect ratios and safe captions on a black ground in every theme.
  Its app bar sits above the feed; the Following and For You controls and media actions remain
  over the media. Saved and board directories keep their adaptive cover grids. A thread opened
  from a catalog sits beside it when the window is at least 800 points wide and both sides are
  at least 600 points (`showsTwoPanes`): an iPad Pro 11-inch (M4, 834 x 1210pt) full screen either
  way up, but not a narrow Split View, Slide Over or Stage Manager window. The shared pane rule
  gives the catalog five twelfths of the width after the 1dp divider, capped at 420dp; the thread
  takes the rest. Both panes have app bars, and opening a thread retains the catalog's scroll
  position. Back in the thread pane closes the thread and shows "Select a thread"; Back again
  leaves the board. The app is native arm64 code on every
  Apple-silicon iPad, and ProMotion iPads run it at up to 120Hz. The share sheet opens as a
  popover in the middle of an iPad's screen;
- the system edge swipe to go back, returning to pages as they were rather than reloading them.

- Settings, below a divider in the navigation drawer, on the shared `ui-next` settings screen:
  hide NSFW boards (from the boards list, the feed and search, as on Android), cover violent
  media (on by default: a spoilered file, or one whose post is labelled as violent, waits behind a
  cover in the feed, the catalog, the thread and the viewer until tapped), the theme
  (System, Light or Dark), AMOLED black and Catalog view (List or Grid), the app lock,
  clearing the reading history (two taps, as on Android) and clearing the image cache;
- the app lock, as on Android: with it on, the app is locked at launch and whenever it has been
  in the background, and hidden in the app switcher. It unlocks with Face ID or Touch ID, or the
  device passcode, where Android asks for strong biometrics only, and then confirms a this-device-only
  Keychain item whose access control requires the same user presence. The item is created on the
  first successful unlock, so existing users are not locked out. Turning it on asks you to unlock
  once first, so it cannot lock you out. The lock screen is the shared `ui-next` one Android uses.

Home, Watched, Boards, Search and Saved use an app bar with a Menu button opening the navigation
drawer. Selecting an entry closes the drawer and changes screen; the current entry is announced
as selected. Settings is below the drawer's divider. Threads, Settings, Downloads and History
show Back instead of Menu. The drawer is the window width less the 56dp app bar, capped at 320dp.
The iOS leading-edge swipe goes back and does not open the drawer; Back closes an open drawer
before navigating. Content starts below the app bar and ends above the bottom system inset.

The canonical green palette is shared with Android. Appearance defaults to Theme **System**,
**AMOLED black** off and **Catalog view** List. Light, Dark and AMOLED use the normative token
table, with pure-black sheet and dialog grounds in AMOLED; placeholder and selected fills remain
distinct. There is no separate color-theme picker, and stored choices from removed color themes
or catalog column counts are ignored.

Followed boards and settings live in a DataStore file beside the database, read and written by
the same `BoardPreferencesStore` and `SettingsStore` (`:storage`) that Android's settings use,
with the same keys. The file itself is not encrypted at the application layer; it sits in
Application Support under iOS Data Protection (`CompleteUntilFirstUserAuthentication`) and is
excluded from iCloud backup, as the SQLite database is. Crash reports are kept in a
`diagnostics` folder there with complete Data Protection, not in UserDefaults.

iOS decides when the background refresh runs, from how often you use the app; it does not run in
the simulator, and not at all if Background App Refresh is off in the system settings. The thread
overflow retains Posts and Files, saved-thread membership, sharing, browser and jump actions;
Files also offers Download all and Save page when the host supplies those actions. Save page
writes the thread archive to Orbin's folder in Files and offers it through the share sheet.

## Building on a Mac

You need a Mac with Apple silicon (the simulator build is arm64 only), Xcode 26 or newer (App Store Connect
rejects uploads built with an older SDK), a JDK 17
on the `PATH`, and XcodeGen:

```bash
brew install xcodegen
xcodegen --spec iosApp/project.yml
open iosApp/Orbin.xcodeproj
```

Pick an iPhone simulator and run. The first build takes several minutes: Xcode's pre-build phase
runs `./gradlew :app-ios:embedAndSignAppleFrameworkForXcode`, which compiles the Kotlin framework.

To run on your own iPhone from Xcode, set `ORBIN_TEAM_ID` in `iosApp/project.yml` to your team,
regenerate the project, and turn on Developer Mode on the phone. TestFlight (below) needs neither.

The shared tests also run on the iOS simulator:

```bash
./gradlew :app-ios:iosSimulatorArm64Test
```

## CI

- **iOS** (`.github/workflows/ios.yml`) runs on macOS for every PR or push to main that can
  change what the iOS app compiles: the native tests on the simulator, then an unsigned
  simulator build of the app. Shared `:app-ios` logic tests run in the CI unit-test job.
- **Shared code (iOS targets)** in `ci.yml` cross-compiles every multiplatform module for iOS on
  Linux, which catches JVM-only APIs in shared code quickly.
- **TestFlight** (`.github/workflows/testflight.yml`) archives, signs and uploads a build. It runs
  when you start it from the Actions tab, and after every release. Until the secrets below exist,
  releases skip it with a notice.

## Setting up TestFlight

TestFlight's internal testing needs no App Review, and installing through the TestFlight app needs
no Developer Mode. You do this once:

1. **Join the Apple Developer Program** at [developer.apple.com](https://developer.apple.com/programs/)
   if you have not.
2. **Register the bundle id.** In Certificates, Identifiers & Profiles → Identifiers → +, register
   an App ID. The workflow uses `io.github.defuuls.orbin` unless you set the `IOS_BUNDLE_ID`
   repository variable to something else.
3. **Create the app** in [App Store Connect](https://appstoreconnect.apple.com) → Apps → + → New
   App, with that bundle id. Any name and SKU will do; the app never has to go to review.
4. **Create an API key.** App Store Connect → Users and Access → Integrations → App Store Connect
   API → Team Keys → +, with the **Admin** role (cloud-managed signing needs it). Download the
   `.p8` file (it can only be downloaded once) and note the **Key ID** and the **Issuer ID**.
5. **Find your Team ID** on developer.apple.com → Account → Membership details.
6. **Add the repository secrets** in GitHub → Settings → Secrets and variables → Actions:

   | Secret | Value |
   | --- | --- |
   | `APPLE_TEAM_ID` | the Team ID |
   | `APP_STORE_CONNECT_KEY_ID` | the Key ID |
   | `APP_STORE_CONNECT_ISSUER_ID` | the Issuer ID |
   | `APP_STORE_CONNECT_KEY` | the whole contents of the `.p8` file, including the `BEGIN`/`END` lines |

7. **Run the workflow:** Actions → TestFlight → Run workflow. Signing is Xcode's cloud-managed
   signing through the API key, so there are no certificates or profiles to create or store. The
   archive is built unsigned and signed only at export, with the team's cloud-managed distribution
   certificate: signing the archive took a new Apple Development certificate on every run (each
   runner starts empty) until Apple's per-account limit refused the build. Any "Created via API"
   development certificates left from before can be revoked in Certificates, Identifiers &
   Profiles; nothing uses them.
8. **Add testers.** Once App Store Connect has processed the build (usually 5–15 minutes), open
   the app → TestFlight → Internal Testing, create a group and add yourself (anyone on your App
   Store Connect team can be an internal tester). Install the TestFlight app on the iPhone and
   accept the invitation.

After that, every release uploads a build automatically. The version shown in TestFlight is the
Android release number (`148` for `148-Nectarine`), and the build number is
`<versionCode>.<Chicago date and time>` (`175.202609262145`), so a later upload is always a higher
build. Apple takes only numbers there, so the whole release name (`158-Yuzu`) goes into the app as
`OrbinVersionName` and shows in Settings › **Version**, with the build number under it. Each upload
also fills the build's **What to Test** from `CHANGELOG.md` (`scripts/testflight_notes.py`): the
release's own section, or what is unreleased for a manual build between releases. Preview it with
`python3 scripts/testflight_notes.py --version-name 158-Yuzu --print`. TestFlight builds expire
after 90 days, so a new release (or a manual run) keeps the app installable.

If an upload succeeds and Apple takes too long to register its build, run **Retry TestFlight
notes** from Actions on `main`. Provide the existing release name, numeric marketing version,
and full uploaded build number. It attaches the release's notes to that build using the existing
App Store Connect secrets. It performs no archive or upload and fails visibly if the notes
cannot be saved. For example, release `202-Arracacha` uses marketing version `202` and the exact
build number from its successful upload, such as `220.202611121516`.
