# AGP 9.2.1 Upgrade

Upgrade of the build toolchain from AGP 8.13.2 / Gradle 8.14.3 / Kotlin 2.0.21 to
AGP 9.2.1 / Gradle 9.4.1 / Kotlin 2.2.21, executed July 2026.

## Version matrix

| Component | Before | After | Why |
|---|---|---|---|
| AGP | 8.13.2 | 9.2.1 | Target. 9.2.1 (not 9.2.0) because 9.2.0 ships an R8 `RecordTag` `ClassNotFoundException` regression fixed in the patch. |
| Gradle | 8.14.3 | 9.4.1 | AGP 9.2 minimum/default. |
| Kotlin (KGP) | 2.0.21 | 2.2.21 | AGP 9 bundles/expects KGP 2.2.10+. |
| KSP | 2.0.21-1.0.28 | 2.3.6 | KSP is standalone-versioned since 2.3.0 (no Kotlin prefix). 2.3.1+ is required for AGP 9 built-in Kotlin — older KSP fails configuration with "KSP is not compatible with Android Gradle Plugin's built-in Kotlin". |
| Room | 2.6.1 | 2.8.4 | 2.7.0 was the first release with proper KSP2/Kotlin 2.x support; 2.6.1 room-compiler fails under KSP2. |
| Hilt | 2.53.1 | 2.60.1 | Hilt 2.59 is the first Gradle plugin supporting (and requiring) AGP 9; 2.59.2+ fixes Gradle 9 transform registration. Must move in the same commit as AGP. |
| detekt | 1.23.7 | 1.23.8 | Kotlin 2.x analysis support. |
| roborazzi | 1.60.0 | 1.64.0 | Gradle 9 input-validation/BuildService fixes. Deliberately below 1.65.0, which requires Kotlin 2.3. |
| ktlint-gradle | 14.2.0 | 14.2.0 (no change) | Already supports Gradle 9.1+ / built-in Kotlin DSL. |

## Code changes required by AGP 9

- `build-logic/.../KotlinAndroid.kt`, `AndroidCompose.kt`: `CommonExtension<*, *, *, *, *, *>`
  → `CommonExtension` (AGP 9 removed the type parameters); nested blocks switched to
  `.apply` per the official migration guide.
- `AndroidLibraryConventionPlugin.kt`, `AndroidComposeConventionPlugin.kt`:
  `com.android.build.gradle.LibraryExtension` (old implementation class, removed in AGP 9)
  → `com.android.build.api.dsl.LibraryExtension` (public interface).
- `compileOptions` is no longer a `CommonExtension` member — block methods moved to the
  concrete Application/Library extensions, so each convention plugin sets it via a shared
  `CompileOptions.configureJava()` helper.
- **Built-in Kotlin adopted** (AGP 9 default): the `org.jetbrains.kotlin.android` applies
  were removed from the application/library convention plugins and the plugin alias was
  dropped. Opting out via `android.builtInKotlin=false` is NOT viable here: KGP's
  kotlin-android plugin casts the android extension to `BaseExtension`, which the AGP 9
  new-DSL extension no longer implements
  (`ApplicationExtensionImpl ... cannot be cast to ... BaseExtension`).
  Kotlin compiler flags are still wired through `tasks.withType<KotlinCompile>` — AGP's
  built-in Kotlin drives KGP (runtime dependency on KGP 2.2.10+) and reuses its task
  types; `jvmTarget` additionally defaults to `compileOptions.targetCompatibility`.
  The `org.jetbrains.kotlin.plugin.compose` and `...plugin.serialization` compiler
  plugins remain applied as before.

## Deliberate deferrals (follow-up work)

1. **Kotlin 2.3.x + library refresh** — unlocks kotlinx-serialization 1.11.x,
   kotlinx-collections-immutable 0.5.x, roborazzi 1.65+. Kept out of this upgrade to keep
   the change reviewable and bisectable.

## Verification

CI is the verifier for this repo (`ci.yml`: ktlint, detekt, unit tests, debug APK;
CodeQL). JDK 17 in CI meets AGP 9's minimum. Before the next release tag:

- [ ] Full CI green on this branch
- [ ] `assembleRelease` (exercised by the release workflow) — watch R8: AGP 9 enforces
      strict full-mode keep rules and `proguard-android-optimize.txt`
- [ ] Install release APK on-device; smoke-test Hilt injection, Room reads/writes,
      settings (theme/icon switching), biometric lock

## Rollback

Single revert of the upgrade merge commit restores AGP 8.13.2 + Gradle 8.14.3 + Kotlin
2.0.21 coherently; no persisted state (schemas, lockfiles) changes shape in this upgrade.
