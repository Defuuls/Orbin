# Orbin Development Skills & Approach

## Error Handling & Problem-Solving

### When Errors Occur

1. **Attempt Multiple Approaches Before Escalation**
   - Try different command variants and flags
   - Use alternative tools or methods for the same task
   - Explore workarounds and indirect paths to the goal
   - Test different configurations or settings

2. **Retry With Different Strategies**
   - If a direct push fails, try indirect methods (rebase, force-with-lease, different branch)
   - If one tool fails, look for alternative tools or APIs
   - Check for configuration issues and adjust accordingly
   - Use git fetch/pull to refresh state before retry

3. **Only Escalate to User When**
   - All reasonable attempts are exhausted
   - The issue is genuinely a blocker requiring external intervention
   - There's a clear explanation of what was tried and why it failed
   - User input or approval is required for the next step

4. **Document Attempts**
   - Show what was tried and the progression of attempts
   - Explain why each approach was attempted
   - Be transparent about blockers that truly can't be overcome

## Code Quality Standards

- Clean Architecture with multi-module structure
- Kotlin 2.4.0 with K2 compiler
- Jetpack Compose for UI
- Material 3 design system
- No premature abstractions; three similar lines is better than abstraction
- Security-first: HTTPS-only, encryption at rest, biometric locks
- Comprehensive testing: unit tests, screenshot tests, integration tests

## Release & Version Management

- Version naming: themed codenames, one era at a time. Pasta names ran v91-v99 and stars ran
  v30-v90 (ending at v90-Vega). Current codenames are popular Japanese female names.
- Pick a codename not already used by an existing Orbin tag; check `git tag --list`.
- versionCode increments by 1 for each release in `gradle.properties`
  (`orbin.versionCode` / `orbin.versionName`).
- CHANGELOG.md documents all changes with Added/Changed/Fixed/Removed sections.
- README.md stays current with the latest release version.
- Git tags use `v{number}-{codename}`.
- Release titles are derived from the tag by the release workflow.
- Orbin Minimal is maintained in a separate repository and must not be built, versioned,
  released, or reintroduced from this repository.

## Build & CI/CD

- Gradle 9.4.1 with convention plugins in build-logic/
- CI: lint (ktlint, detekt), unit tests, screenshot tests, build APK, CodeQL
- Pre-commit: Fix formatting issues automatically (ktlint --format)
- Tests: JUnit, Turbine, MockK, Truth, Robolectric, Roborazzi
- All changes must pass linting before commit

## Persistence & State Management

- Use `rememberSaveable` for UI state that should survive recomposition and navigation
- Room database for long-term persistence (history, bookmarks, downloads, searches)
- Encrypted DataStore for app settings (protected by Android Keystore)
- SQLCipher for database encryption at rest

## Testing Before Release

- Compile and test locally: `./gradlew test detekt ktlintCheck assembleDebug`
- Verify formatting fixes applied correctly
- Create PR as draft initially
- Ensure all CI checks pass before marking ready
- Test golden path and edge cases for new features

## When You Get Stuck

1. Check git history for similar changes: `git log --grep="keyword"`
2. Read related code and recent commits
3. Look at test files for expected behavior
4. Check architecture docs in `/docs/architecture/`
5. Try different approaches with clear documentation
6. Only then ask user for guidance with full context
