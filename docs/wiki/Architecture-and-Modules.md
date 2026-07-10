# Architecture and Modules

Orbin follows **Clean Architecture** with a strict, compiler-enforced separation of concerns.
Dependencies always point *inward*: outer layers (UI, framework) depend on inner layers
(domain, model), never the reverse. The canonical reference is
[`docs/architecture/README.md`](https://github.com/Defuuls/Orbin/blob/main/docs/architecture/README.md);
this page is the summary.

## Layers

| Layer | Modules | Responsibility | Android? |
| --- | --- | --- | --- |
| Presentation | `app`, `feature:*`, `core:ui`, `core:designsystem` | Compose UI, navigation, ViewModels, immutable UI state | yes |
| Domain | `domain` | Use cases, repository **contracts** | no* |
| Data | `data`, `network`, `media`, `provider:*` | Repository implementations, Room/DataStore, HTTP, engines | yes (except `provider:api`) |
| Model | `core:model` | Pure domain entities shared by all layers | no |
| Cross-cutting | `core:common`, `core:testing` | Result types, dispatchers, test fixtures | yes |

\* `domain` is an Android library only so it can expose Paging types; it contains no Android
framework usage. `provider:api` and `core:model` are pure-JVM modules — the build fails if an
Android dependency leaks into them, which keeps the boundary honest.

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
└── feature/                  # home, board, thread, search, gallery, history,
                              # settings, downloads, onboarding
```

## Key design decisions

### The provider seam
All engine-specific behavior is hidden behind `ImageBoardProvider` (`provider:api`). The app
holds a `Set<ImageBoardProvider>` (Hilt multibinding) and a `ProviderRegistry` resolves the
active one. Adding LynxChan/TinyIB/etc. means adding a `provider:*` module — **nothing else
changes**.

### Repository pattern with `OrbinResult`
Repositories return `OrbinResult<T>` (or `Flow<OrbinResult<T>>`) carrying a typed `DataError`,
so the UI branches on failure category (offline / not-found / rate-limited) without catching
exceptions. Providers throw `ProviderException`; the data layer maps those to `DataError` once.

### Offline-first data flow
Reads come from Room first (instant display), then a network refresh updates the cache, which
re-emits through the same `Flow`. Catalogs use Paging 3; threads stream so background refreshes
surface new replies live.

### Parsed comments, not HTML
Engine post HTML is parsed **once** in the data/provider layer into an immutable `PostComment`
tree. The UI renders that tree to an `AnnotatedString` — fast, testable, and free of HTML in
the presentation layer. Backlinks are computed by inverting forward quote links
(`BuildReplyGraphUseCase`).

### Encrypted persistence
The Room database is encrypted with SQLCipher and settings live in an encrypted DataStore, both
protected by a hardware-backed Android Keystore key (TEE/StrongBox). Cloud backup and device
transfer of local data are disabled.

### Performance posture
- Immutable, stable UI state (`data class` + `kotlinx.collections.immutable`) to minimize
  recompositions; Compose compiler strong-skipping is on, with metrics emitted to `build/`.
- Lazy lists with stable keys; Paging for catalogs; background parsing on
  `Dispatchers.Default`.
- Coil 3 memory + disk caching; Media3 for hardware-accelerated playback and video caching
  (since v30 repeated viewing does not churn CDN requests).

## Testing strategy

| Kind | Tooling | Where |
| --- | --- | --- |
| Unit | JUnit, Truth, MockK, Turbine | `src/test` in every module |
| Repository/DB | Room in-memory, MockWebServer | `data`, `network` |
| UI | Compose UI test, Hilt test runner | `feature:*/src/androidTest` |
| Screenshot | Roborazzi | `core:designsystem`, `feature:*` |

Architecture decision records live in
[`docs/architecture/adr/`](https://github.com/Defuuls/Orbin/tree/main/docs/architecture).
