# The iOS app

Orbin's iOS app shares everything below the platform with Android through Kotlin Multiplatform:
the models (`core:model`), the domain, the image-board providers and their Ktor networking, the
design system and every `ui-next` screen. What is iOS-only is small:

| Part | Where | What it does |
| --- | --- | --- |
| `:app-ios` | `app-ios/` | The iOS composition root (Kotlin). Builds the providers over Ktor's Darwin engine, opens the shared database, holds navigation and loading state (`Browser`), and draws the shared screens. Linked into the app as the static `OrbinKit` framework. |
| Xcode project | `iosApp/` | A SwiftUI shell that hosts `MainViewController()` full screen, plus the app icon and `Info.plist`. Generated from `iosApp/project.yml` by [XcodeGen](https://github.com/yonaskolb/XcodeGen); the `.xcodeproj` is not committed. |

## What it does so far

A reader, so far:

- the feed, the start screen as on Android: the newest threads of every board you follow, on
  every site, sorted board A–Z and newest first within a board, with per-board thread limits and
  the permanent filter applied as on Android. A board that fails to load leaves the others;
- boards from every site (the same two the Android app ships), with one site being down never
  hiding the other's boards, and a switch on each to follow it;
- a board's catalog, with thumbnails;
- a thread's posts drawn by the same renderer as Android (`core:ui`'s `PostCommentText`):
  greentext, spoilers that reveal on tap, quotes that jump to the post they quote, and links
  that open in the browser when they pass the same https check Android uses;
- a full-screen viewer for a thread's files: swipe between them, pinch or double-tap to zoom.
  Video and audio open in the browser, since there is no iOS player yet;
- watching a thread (the thread screen's watch action bookmarks it) and a reading history that
  marks the threads you have opened as read in the catalog. Both live in the same Room database
  as Android's (`:storage`), opened through the bundled SQLite driver in Application Support;
- the system edge swipe to go back, returning to pages as they were rather than reloading them.

Followed boards live in a DataStore file beside the database, read and written by the same
`BoardPreferencesStore` (`:storage`) that Android's settings use, with the same keys.

Not there yet: unread
counts on watched threads (Android fills them from a background refresh iOS does not have yet), an
in-app video player, search, settings and saved threads. These move over in later steps.

## Building on a Mac

You need a Mac with Apple silicon (the simulator build is arm64 only), Xcode 16 or newer, a JDK 17
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

- **iOS** (`.github/workflows/ios.yml`) runs on macOS for every PR that can change what the iOS
  app compiles: the shared tests on the simulator, then an unsigned simulator build of the app.
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
   signing through the API key, so there are no certificates or profiles to create or store.
8. **Add testers.** Once App Store Connect has processed the build (usually 5–15 minutes), open
   the app → TestFlight → Internal Testing, create a group and add yourself (anyone on your App
   Store Connect team can be an internal tester). Install the TestFlight app on the iPhone and
   accept the invitation.

After that, every release uploads a build automatically. The version shown in TestFlight is the
Android release number (`148` for `148-Nectarine`), and the build number is
`<versionCode>.<workflow run>`. TestFlight builds expire after 90 days, so a new release (or a
manual run) keeps the app installable.
