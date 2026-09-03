# Developer Guide

This guide covers the current Orbin development loop, architecture guardrails, CI, and release
process. Exact dependency versions in `gradle/libs.versions.toml`, the Gradle wrapper, and
`gradle.properties` are authoritative.

See also [[Architecture and Modules|Architecture-and-Modules]] and the repository `docs/` tree.

## Prerequisites

- **JDK 17**
- Android SDK platform **API 37** (`compileSdk` 37, `targetSdk` 36, `minSdk` 31)
- Android Studio Ladybug or newer recommended
- `ANDROID_HOME` or a valid `local.properties`

## First build

```bash
git clone https://github.com/Defuuls/Orbin.git
cd Orbin
./gradlew help
./gradlew assembleDebug
```

## Everyday commands

| Task | Command |
| --- | --- |
| Debug APK | `./gradlew assembleDebug` |
| Install | `./gradlew :app:installDebug` |
| Unit tests | `./gradlew test` |
| Module tests | `./gradlew :domain:test` |
| Formatting | `./gradlew ktlintCheck` / `./gradlew ktlintFormat` |
| Static analysis | `./gradlew detekt` |
| Android instrumentation | `./gradlew connectedDebugAndroidTest` |
| Screenshot verification | `./gradlew verifyRoborazziDebug` |
| Record intended screenshot changes | `./gradlew recordRoborazziDebug` |
| Architecture validation | `python3 scripts/validate_architecture.py` |
| Repository consistency | `python3 scripts/validate_repo.py` |
| Baseline profile | `./gradlew :app:generateReleaseBaselineProfile` |

CI may treat Kotlin warnings as errors. Fix warnings rather than depending on a permissive local
configuration.

## Architecture rules

Orbin's module boundaries are enforced automatically. Before introducing a new dependency, read:

- `docs/architecture/README.md`
- `docs/architecture/module-map.md`
- `docs/architecture/quality-gates.md`

The important rule is one-way ownership: features may depend inward on domain/contracts, while
providers/infrastructure do not reach back into features. `ui-next` stays app-agnostic and receives
plain presentation state/callbacks.

`scripts/validate_architecture.py` runs in CI so forbidden edges and cycles fail before merge.

## Provider development

New engines implement the provider SPI rather than adding engine conditionals to app/feature code.
Start with:

- `docs/provider-api/adding-a-provider.md`
- `docs/provider-api/contract.md`

Provider contract/fixture tests should cover both normal responses and real-world tolerance: missing
fields, URL forms, timestamps, media, empty results, and typed failures.

The registry validates normalized provider results. Provider diagnostics record only operational
metadata (provider, operation, duration, outcome), never board names, thread IDs, queries, or URLs.

## UI development

`ui-next` contains the primary screen composables. Prefer plain presentation models and callbacks,
with feature modules performing domain/ViewModel adaptation.

For intentional visual changes:

1. run the relevant unit/semantics tests,
2. run Roborazzi verification,
3. inspect diff artifacts,
4. record new baselines only after confirming the visual change is intended,
5. rerun verification.

Do not disable or loosen screenshot assertions to land a design change.

The feed/catalog UI is grid-first. List is retained only as legacy state compatibility and is not a
visible layout option.

## Toolchain

The project is on the AGP 9 generation of the Android toolchain. At the time of this documentation:

| Component | Version |
| --- | --- |
| AGP | 9.3.1 |
| Gradle | 9.6.1 |
| Kotlin | 2.4.10 |
| KSP | 2.3.10 |
| Room | 2.8.4 |
| Hilt | 2.60.1 |

Always verify the version catalog/wrapper before diagnosing a mismatch. Historical AGP 9 migration
notes live in `docs/agp-9-upgrade.md`.

Gradle configuration cache, build cache, parallel execution, and incremental Kotlin compilation are
enabled. Convention plugins in `build-logic/` keep module build files small.

## CI and quality gates

A PR is expected to pass the applicable matrix before merge:

| Gate | Purpose |
| --- | --- |
| Architecture validation | Prevent forbidden dependency directions/cycles |
| Repository validation | Keep generated/release/docs metadata consistent |
| ktlint | Kotlin formatting |
| detekt | Kotlin static analysis |
| Android Lint | Android correctness/static analysis |
| JVM tests | Unit, parser, contract, state and repository behavior |
| Screenshots | Roborazzi visual regression checks |
| Instrumentation | Real Android behavior across supported API levels |
| CodeQL | Java/Kotlin security analysis |
| Performance/build health | Catch regressions and make build graph growth visible |

Major workflows include `ci.yml`, `codeql.yml`, `screenshots.yml`, `instrumentation.yml`,
`baseline-profile.yml`, `cut-release.yml`, `release.yml`, `wiki-sync.yml`, and `pages.yml`.

### Build health

The repository publishes/uses build-health information so module fan-out and source growth remain
observable. The objective is not to minimize module count at all costs; it is to prevent the build
graph from becoming an invisible tax on everyday development.

## Baseline profiles

`:benchmark` records startup/feed paths for ahead-of-time optimization. Generate with:

```bash
./gradlew :app:generateReleaseBaselineProfile
```

This requires a suitable rooted device/emulator. The manual Baseline Profile workflow can generate
and propose updated profile data through CI.

## Release signing

Keep local signing material outside the repository and use the supported `ORBIN_KEYSTORE_*`
environment variables when needed. Publication uses repository secrets.

Never commit keystores, passwords, or generated secret material.

## Cutting a full Orbin release

The preferred release path is manifest-driven:

1. Update/merge `release/next.toml` with the next number, codename, version code, and release notes.
2. **Cut Release** prepares the release PR using `scripts/prepare_release.py` and synchronized
   metadata changes.
3. Let the release PR pass required checks and merge it.
4. The release automation creates/uses the `v<number>-<Codename>` tag and dispatches the signed
   build.
5. `release.yml` builds the signed APK, stages mapping information, computes SHA-256 checksums,
   generates notes, and publishes the GitHub Release.
6. Verify the tag, release page, APK, mapping file, and checksums before declaring success.

`release/README.md` is the implementation-level reference.

Orbin Minimal has a separate version line and `minimal-v*` release tags. Releasing one app does not
implicitly release the other.

## Wiki updates

`docs/wiki/` is the source of truth for the public GitHub Wiki. Edit those Markdown files in a normal
PR. After merge, `wiki-sync.yml` mirrors the directory to the wiki.

Do not make durable documentation changes only through the GitHub Wiki UI, because the next sync can
overwrite them.

## Contributing

Read `CONTRIBUTING.md` and `docs/development-setup.md`. For architecture/provider work, include the
relevant contract/architecture tests in the same PR so the rule becomes executable rather than a
comment future contributors can accidentally violate.
