# Orbin

![Orbin brand showcase](docs/assets/orbin-brand-showcase.svg)

Orbin is a modern, privacy-focused, open-source **Android imageboard browser** built with Kotlin,
Jetpack Compose, and Material 3 Expressive. It targets **Android 12+ (API 31+)** and uses a modular Clean
Architecture with engine-specific behavior isolated behind a provider contract.

Orbin is deliberately **read-only**: it browses boards, catalogs, threads, links, and media, but
does not post, reply, or create threads.

**Website:** https://defuuls.github.io/Orbin/

**Current release:** [172 — Fiddlehead](https://github.com/Defuuls/Orbin/releases/tag/v172-Fiddlehead)

**What's new in 172:** Nearby devices gets its own setup screen, and pairing tries every network address.

**Providers:** 4chan (Vichan-compatible reference provider) and BBW Chan (LynxChan)

**Brand:** [identity and logo usage](docs/brand.md)

> Orbin is under active development with signed releases, automated quality gates, encrypted
> local persistence, provider contract validation, and a UI designed to remain readable from
> compact phones through tablets and foldables.

---

## What Orbin does

### Browsing

- **Multi-provider architecture.** Vichan/4chan-compatible and LynxChan engines ship today. New
  engines plug into `ImageBoardProvider` instead of leaking engine rules into the app.
- **Subscribed feed.** Threads from followed boards are grouped by board A–Z, most recently
  active first within each board, with read state, filtering, pull-to-refresh, and inline playback.
- **Readable grid-first catalogs.** Feed and board catalogs use an adaptive card grid as the
  primary presentation. Cards maintain useful width on compact phones, prioritize the subject over
  secondary metadata, and adapt across larger screens.
- **Uncropped previews.** Each card shows the OP's media whole, sized to its own aspect ratio,
  instead of a centre crop.
- **Wide-screen two-pane navigation.** At 840dp and above, a board catalog and its open thread can
  remain side by side without losing navigation state.
- **Offline awareness.** Network state and typed failures are surfaced explicitly rather than
  turning failed requests into unexplained blank screens.
- **Three tabs.** Feed, Media and Boards make up the bottom navigation. Settings opens from the gear
  in the Feed header, and holds only a few rows. Search opens from Boards, and saved files open from Media.

### Thread reader

- Structured posts with greentext, quote links, quote previews, backlinks, spoilers, formatting,
  external links, and cross-board references.
- Inline images/video, multi-attachment carousels, collapsible posts, thread statistics, and a
  dedicated Files view for scanning every attachment.
- Reading history and persistent scroll-position restore.
- Watch threads and receive new-reply notifications.
- **Save links.** Thread external links can be deduplicated and exported as a plain-text file to
  `Downloads/Orbin`.
- Download thread media into `Downloads/Orbin`, organised by board and thread.

### Media

- Coil 3 image loading and Media3 video playback with caching, progressive loading, zoom,
  swipe-gallery navigation, preloading, autoplay, mute controls (an unmute carries to the next
  video until Orbin closes), double-tap to skip 5 seconds, fullscreen playback, and native downloads.
- A **Downloads** tab lists everything you saved; each board also has a **media wall** of its files.
- Optional **deep scan** follows threads to discover media attached to replies.
- Download organization can be flat, by board, by thread, or board then thread.

### Personalization and accessibility

- **Material 3 Expressive redesign.** Flat, matte surfaces, tonal selection containers (`primaryContainer`),
  expressive shape scale (24dp cards, 28dp sheets), and `SpringSpec` physics seeded from a forest green
  palette (`#276A3C`).
- Dynamic color, light/dark/AMOLED modes, global font scaling, and Color themes
  (Yotsuba, Warosu, Miku, Lain, and more) that recolor the Next shell including Feed.
- **Cosmic orbit app icon.** Stylized planetary orbit mark with deep space hues and monochrome
  themed launcher icon support.
- Adaptive layouts for compact phones, tablets, foldables, landscape, and edge-to-edge windows.
- Read/unread hierarchy, screen-reader semantics, touch-target and contrast checks, and screenshot
  coverage for normal, dark, AMOLED, large-text, and maximum-text configurations.
- Settings and commands are searchable rather than requiring users to memorize where controls live.

### Sync across devices

Orbin offers optional nearby sync on Android and iOS. It shares reading history, last-read
positions, bookmarks and watched threads directly over your local network, encrypted between
paired devices. No Orbin account, hosted server or Internet connection is needed. Media,
settings and WebDAV credentials stay on their original device.

To pair devices:

1. Use builds with nearby-sync support on both devices, connect them to the same Wi-Fi, and keep
   Orbin open and unlocked.
2. Open **Settings → Nearby devices** and enable **Nearby device sync** on both.
3. Choose **Add a device** on one; it shows a QR code. On the other, choose **Join another device
   → Scan QR code** and point the camera at it. Copying the code and pasting it into **Join with a
   code instead** still works. Codes expire after ten minutes and can enroll one device; share them
   privately.
4. Use **Sync now** for a manual transfer. For automatic transfers, enable **Trust for automatic
   sync** for the pairing on both devices.

Trust is off by default. Trusted devices check their current local subnet every thirty seconds
and sync when the paired device is reachable and authenticates with its saved key. Both apps
must remain open and unlocked. Turning trust off keeps manual sync available; **Remove device**
revokes future access but does not erase copies already received by the other device.

Nearby sync supports local IPv4 networks and up to eight pairings per device. Allow local-network
access on iOS. Guest Wi-Fi isolation, VPNs or firewalls can prevent connections. Address discovery
is not included: if a saved device address changes, remove the pairing on both devices and pair
again. Update Orbin on both devices when upgrading the sync protocol.

Not on the same Wi-Fi? Turn on **Also use Bluetooth** on both devices before pairing: pairing and
**Sync now** then also work over Bluetooth LE within roughly ten metres, with the same encryption,
between any mix of Android and iOS. On mobile data alone, share a hotspot from one phone and join
it with the other; the hotspot counts as a local network. Or [host an Orbin relay](docs/relay.md)
and enter its URL under **Relay server** before pairing: the QR code carries it, and pairing and
**Sync now** then work over the Internet. The relay only forwards end-to-end encrypted frames; there
is no public Orbin relay.

For sync over the Internet (devices far apart, mobile data), the separate **WebDAV sync** option
uses an HTTPS folder you control and its login. Set it up once, then use **Set up another device →
Show QR code** and scan it with **Copy setup from another device** on the second device instead of
retyping the folder and password. See the [nearby-sync setup and limits](docs/nearby-sync.md),
[authentication and credential storage](docs/authentication.md), and [privacy policy](PRIVACY.md).

### Privacy and security

- Room data is encrypted with SQLCipher and preferences use encrypted DataStore storage protected
  by Android Keystore material.
- Optional biometric app lock uses a Keystore-backed cryptographic gate; `FLAG_SECURE` protects
  locked content from recents previews.
- The app lock gates the UI; background sync can still use its saved login. iOS keeps the sync
  password in device-only Keychain storage. See [authentication and recovery](docs/authentication.md).
- Optional [nearby device sync](docs/nearby-sync.md) shares reading activity directly over Wi-Fi or
  Bluetooth, or through a relay you host, encrypted between paired devices, with no account.
- HTTPS-only imageboard and WebDAV networking, encrypted DNS with system-resolver fallback when a network
  blocks DoH, and configurable user-agent behavior.
- Android cloud backup/device transfer of private local state is disabled.
- Explicit backup/restore exports are user-controlled. Backup JSON and exported link files are
  plaintext, so store them somewhere you trust.

## Architecture at a glance

```text
UI / ui-next
      ↓
feature modules
      ↓
    domain
      ↓
repository contracts
      ↓
data / network / media
      ↓
provider registry
      ↓
provider:vichan | provider:lynxchan
```

`ui-next` is intentionally app-agnostic: screens consume plain presentation data and callbacks.
Feature modules own ViewModels and mapping. Provider implementations own engine quirks. Shared
provider output contracts are validated at the registry boundary.

The repository also has executable architecture rules. CI runs `scripts/validate_architecture.py`
to catch forbidden dependency directions, cycles, and source-boundary leaks before they become
review conventions that slowly decay.

See [`docs/architecture/README.md`](docs/architecture/README.md),
[`docs/architecture/module-map.md`](docs/architecture/module-map.md), and
[`docs/architecture/quality-gates.md`](docs/architecture/quality-gates.md).

## Module map

```text
Orbin/
├── app/                      # Application shell, navigation, DI aggregation
├── app-ios/                  # iOS app (Kotlin): wires shared providers and screens
├── iosApp/                   # iOS app (Xcode, generated by XcodeGen)
├── domain/                   # Core models, policies, repository contracts
├── data/                     # Persistence/repository implementation
├── network/                  # HTTP clients and transport helpers
├── media/                    # Image/video/download infrastructure
├── ui-next/                  # App-agnostic Compose screen kit
├── feature-*/                # Feature ViewModels and presentation mapping
├── provider/
│   ├── api/                  # Provider contracts and shared models
│   ├── vichan/               # 4chan/Vichan reference implementation
│   ├── lynxchan/             # BBW Chan/LynxChan implementation
│   └── registry/             # Provider registration and contract checks
└── core/                     # Common utilities, models, UI primitives, design system
```

## Quality gates

Every meaningful change is expected to survive the same layered checks used for releases:

- architecture validation and repository consistency
- ktlint and detekt
- Android Lint
- JVM/unit and provider-contract tests
- instrumentation tests
- screenshot/Roborazzi verification where UI behavior changes
- CodeQL security analysis

See [`docs/architecture/quality-gates.md`](docs/architecture/quality-gates.md) and
[`docs/wiki/Developer-Guide.md`](docs/wiki/Developer-Guide.md) for local commands and expectations.

## Building

Orbin uses the Gradle wrapper. A JDK 17 installation is recommended.

```bash
./gradlew assembleDebug
```

For the full local check suite:

```bash
bash scripts/check.sh
```

### iOS

The iOS app shares the models, providers and screens with Android through Kotlin Multiplatform
and is distributed through TestFlight. Building it needs a Mac with Xcode; see
[`docs/ios.md`](docs/ios.md).

## License

See [LICENSE](LICENSE).
