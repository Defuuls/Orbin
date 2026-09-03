# Engineering quality gates

Orbin is intentionally a large application whose *local* development experience should remain
small. A developer changing one feature should not need to understand the whole repository.

This document defines the mechanical gates that keep that promise true.

## One command

Run the same broad local gate CI relies on:

```bash
bash scripts/check.sh
```

It checks repository consistency and architecture first, then formatting, static analysis, JVM
tests and Android lint. The cheap structural checks fail before Gradle work begins.

## Dependency rules

`scripts/validate_architecture.py` runs on every pull request and enforces these invariants:

- `core:model` has no project dependencies and imports no Android or outer-layer Orbin package.
- `domain` points inward to `core:*` and `provider:api`, never concrete infrastructure.
- feature modules do not depend on other feature modules.
- provider implementations do not depend on app, features, data, media, UI, or another provider
  implementation.
- `ui-next` stays presentation-only and cannot depend on features, repositories, networking or
  providers.

If a new architecture rule matters enough to document, prefer making it executable here too.

## Provider contract

`ProviderContract` defines what is allowed to cross the provider SPI boundary regardless of the
engine that produced it:

- board ids are unique;
- catalog/thread posts are structurally consistent;
- post ids are unique within a thread;
- attachments have nonblank ids;
- media and thumbnail URLs are fully-resolved absolute HTTP(S) URLs.

`ProviderRegistryImpl` wraps every registered provider in `InstrumentedImageBoardProvider`, so
contract violations fail at the provider seam instead of surfacing later as mysterious UI/media
bugs.

When adding a provider, keep engine-specific fixture tests in that provider module and use the
shared contract for cross-engine invariants.

## Diagnostics without telemetry

Provider calls record a bounded in-memory event containing only:

- provider id;
- operation (`boards`, `catalog`, `thread`, `search`);
- duration in milliseconds;
- outcome class.

Board ids, thread ids, queries, URLs, titles, post text and media names are deliberately absent.
The events can be included in the existing local diagnostics export, and they are never sent by
the app automatically.

This makes a report useful enough to distinguish transport, parsing and contract failures while
preserving Orbin's no-telemetry design.

## Local feature rule

A feature should read like a small application:

1. route/screen adapter;
2. ViewModel and immutable state;
3. presentation mapping;
4. domain use cases/repository contracts;
5. `ui-next` screen fed with plain values and callbacks.

Avoid feature-to-feature calls. Shared behavior belongs in the domain/core layer or a deliberately
shared infrastructure module.

## Performance posture

Gradle configuration cache, build cache, parallel execution, incremental Kotlin compilation and
configure-on-demand are enabled in `gradle.properties`. Keep module APIs narrow so a change does
not invalidate unrelated compilation.

For routine development, run the narrowest relevant module tests first, then `scripts/check.sh`
before opening a PR. CI remains the authoritative full gate.

## Reliability pyramid

Prefer tests in this order:

1. pure model/sort/parser tests;
2. provider fixture + provider contract tests;
3. repository/use-case tests;
4. ViewModel/state tests;
5. screenshot/Compose semantics tests;
6. a small number of instrumentation journeys.

A regression should normally be caught below the UI layer. Instrumentation is the final safety net,
not the first place business logic is tested.
