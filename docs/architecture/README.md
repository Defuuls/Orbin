# Architecture

Orbin follows **Clean Architecture** with a strict, compiler-enforced separation of concerns.
Dependencies always point *inward*: outer layers (UI, framework) depend on inner layers
(domain, model), never the reverse.

For a fast orientation before reading implementation code, see the [module map](module-map.md).
The architecture's executable enforcement lives in [engineering quality gates](quality-gates.md).

## Layers

| Layer | Modules | Responsibility | Android? |
| --- | --- | --- | --- |
| Presentation | `app`, `feature:*`, `ui-next`, `core:ui`, `core:designsystem` | Compose UI, navigation, ViewModels, immutable UI state | yes |
| Domain | `domain` | Use cases, repository **contracts** | no* |
| Data | `data`, `network`, `media`, `provider:*` | Repository implementations, Room/DataStore, HTTP, engines | yes (except `provider:api`) |
| Model | `core:model` | Pure domain entities shared by all layers | no |
| Cross-cutting | `core:common`, `core:testing` | Result types, dispatchers, test fixtures | yes |

\* `domain` is an Android library only so it can expose Paging types; it contains no Android
framework usage. `provider:api` and `core:model` are pure-JVM modules — the build will fail if an
Android dependency leaks into them, which keeps the boundary honest.

## Module dependency graph

```mermaid
graph TD
    app --> feature_home & feature_board & feature_thread & feature_search
    app --> feature_history & feature_settings & feature_gallery & feature_downloads & feature_onboarding
    app --> data
    app --> provider_vichan & provider_lynxchan

    subgraph Presentation
      feature_home[feature:home]
      feature_board[feature:board]
      feature_thread[feature:thread]
      feature_search[feature:search]
      feature_history[feature:history]
      feature_settings[feature:settings]
      feature_gallery["feature:gallery (incl. bookmarks)"]
      feature_downloads[feature:downloads]
      feature_onboarding[feature:onboarding]
    end

    feature_home --> domain
    feature_board --> domain
    feature_thread --> domain
    feature_thread --> media
    feature_gallery --> media
    feature_home --> core_ui[core:ui]
    core_ui --> core_designsystem[core:designsystem]

    app --> ui_next[ui-next]
    feature_home --> ui_next
    feature_board --> ui_next
    feature_thread --> ui_next
    feature_settings --> ui_next
    feature_gallery --> ui_next
    ui_next --> core_designsystem

    domain --> core_model[core:model]
    domain --> core_common[core:common]
    domain --> provider_api[provider:api]

    data --> domain
    data --> network
    data --> provider_api

    provider_vichan[provider:vichan] --> provider_api
    provider_vichan --> network
    provider_lynxchan[provider:lynxchan] --> provider_api
    provider_lynxchan --> network
    provider_api --> core_model
    media --> core_model
    network --> core_common
    core_common --> core_model
```

## Key design decisions

### The interface seam

`ui-next` holds every screen the app shows. It imports no app type — no model, no repository, no
view model — even though the feature convention plugin puts `core:model` and `core:ui` on its
classpath; the design system is all it actually uses. A screen
takes already-formatted rows and hands back an id; the feature module owns the join to its view
model, and anything behavioural — thumbnails, post bodies, video — is passed in through a slot so
it is the shipped component rather than a copy of one. That is why the same feed row serves the
subscribed feed and every board catalog, why the screens can be screenshot without a view model,
and why a change to how a thread renders cannot reach into how one is loaded.

### The provider seam
All engine-specific behavior is hidden behind `ImageBoardProvider` (`provider:api`). The app
holds a `Set<ImageBoardProvider>` (Hilt multibinding) and a `ProviderRegistry` resolves the active
one. `provider:vichan` (4chan) and `provider:lynxchan` (BBW Chan) are the two engines shipped
today; adding TinyIB/etc. means adding another `provider:*` module — **nothing else changes**.
Every provider result is checked against the shared [provider contract](../provider-api/contract.md)
at the registry boundary. See
[`docs/provider-api/adding-a-provider.md`](../provider-api/adding-a-provider.md).

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
tree (`PostNode`). The UI renders that tree to an `AnnotatedString` — fast, testable, and free of
HTML in the presentation layer. Backlinks are computed by inverting forward quote links
(`BuildReplyGraphUseCase`).

### Performance posture
- Immutable, stable UI state (`data class` + `kotlinx.collections.immutable`) to minimize
  recompositions; Compose compiler strong-skipping is on, with metrics emitted to `build/`.
- Lazy lists with stable keys; Paging for catalogs; background parsing on `Dispatchers.Default`.
- Coil 3 memory + disk caching; Media3 for hardware-accelerated playback.
- Configuration cache, build cache, parallel Gradle execution and incremental Kotlin compilation
  are enabled repository-wide; CI publishes a module fan-out/source-size summary to keep build
  graph growth visible.

## Testing strategy

| Kind | Tooling | Where |
| --- | --- | --- |
| Pure / contract | JUnit, Truth | `core:model`, `provider:api`, provider fixture tests |
| Unit | JUnit, Truth, MockK, Turbine | `src/test` in every module |
| Repository/DB | Room in-memory, MockWebServer | `data`, `network` |
| UI | Compose UI test, Hilt test runner | `feature:*/src/androidTest` |
| Screenshot | Roborazzi | `ui-next`, `core:designsystem`, `feature:*` |

CI also runs `scripts/validate_architecture.py` before Gradle analysis. This turns dependency and
source-boundary rules into merge gates instead of review conventions.

Individual design decisions and their rationale are recorded chronologically in
[CHANGELOG.md](https://github.com/Defuuls/Orbin/blob/main/CHANGELOG.md) rather than as separate
ADR files — this document is the current-state summary.
