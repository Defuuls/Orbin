# Original User Request

## 2026-10-02T22:41:34Z

# Teamwork Project Prompt — Draft

> Status: Launched
> Goal: Craft prompt → get user approval → delegate to teamwork_preview
> Requested team: small, focused team

Execute Phase 3 of the Orbin Kotlin Multiplatform Migration for the Data Layer. This is a single self-contained refactor; keep it small and focused.

Working directory: c:\Users\Daddy\Downloads\Orbin_\Orbin
Integrity mode: development

## Requirements

### R1. Move Repositories to Shared Module
Move all repositories from `data/src/main/kotlin` to the shared `core/data/src/commonMain/kotlin` module.

### R2. Refactor to Kotlin Multiplatform
You MUST refactor the migrated files to be valid KMP code. 
- Replace `java.io.File` with Okio (`okio.Path`, `okio.FileSystem.SYSTEM`).
- Replace `JSONObject` and `org.json` with `kotlinx.serialization`.
- Replace `OkHttpClient` with Ktor.
- Remove any `android.content.Context` dependencies or isolate them behind `expect/actual`.

### R3. Platform-Specific Database Encryption
Implement SQLCipher for the Android Room database. For iOS, implement `NSFileProtectionComplete` for file protection instead of SQLCipher to avoid custom C-interop.

## Acceptance Criteria

### Compilation
- [ ] `./gradlew :app:assembleDebug` completes successfully without unresolved references.
- [ ] `./gradlew :app-ios:build` completes successfully without unresolved Android references in `commonMain`.
