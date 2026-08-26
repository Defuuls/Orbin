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
   - The issue is genuinely a blocker requiring external intervention (e.g., server misconfiguration, missing credentials)
   - There's a clear explanation of what was tried and why it failed
   - User input or approval is required for the next step

4. **Document Attempts**
   - Show what was tried and the progression of attempts
   - Explain why each approach was attempted
   - Be transparent about blockers that truly can't be overcome

### Example Scenarios

**Scenario: Git push fails with 403**
- ❌ Don't: Report failure immediately
- ✅ Do: Try `git push --force-with-lease`, different branches, tag-specific push, alternative protocols, checking permissions, etc.

**Scenario: Build fails with lint errors**
- ❌ Don't: Ask user to fix it
- ✅ Do: Analyze the error, attempt automatic fixes, run formatters, check configuration, try different build approaches

**Scenario: Remote server returns error**
- ❌ Don't: Ask user to check server
- ✅ Do: Try alternate endpoints, check network config, use different authentication, investigate logs, provide detailed error info before asking

## Code Quality Standards

- Clean Architecture with multi-module structure
- Kotlin 2.4.0 with K2 compiler
- Jetpack Compose for UI
- Material 3 design system
- No premature abstractions; three similar lines is better than abstraction
- Security-first: HTTPS-only, encryption at rest, biometric locks
- Comprehensive testing: unit tests, screenshot tests, integration tests

## Release & Version Management

- Version naming: themed codenames, one era at a time. From v91 these are types of pasta
  (Bucatini, Rigatoni, Orecchiette, Fusilli, Farfalle, Linguine, Cavatappi, Pappardelle).
  v30-v90 used stars and ended with v90-Vega; do not pick from that era. Pick a name not
  already used by an existing tag — check `git tag --list 'v*'`, not this list.
- versionCode increments by 1 for each release, in `gradle.properties`
  (`orbin.versionCode` / `orbin.versionName`). Orbin Minimal versions separately in the same
  file (`orbin.minimalVersionCode` / `orbin.minimalVersionName`) and releases under
  `minimal-v*` tags via `release-minimal.yml`
- CHANGELOG.md documents all changes with Added/Changed/Fixed/Removed sections
- README.md kept current with latest release version
- CHANGELOG.md is the comprehensive historical record. docs/wiki/Release-History.md is a
  narrative summary of selected eras, not a complete list, and says so in its own header — do
  not treat it as the place a new release must be recorded (root-level RELEASE_HISTORY.md and
  DESIGN_SYSTEM.md were retired — superseded by docs/wiki/ and stale)
- Git tags: the full client is `v{number}-{codename}`; Orbin Minimal is
  `minimal-v{number}-{codename}`, on its own line
- Codenames are **popular Japanese female names** (Sakura, Yui, Hana, Aoi, Mei, Rin, Hina,
  Emi, Yuna, Akari, Miu, Riko, Ichika, Himari, Mio, Nao…). In use from **v100** and
  **minimal-v4**; before that the full client used pasta (v91-v99) and stars (to v90), and
  Orbin Minimal had no codename at all (minimal-v1 to v3).
- **One pool across both apps — never reuse a name.** A codename identifies exactly one
  release, so "Sakura" can never mean both a full-client and a Minimal build.

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

## Commit Message Standards

```
[Feature/Fix/Refactor] Brief description

Detailed explanation if needed, explaining the WHY.

Co-Authored-By: Claude Haiku 4.5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_014SHvg7LmG5zcK951kVN4bS
```

## When You Get Stuck

1. Check git history for similar changes: `git log --grep="keyword"`
2. Read related code and recent commits
3. Look at test files for expected behavior
4. Check architecture docs in `/docs/architecture/`
5. Try different approaches with clear documentation
6. Only then ask user for guidance with full context
