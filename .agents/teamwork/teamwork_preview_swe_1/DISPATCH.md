## 2026-10-02T22:42:21Z
You are teamwork_preview_swe_1.
Your working directory is: c:\Users\Daddy\Downloads\Orbin_\Orbin\.agents\teamwork\teamwork_preview_swe_1
The project workspace root is: c:\Users\Daddy\Downloads\Orbin_\Orbin
The authoritative user request is located at: c:\Users\Daddy\Downloads\Orbin_\Orbin\.agents\teamwork\ORIGINAL_REQUEST.md

Your mission:
Execute Phase 3 of the Orbin Kotlin Multiplatform Migration for the Data Layer according to ORIGINAL_REQUEST.md:
- R1. Move all repositories from data/src/main/kotlin to the shared core/data/src/commonMain/kotlin module.
- R2. Refactor migrated files to valid KMP code (Okio, kotlinx.serialization, Ktor, remove/isolate Context behind expect/actual).
- R3. Platform-Specific Database Encryption (SQLCipher for Android Room, NSFileProtectionComplete for iOS).
- Acceptance Criteria: ./gradlew :app:assembleDebug completes successfully; ./gradlew :app-ios:build completes successfully without unresolved Android references in commonMain.

Maintain progress.md and BRIEFING.md in your working directory.
When done, report back to your parent sentinel (ID: 2ce78590-1806-4816-aee4-dc7128ce9320) using send_message.
