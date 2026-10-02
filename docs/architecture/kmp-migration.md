# Kotlin Multiplatform migration

The goal is one Kotlin Multiplatform codebase for Android and iOS, built on open-source libraries,
with thin platform shells. It lands phase by phase, one PR each, and every phase ships green with
no behaviour change.

**Status:** phases 1–2 merged in #633 (main `04e1251a`). Phase 3 is next.

## Decisions

| Decision | Choice | Why |
|---|---|---|
| Rewrite or consolidate | Consolidate | The stack is already open source (Kotlin, Compose Multiplatform, Ktor, Room, Coil, Media3). The cost was the Android/iOS split, not the libraries. |
| Dependency injection | **kotlin-inject** | Checked at compile time like Hilt, and it works in shared code. Hilt stays on Android until phase 6 and reads the shared graph through a bridge. |
| Delivery | Phase by phase | Each phase is one PR, green and shippable before the next starts. |
| iOS scope | Full peer | Data, network and media move fully into shared code, and iOS duplicates are deleted. |

## Starting point

- **Already shared:** `core:model`, `core:common`, `domain`, `provider:*`, `storage`, `ui-next`, `core:ui`, `core:designsystem`, `app-ios`.
- **Android-only:** `data`, `network`, `media`, and all `feature:*` modules, tied to Hilt, OkHttp and Media3. `app-ios` re-implemented much of that layer (~45 files).
- **Two UI stacks:** the Android `feature:*` screens and the shared `ui-next` screens overlap.

## Target stack

| Concern | Before | Target |
|---|---|---|
| DI | Hilt (Android-only) | kotlin-inject (shared) ✅ started |
| HTTP | OkHttp + Ktor/OkHttp; Darwin on iOS with no policy | Ktor everywhere, one factory and shared policy ✅. OkHttp stays as Android's engine. |
| Database | Room KMP + SQLCipher (Android) | Room KMP; SQLCipher on both platforms |
| Settings | DataStore (partly shared) | DataStore Preferences, shared only |
| Navigation | androidx navigation-compose + custom | Navigation Compose Multiplatform, typed routes |
| ViewModels | AndroidX + Hilt | Lifecycle ViewModel (Compose Multiplatform) |
| Images | Coil 3 | Coil 3, Ktor network loader on both |
| Video | Media3 / AVPlayer | `expect/actual` player: Media3 / AVPlayer |
| Downloads | WorkManager / iOS code | Shared queue: WorkManager / background `URLSession` |
| Logging | `android.util.Log`, unused Timber | Kermit ✅ |
| Hashing | `java.security.MessageDigest` | kotlincrypto ✅ (POWBlock) |
| UI | `feature:*` + `ui-next` | `ui-next` only (Material 3) |
| Tests | JUnit, MockK, Robolectric, Roborazzi | kotlin-test + Turbine in shared code, fakes over mocks; Roborazzi for screenshots |

## Phases

### ✅ Phase 1: shared object graph (#633)

- New `:core:graph`: `SharedGraph`, a kotlin-inject component. Each platform passes in its HTTP
  client, database, DataStore and dispatcher, and the graph builds:
  - the Json config
  - the sites (`orbinProviders()`, now one list)
  - `SettingsStore` and `BoardPreferencesStore`
  - the bookmark and history repositories
- **Android:** Hilt reads the graph via `SharedGraphModule`. Deleted the duplicates:
  `ProvidersModule`, the repository providers in `DatabaseModule`, and `NetworkModule.providesJson`.
- `NetworkConfigProvider` moved to `FixedNetworkConfigProvider`. This broke a real Hilt cycle:
  OkHttp → settings → graph → client.
- **iOS:** `AppGraph` calls `createSharedGraph(...)`.
- `validate_architecture.py`: only `:app` and `:app-ios` may depend on `:core:graph`.
- `android.util.Log` → Kermit, and the unused Timber dependency was removed.

### ✅ Phase 2: shared network policy (#633)

- New `:core:network` (jvm/iOS) holds:
  - `NetworkConfig`
  - `RequestPolicy`: HTTPS-only, User-Agent, media Accept and same-origin Referer, `max-age=60` on API GETs, `no-store` on mutations
  - `PowBlock`: parsing, the terms-page paths, and mining as a cancellable coroutine on a two-wide dispatcher (replacing a Java thread pool)
  - Ktor plugins that apply the policy and clear the gate
  - one `orbinHttpClient(engine, config, engineAppliesPolicy)` factory
- **Android:** OkHttp stays the engine (DoH, disk cache, TLS, Media3/Coil). Its interceptors
  delegate to the shared policy, so image, video and download traffic stays covered. The unchanged
  OkHttp tests still pass.
- **iOS gained:** the POWBlock gate (8chan.moe), the media header and cache rules, and HTTPS-only
  enforcement.

### ⏭ Phase 3: data (large)

- Move the repositories and paging (paging-common) into shared code.
- Delete the duplicated repository code in `app-ios`.
- Encrypt the database on both platforms (SQLCipher on iOS).
- Grow `SharedGraph` to cover these, and shrink Hilt's `data` modules to platform inputs.

### Phase 4: media (medium)

- `expect/actual` wrappers for image loading, the video player and the download queue.
- Move the WorkManager download job behind the shared queue, with background `URLSession` on iOS.
- Move Coil to the Ktor network loader.
- Make `RetryAfterTracker` shared (currently Android/video only).

### Phase 5: UI consolidation (large)

- Port the remaining `feature:*` screens into `ui-next` one at a time: onboarding, search and
  downloads first, then the larger screens.
- Use shared ViewModels and multiplatform navigation.
- Delete each `feature:*` module once its Roborazzi screenshots match.

### Phase 6: platform shells (small)

- `app` and `app-ios` shrink to entry points, wiring, notifications, deep links and sharing.
- Remove Hilt and the Android-only Compose BOM.

### Phase 7: hardening (medium)

- Re-baseline the macrobenchmarks and baseline profiles.
- TalkBack and VoiceOver passes.
- Security review of storage, deep links and the clipboard.
- Upgrade-path test from the old database to the new one.

## Guardrails

- **No big-bang branch.** Every phase merges to `main` behind the full CI suite: ktlint, detekt,
  Android Lint, tests with warnings as errors, `buildHealth`, Roborazzi, the iOS simulator build,
  instrumentation tests and CodeQL.
- **Protect user data.** Room schemas and DataStore keys stay stable, and schema bumps need
  migration tests.
- **Prove behaviour is preserved.** Existing tests are kept unmodified where they cover moved code.
- **Release automation is untouched** (`release/next.toml`).
- **Check in the real app.** Each phase is checked with `/run-orbin`
  (`.claude/skills/run-orbin/`), which drives the real `MainActivity` headlessly.

## Also done along the way

| PR | What |
|---|---|
| #628 | CI fixes on the Material 3 migration: ktlint, Scaffold padding lint, gallery goldens |
| #629, #630 | Release 162-Cantaloupe cut and published |
| #632 | `/run-orbin` skill: drive the real app headlessly (Robolectric + Roborazzi) |
| #633 | Fixed the "%d threads" header (shipped in 162) plus phases 1–2 |
