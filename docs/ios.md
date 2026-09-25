# The iOS app

Orbin's iOS app shares everything below the platform with Android through Kotlin Multiplatform:
the models (`core:model`), the domain, the image-board providers and their Ktor networking, the
design system and every `ui-next` screen. What is iOS-only is small:

| Part | Where | What it does |
| --- | --- | --- |
| `:app-ios` | `app-ios/` | The iOS composition root (Kotlin). Builds the providers over Ktor's Darwin engine, holds navigation and loading state (`Browser`), and draws the shared screens. Linked into the app as the static `OrbinKit` framework. |
| Xcode project | `iosApp/` | A SwiftUI shell that hosts `MainViewController()` full screen, plus the app icon and `Info.plist`. Generated from `iosApp/project.yml` by [XcodeGen](https://github.com/yonaskolb/XcodeGen); the `.xcodeproj` is not committed. |

## What it does so far

This is the first, read-only version:

- boards from every site (the same two the Android app ships), with one site being down never
  hiding the other's boards;
- a board's catalog, with thumbnails;
- a thread's posts as text, with thumbnails;
- the system edge swipe to go back.

Not there yet: the merged feed, bookmarks and watched threads, the media viewer and video,
search, settings, rich post text (greentext, spoilers, tappable quotes) and saved threads. Those
depend on Android-only layers today (Room, DataStore, Media3, the feature ViewModels) and move
over in later steps.

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
