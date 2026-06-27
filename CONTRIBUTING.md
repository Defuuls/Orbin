# Contributing to Orbin

Thanks for your interest in improving Orbin! This guide covers how to get set up, the conventions
we follow, and how to get a change merged.

## Code of conduct

Be respectful and constructive. Harassment of any kind is not tolerated.

## Getting started

1. Fork and clone the repository.
2. Install JDK 17+ and the Android SDK (API 35).
3. Open in Android Studio (Ladybug or newer) or build from the command line.
4. Verify your environment:
   ```bash
   ./gradlew help
   ./gradlew test
   ```

See [docs/development-setup.md](docs/development-setup.md) for details.

## Project conventions

- **Architecture.** Respect module boundaries (see
  [docs/architecture/README.md](docs/architecture/README.md)). Dependencies point inward:
  `feature → domain → model`. `domain` and `provider:api` must stay pure Kotlin (no Android).
- **Kotlin style.** Official Kotlin code style, enforced by ktlint. Run `./gradlew ktlintFormat`.
- **Static analysis.** detekt must pass: `./gradlew detekt`.
- **Immutability.** UI state is immutable; prefer `data class` + `kotlinx.collections.immutable`
  to keep Compose recompositions minimal.
- **DI.** Use constructor injection and Hilt. Bind implementations to interfaces in a `di` package.
- **No leaky abstractions.** Transport/engine details stay inside `network`/`provider:*`. The rest
  of the app speaks in domain models and `OrbinResult`.
- **Documentation.** Public types and non-obvious logic get KDoc explaining the *why*.

## Tests

Every behavioral change ships with tests:
- **Unit tests** for use cases, mappers, parsers, and ViewModels (`src/test`).
- **Instrumented/UI tests** for Compose screens (`src/androidTest`).
- **Screenshot tests** via Roborazzi for design-system components.

```bash
./gradlew test                    # all JVM unit tests
./gradlew connectedCheck          # instrumented tests (device/emulator)
./gradlew verifyRoborazziDebug    # screenshot tests
```

## Commit & PR process

- Branch from `main`; use a descriptive branch name.
- Write [Conventional Commits](https://www.conventionalcommits.org/) messages
  (`feat:`, `fix:`, `docs:`, `build:`, `refactor:`, `test:`…).
- Keep PRs focused. Fill in the PR template, link related issues.
- CI must be green (build, tests, detekt, ktlint) before review.
- A maintainer reviews and merges. Squash-merge is preferred.

## Adding a new provider

Implementing support for another image board engine is intentionally simple — see the
[step-by-step guide](docs/provider-api/adding-a-provider.md). In short: implement
`ImageBoardProvider`, map the engine's responses to domain models, and register the provider with
a Hilt `@IntoSet` binding. No changes to domain, data, or UI are required.

## License

By contributing, you agree that your contributions are licensed under the
[MIT License](LICENSE).
