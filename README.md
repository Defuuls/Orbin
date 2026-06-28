# Orbin

A modern, fast, and beautiful open-source **Android image board client**, built with Kotlin,
Jetpack Compose, and Material 3. Orbin targets **Android 15+ (API 35+)** and is engineered around
a strict, modular Clean Architecture so that supporting a new image board engine is a matter of
implementing a single interface.

> **Status:** under active development. The architecture, build system, domain core, networking,
> and the reference provider are in place; UI features and the data layer are landing
> incrementally. See [CHANGELOG.md](CHANGELOG.md).

![Orbin settings screenshot](docs/assets/orbin-settings-screenshot.svg)

---

## Features

**Browsing**
- Multi-provider support through a clean provider abstraction (vichan/4chan-compatible engine
  included; LynxChan, TinyIB, etc. can be added without touching app code).
- Board list, catalog with sorting, and a rich thread viewer.

**Thread viewer**
- Structured reply tree with quote links, quote previews, and backlinks.
- Inline images and video, collapsible replies, thread stats, image grid.
- Reading history with unread indicators and scroll-position restore.

**Media** *(planned/landing)*
- Hardware-accelerated image and video, progressive loading, pinch-zoom, swipe gallery,
  background preloading, autoplay + mute toggle, and a native download manager.

**Personalization**
- Material 3 with dynamic color, light/dark, and AMOLED-black themes.
- Adaptive layouts for tablets, foldables, landscape, and edge-to-edge.
- Predictive back gesture and smooth shared-element transitions.

**Privacy & security**
- HTTPS-only networking by default, optional DNS-over-HTTPS, configurable user-agent and proxy,
  optional certificate pinning, and encrypted local storage where appropriate.

## Tech stack

| Concern | Choice |
| --- | --- |
| Language | Kotlin 2.0 (K2), Coroutines, Flow/StateFlow, Serialization |
| UI | Jetpack Compose, Material 3, Navigation Compose, Paging 3 |
| DI | Hilt |
| Persistence | Room, DataStore |
| Networking | OkHttp + Retrofit, kotlinx.serialization |
| Media | Coil 3 (images), Media3/ExoPlayer (video) |
| Background | WorkManager |
| Quality | detekt, ktlint, JUnit, Turbine, MockK, Truth, Robolectric, Roborazzi |

## Module structure

```
Orbin/
├── app/                      # Application, MainActivity, navigation host, DI aggregation
├── build-logic/              # Gradle convention plugins (the build's backbone)
├── core/
│   ├── common/               # Result types, dispatchers, NetworkMonitor
│   ├── model/                # Pure domain entities (no Android deps)
│   ├── designsystem/         # Theme, color, typography, reusable components
│   ├── ui/                   # Shared Compose UI building blocks
│   └── testing/              # Test fixtures and rules
├── domain/                   # Repository contracts + use cases (pure logic)
├── data/                     # Room, DataStore, Paging, repository implementations
├── network/                  # OkHttp/Retrofit, DoH, connectivity
├── media/                    # Coil 3 + Media3 integration, download manager
├── provider/
│   ├── api/                  # The ImageBoardProvider SPI (pure Kotlin)
│   └── vichan/               # Reference provider (vichan/4chan-compatible JSON)
└── feature/                  # home, board, thread, search, bookmarks, history,
                              # settings, gallery, downloads
```

See [docs/architecture/README.md](docs/architecture/README.md) for the dependency graph and design
rationale, and [docs/provider-api/adding-a-provider.md](docs/provider-api/adding-a-provider.md) to
add a new engine.

## Build instructions

**Requirements**
- JDK 17+
- Android SDK with API 35 (`compileSdk`/`minSdk` = 35)
- Android Studio Ladybug+ (or the command line below)

**Common tasks**
```bash
./gradlew assembleDebug          # build the debug APK
./gradlew test                   # JVM unit tests across all modules
./gradlew detekt ktlintCheck     # static analysis & formatting
./gradlew :app:installDebug      # install on a connected device/emulator
```

The build uses a Gradle version catalog (`gradle/libs.versions.toml`) and convention plugins in
`build-logic/`; module build files stay intentionally small (often three lines).

## Contributing

Contributions are welcome — please read [CONTRIBUTING.md](CONTRIBUTING.md) and the
[development setup](docs/development-setup.md). By contributing you agree your work is licensed
under the project's MIT license.

## License

Orbin is released under the [MIT License](LICENSE).
