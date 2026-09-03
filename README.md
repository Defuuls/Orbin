# Orbin

Orbin is a modern, privacy-focused, open-source **Android imageboard browser** built with Kotlin,
Jetpack Compose, and Material 3. It targets **Android 12+ (API 31+)** and uses a modular Clean
Architecture with engine-specific behavior isolated behind a provider contract.

Orbin is deliberately **read-only**: it browses boards, catalogs, threads, links, and media, but
does not post, reply, or create threads.

**Website:** https://defuuls.github.io/Orbin/

**Current release:** [123 — Emiri](https://github.com/Defuuls/Orbin/releases/tag/v123-Emiri)

**Providers:** 4chan (Vichan-compatible reference provider) and BBW Chan (LynxChan)

> Orbin is under active development with signed releases, automated quality gates, encrypted
> local persistence, provider contract validation, and a UI designed to remain readable from
> compact phones through tablets and foldables.

![Orbin thread viewer](docs/assets/orbin-hero-screenshot.svg)

![Orbin settings](docs/assets/orbin-settings-screenshot.svg)

---

## What Orbin does

### Browsing

- **Multi-provider architecture.** Vichan/4chan-compatible and LynxChan engines ship today. New
  engines plug into `ImageBoardProvider` instead of leaking engine rules into the app.
- **Subscribed feed.** Threads from followed boards are merged and ordered by activity, with
  board identity, read state, filtering, pull-to-refresh, and optional inline video autoplay.
- **Readable grid-first catalogs.** Feed and board catalogs use an adaptive card grid as the
  primary presentation. The old List option is no longer exposed. Cards maintain useful width on
  compact phones, prioritize the subject over secondary metadata, and adapt across larger screens.
- **Images view.** A media-first image grid remains available when visual scanning matters more
  than thread metadata.
- **Wide-screen two-pane navigation.** At 840dp and above, a board catalog and its open thread can
  remain side by side without losing navigation state.
- **Offline awareness.** Network state and typed failures are surfaced explicitly rather than
  turning failed requests into unexplained blank screens.

### Thread reader

- Structured posts with greentext, quote links, quote previews, backlinks, spoilers, formatting,
  external links, and cross-board references.
- Inline images/video, multi-attachment carousels, collapsible posts, thread statistics, and a
  dedicated Files view for scanning every attachment.
- Reading history and persistent scroll-position restore.
- Watch threads and receive new-reply notifications with configurable quiet hours.
- **Save links.** Thread external links can be deduplicated and exported as a plain-text file to
  the configured saved-media folder, which defaults to `Downloads/Orbin`.
- Download thread media using the same configurable storage hierarchy as normal downloads.

### Media

- Coil 3 image loading and Media3 video playback with caching, progressive loading, zoom,
  swipe-gallery navigation, preloading, autoplay, mute controls, fullscreen playback, and native
  downloads.
- **All media** provides one continuous wall of media discovered across board catalogs.
- Optional **deep scan** follows threads to discover media attached to replies.
- Download organization can be flat, by board, by thread, or board then thread.

### Personalization and accessibility

- Material 3, dynamic color, light/dark/AMOLED modes, global font scaling, and more than twenty
  imageboard-inspired palettes.
- Adaptive layouts for compact phones, tablets, foldables, landscape, and edge-to-edge windows.
- Read/unread hierarchy, screen-reader semantics, touch-target and contrast checks, and screenshot
  coverage for normal, dark, AMOLED, large-text, and maximum-text configurations.
- Settings and commands are searchable rather than requiring users to memorize where controls live.

### Privacy and security

- Room data is encrypted with SQLCipher and preferences use encrypted DataStore storage protected
  by Android Keystore material.
- Optional biometric app lock uses a Keystore-backed cryptographic gate; `FLAG_SECURE` protects
  locked content from recents previews.
- HTTPS-only application networking, encrypted DNS with system-resolver fallback when a network
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
├── benchmark/                # Baseline profile generation
├── build-logic/              # Gradle convention plugins
├── core/
│   ├── common/               # Shared results, dispatchers, network state
│   ├── model/                # Pure domain entities
│   ├── designsystem/         # Theme, typography, reusable design primitives
│   ├── ui/                   # Shared Compose helpers
│   └── testing/              # Shared test infrastructure
├── domain/                   # Repository contracts and use cases
├── data/                     # Room, encrypted preferences, repositories, paging
├── network/                  # OkHttp/Retrofit, connectivity, DNS
├── media/                    # Coil, Media3, downloads
├── ui-next/                  # App-independent screen composables
├── provider/
│   ├── api/                  # ImageBoardProvider SPI and provider contracts
│   ├── vichan/               # Vichan/4chan-compatible implementation
│   └── lynxchan/             # LynxChan / BBW Chan implementation
└── feature/                  # home, board, thread, search, history, settings,
                              # gallery/bookmarks, downloads, onboarding
```

## Provider reliability

Provider behavior is treated as a contract rather than a collection of happy-path parsers.
Implementations normalize engine data into Orbin models and are tested for malformed/missing
fields, URL handling, timestamps, media, catalogs, threads, and typed failure behavior.

BBW Chan compatibility includes LynxChan-specific tolerance such as inactive-board handling,
absolute/relative media URL normalization, missing media paths, and catalog timestamp fallbacks.

Provider calls also feed a privacy-safe diagnostics layer. Diagnostics record the provider,
operation, duration, and outcome, not board names, thread IDs, search queries, or requested URLs.

To add an engine, start with [`docs/provider-api/adding-a-provider.md`](docs/provider-api/adding-a-provider.md)
and [`docs/provider-api/contract.md`](docs/provider-api/contract.md).

## Quality gates

Every meaningful change is expected to survive the same layered checks used for releases:

- architecture validation and repository consistency
- ktlint and detekt
- Android Lint
- JVM/unit and provider-contract tests
- Roborazzi screenshot verification for UI changes
- instrumentation tests across supported Android API levels
- CodeQL security analysis
- performance/build-health checks
- release preflight before signed publication

Build-health tooling keeps module fan-out and source growth visible instead of letting Gradle
complexity become invisible debt.

## Tech stack

| Concern | Choice |
| --- | --- |
| Language | Kotlin 2.4.10 (K2), Coroutines, Flow/StateFlow, Serialization |
| UI | Jetpack Compose, Material 3, Navigation Compose, Paging 3 |
| DI | Hilt |
| Persistence | Room + SQLCipher, encrypted DataStore |
| Networking | OkHttp, Retrofit, kotlinx.serialization |
| Media | Coil 3, Media3/ExoPlayer |
| Background | WorkManager |
| Quality | detekt, ktlint, Android Lint, JUnit, Turbine, MockK, Truth, Robolectric, Roborazzi, CodeQL |

Exact dependency versions are pinned in [`gradle/libs.versions.toml`](gradle/libs.versions.toml).

## Build

**Requirements**

- JDK 17+
- Android SDK platform API 37 (`compileSdk` 37, `targetSdk` 36, `minSdk` 31)
- Android Studio Ladybug or newer, or the command line

```bash
./gradlew assembleDebug
./gradlew test
./gradlew ktlintCheck detekt
./gradlew :app:installDebug
```

For the complete local quality path, release process, signing setup, screenshot workflow, and CI
matrix, see the [Developer Guide](https://github.com/Defuuls/Orbin/wiki/Developer-Guide).

## Documentation

| Resource | Purpose |
| --- | --- |
| [Wiki](https://github.com/Defuuls/Orbin/wiki) | Current user and developer documentation |
| [User Guide](https://github.com/Defuuls/Orbin/wiki/User-Guide) | Feed, boards, threads, media, links, downloads |
| [Settings Guide](https://github.com/Defuuls/Orbin/wiki/Settings-Guide) | Settings behavior and defaults |
| [Developer Guide](https://github.com/Defuuls/Orbin/wiki/Developer-Guide) | Toolchain, CI, testing, release workflow |
| [Architecture and Modules](https://github.com/Defuuls/Orbin/wiki/Architecture-and-Modules) | Dependency model and module responsibilities |
| [Troubleshooting](https://github.com/Defuuls/Orbin/wiki/Troubleshooting) | User and build troubleshooting |
| [CHANGELOG.md](CHANGELOG.md) | Complete chronological release record |
| [SECURITY.md](SECURITY.md) | Vulnerability reporting and supported versions |

`docs/wiki/` is the source of truth for the GitHub Wiki. The `wiki-sync.yml` workflow mirrors it
after changes land on `main`.

## Contributing

Contributions are welcome. Read [CONTRIBUTING.md](CONTRIBUTING.md),
[`docs/development-setup.md`](docs/development-setup.md), and the architecture quality-gate docs
before changing module boundaries or adding a provider.

## License

Orbin is released under the [GNU Affero General Public License v3.0](LICENSE).
