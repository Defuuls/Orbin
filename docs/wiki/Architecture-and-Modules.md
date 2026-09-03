# Architecture and Modules

Orbin uses modular Clean Architecture with **executable dependency rules**. The goal is not merely
to have many modules; it is to make each feature understandable as a small local system while the
application remains capable of growing.

The canonical implementation-level documentation lives under
[`docs/architecture/`](https://github.com/Defuuls/Orbin/tree/main/docs/architecture). This page is
the wiki orientation.

## Dependency direction

```text
ui-next
   ↑
feature modules
   ↓
 domain
   ↓
contracts / models
   ↑
data + infrastructure
   ↑
provider implementations
```

UI never owns transport details. Providers never reach into features. Engine quirks are normalized
before they cross the provider boundary.

## Major layers

| Layer | Modules | Responsibility |
| --- | --- | --- |
| App shell | `app` | Process lifecycle, navigation host, DI aggregation |
| Interface | `ui-next`, `core:designsystem`, `core:ui` | Plain-data Compose screens and reusable UI |
| Features | `feature:*` | ViewModels, feature state, mapping, navigation adapters |
| Domain | `domain`, `core:model` | Use cases, repository contracts, domain entities |
| Data | `data` | Room, encrypted preferences, repositories, paging |
| Infrastructure | `network`, `media` | HTTP/connectivity, image/video/download infrastructure |
| Providers | `provider:api`, `provider:vichan`, `provider:lynxchan` | Engine SPI, contracts and implementations |
| Engineering | `build-logic`, scripts, CI workflows | Build conventions and quality enforcement |

## `ui-next`: the interface seam

`ui-next` is intentionally isolated from app-specific types. Screens accept already-formatted rows,
primitive/presentation state, callbacks, and composable slots. Feature modules adapt ViewModel/domain
state into those inputs.

That gives Orbin an important property: the full repository can be large while an individual screen
remains understandable without loading the database, provider, and navigation implementation into
your head at once.

It also makes screenshot testing straightforward because screens can render from deterministic sample
data.

## Provider architecture

`ImageBoardProvider` is the engine seam. The app resolves installed providers through a registry;
features do not contain Vichan or LynxChan conditionals.

Current implementations:

- `provider:vichan` for the Vichan/4chan-compatible reference integration
- `provider:lynxchan` for LynxChan, including BBW Chan

### Shared provider contract

Provider results are validated against common invariants at the registry boundary. Provider fixture
and contract tests exercise behavior such as boards, catalogs, threads, media, URLs, timestamps,
missing fields, and normalized failures.

This matters because imageboard engines frequently return data that is technically valid but differs
from the cleanest example payload. BBW Chan compatibility therefore includes tolerance for inactive
boards, absolute and relative media paths, absent media paths, and catalog timestamp fallbacks.

See [`docs/provider-api/contract.md`](https://github.com/Defuuls/Orbin/blob/main/docs/provider-api/contract.md)
and [`adding-a-provider.md`](https://github.com/Defuuls/Orbin/blob/main/docs/provider-api/adding-a-provider.md).

## Provider diagnostics

Provider calls are instrumented at the registry layer for debugging and support. Diagnostics are
intentionally privacy-limited to operational information such as:

- provider,
- operation,
- duration,
- success/failure outcome.

They do **not** record board names, thread IDs, search queries, or request URLs. This gives developers
a way to answer "which layer failed?" without turning diagnostics into browsing-history telemetry.

## Data flow

A typical path is:

```text
Compose screen
  ↓ event
feature ViewModel
  ↓
domain use case
  ↓
repository contract
  ↓
data repository
  ↓
provider registry / network / database
  ↓
normalized domain result
  ↑
UI state
```

Repositories expose typed `OrbinResult`/`DataError` behavior so presentation code does not catch
arbitrary transport exceptions.

## Parsed comments

Post markup is parsed into structured `PostComment`/`PostNode` data before presentation. Native UI
then renders greentext, quote links, spoilers, formatting, board links, and external links without
shipping raw provider HTML into the screen layer.

## Encrypted persistence

Room storage is encrypted with SQLCipher. Preferences are encrypted and protected by Android
Keystore material. Android cloud backup/device transfer of private Orbin state is disabled; explicit
exports are user-controlled.

## Architecture enforcement

Architecture is a CI gate, not a diagram people are expected to remember.

`scripts/validate_architecture.py` checks dependency/source boundaries and cycles before deeper Gradle
analysis. Repository validation and release preflight add further consistency checks.

Useful references:

- [`docs/architecture/README.md`](https://github.com/Defuuls/Orbin/blob/main/docs/architecture/README.md)
- [`docs/architecture/module-map.md`](https://github.com/Defuuls/Orbin/blob/main/docs/architecture/module-map.md)
- [`docs/architecture/quality-gates.md`](https://github.com/Defuuls/Orbin/blob/main/docs/architecture/quality-gates.md)

## Testing pyramid

| Layer | Examples |
| --- | --- |
| Pure/contract | model invariants, provider contracts, parsers |
| Unit | use cases, ViewModels, repositories |
| Integration | Room, MockWebServer, provider fixtures |
| UI semantics | Compose behavior and accessibility |
| Screenshot | Roborazzi normal/dark/AMOLED/text-scale baselines |
| Instrumentation | real Android startup and feature journeys |
| Security | CodeQL and dependency/repository checks |
| Performance | build-health and performance workflows |

## Build-health philosophy

Gradle uses configuration cache, build cache, parallel execution, convention plugins, and incremental
Kotlin compilation. CI publishes build-health information so dependency fan-out and source growth
remain visible.

The objective is simple: **a large app should still feel small when you are working on one feature.**
