# Module map

Use this as the first stop when answering “where does this behavior live?”. The goal is to make a
large repository locally understandable without tracing the whole dependency graph.

| Module | Owns | Start here when… |
| --- | --- | --- |
| `app` | application lifecycle, navigation, DI aggregation | a route, top-level shell or process concern is wrong |
| `ui-next` | stateless screen/layout vocabulary | presentation, spacing or screen composition changes |
| `feature:home` | subscribed feed state/adapters | feed loading, ordering or read-state presentation changes |
| `feature:board` | board catalog state/adapters | a board/catalog interaction changes |
| `feature:thread` | thread reader state/adapters | reading, watch, save/export, scrolling or thread actions change |
| `feature:gallery` | gallery + all-media experiences | media browsing/selection behavior changes |
| `feature:settings` | settings/backup UI and adapters | a preference or backup surface changes |
| `feature:search` | search feature state | search UX or results behavior changes |
| `feature:history` | history presentation | history interactions change |
| `feature:downloads` | download management UI | download queue presentation changes |
| `feature:onboarding` | first-run flow | onboarding/recovery entry changes |
| `domain` | repository contracts and use cases | business rules need changing without Android/network details |
| `data` | repository implementations, DB/DataStore orchestration | caching, persistence or provider-to-domain flow changes |
| `network` | shared OkHttp/Retrofit/connectivity | HTTP policy, DoH, caching or connectivity changes |
| `media` | Coil/Media3/download mechanics | actual image/video loading or playback changes |
| `provider:api` | provider SPI, capabilities, contract + diagnostics | cross-engine behavior or provider guarantees change |
| `provider:vichan` | Vichan/4chan protocol adapter | Vichan JSON/HTML quirks change |
| `provider:lynxchan` | LynxChan/BBWChan protocol adapter | LynxChan JSON/HTML quirks change |
| `core:model` | immutable domain entities and pure sorting/filtering | a concept or pure rule belongs everywhere |
| `core:common` | result types, dispatchers, cross-cutting primitives | shared non-UI infrastructure changes |
| `core:designsystem` | reusable visual tokens/components | shared Material styling changes |
| `core:ui` | shared domain-aware Compose primitives | reusable rendering such as parsed post comments changes |
| `core:testing` | fakes, fixtures and test helpers | multiple modules need the same test setup |
| `benchmark` | baseline profile/performance journeys | startup/scroll performance is being measured |
| `build-logic` | Gradle convention plugins | module build defaults need changing |

## Feature reading order

When entering a feature for the first time, read in this order:

1. the `Next*Screen`/route adapter that binds state to `ui-next`;
2. its ViewModel and UI state;
3. presentation-index/mapping helpers;
4. the domain use case/repository contract it calls;
5. the data implementation only if the question is persistence/network related.

This direction follows the user action inward and avoids opening infrastructure before you know
which contract the feature actually consumes.

## Change placement test

Before adding a dependency, ask:

- Is the behavior pure and shared? Put it in `core:model` or `domain`.
- Is it engine-specific? Put it in `provider:*` behind `provider:api`.
- Is it storage/network/media mechanics? Put it in the corresponding infrastructure module.
- Is it state for one screen/flow? Keep it in that feature.
- Is it only how plain presentation data looks? Keep it in `ui-next`.

Feature-to-feature calls are never the answer; extract the shared contract inward instead.
