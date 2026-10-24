package com.orbin.ios

import com.orbin.core.model.AppColorTheme
import com.orbin.core.model.AppThemeMode
import com.orbin.core.model.BoardId
import com.orbin.core.model.Bookmark
import com.orbin.core.model.ProviderId
import com.orbin.core.model.ThreadId
import com.orbin.core.model.ThreadKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class IosBackupTest {
    private val site = ProviderId("site")
    private val gone = ProviderId("gone")

    private class Stores {
        val settings = FakeSettings()
        val boards = FakeBoardPreferences()
        val bookmarks = FakeBookmarks()
    }

    private class Files(
        private val picked: String? = null,
    ) : BackupFiles {
        var shared: Pair<String, String>? = null

        override suspend fun share(
            fileName: String,
            contents: String,
        ): Boolean {
            shared = fileName to contents
            return true
        }

        override suspend fun pick(): String? = picked
    }

    private fun TestScope.backup(
        stores: Stores,
        files: BackupFiles = Files(),
        providers: List<ProviderId> = listOf(site),
        isAuthorized: () -> Boolean = { true },
    ) = IosBackup(
        settings = stores.settings,
        boardPreferences = stores.boards,
        bookmarks = stores.bookmarks,
        providers = providers,
        files = files,
        scope = backgroundScope,
        appVersion = "155",
        isAuthorized = isAuthorized,
        now = { "2026-09-26T12:00:00Z" },
    )

    @Test
    fun aBackupRestoresBoardsBookmarksAndSettingsButNeverTheAppLock() =
        runTest {
            val from = Stores()
            from.settings.setThemeMode(AppThemeMode.DARK)
            from.settings.setColorTheme(AppColorTheme.PURPLE)
            from.settings.setCoverViolentMedia(false)
            from.settings.setBiometricLockEnabled(true)
            from.boards.setSubscribedBoard(site, BoardId("g"), true)
            val key = ThreadKey(site, BoardId("g"), ThreadId(7))
            from.bookmarks.addBookmark(
                Bookmark(key, "Hi", createdAtMillis = 1, isWatched = true, lastSeenReplyCount = 3),
            )

            val json = backup(from).exportToJson()

            val to = Stores()
            val result = backup(to).importFromJson(json).getOrThrow()
            assertEquals(1 to 1, result)
            val settings = to.settings.settings.first()
            assertEquals(AppThemeMode.DARK, settings.themeMode)
            assertEquals(AppColorTheme.PURPLE, settings.colorTheme)
            assertFalse(settings.coverViolentMedia)
            assertFalse(settings.biometricLockEnabled, "a backup must not change the app lock")
            assertEquals(setOf(BoardId("g")), to.boards.observeSubscribedBoards(site).first())
            val bookmark = to.bookmarks.getBookmark(key)!!
            assertTrue(bookmark.isWatched)
            assertEquals(3, bookmark.lastSeenReplyCount)
        }

    @Test
    fun importingMergesAndSkipsSitesThisBuildDoesNotHave() =
        runTest {
            val from = Stores()
            from.boards.setSubscribedBoard(site, BoardId("g"), true)
            from.boards.setSubscribedBoard(gone, BoardId("x"), true)
            val json = backup(from, providers = listOf(site, gone)).exportToJson()

            val to = Stores()
            to.boards.setSubscribedBoard(site, BoardId("b"), true)
            backup(to).importFromJson(json).getOrThrow()

            assertEquals(setOf(BoardId("b"), BoardId("g")), to.boards.observeSubscribedBoards(site).first())
            assertEquals(emptySet(), to.boards.observeSubscribedBoards(gone).first())
        }

    @Test
    fun aNewerOrBrokenFileIsRefusedWithAReason() =
        runTest {
            val backup = backup(Stores())
            assertTrue(backup.importFromJson("""{"formatVersion": 99}""").isFailure)
            assertTrue(backup.importFromJson("not json").isFailure)
        }

    @Test
    fun exportHandsTheShareSheetADatedFile() =
        runTest {
            val files = Files()
            val backup = backup(Stores(), files)

            backup.export()
            backup.state.first { it == BackupState.Exported }

            assertEquals("orbin-backup-2026-09-26.json", files.shared?.first)
        }

    @Test
    fun importReportsWhatItRestoredAndCancellingChangesNothing() =
        runTest {
            val from = Stores()
            from.boards.setSubscribedBoard(site, BoardId("g"), true)
            val json = backup(from).exportToJson()

            val restoring = backup(Stores(), Files(picked = json))
            restoring.import()
            assertEquals(
                BackupState.Imported(boards = 1, bookmarks = 0),
                restoring.state.first { it is BackupState.Imported },
            )

            val cancelled = backup(Stores(), Files(picked = null))
            cancelled.import()
            assertIs<BackupState.Idle>(cancelled.state.first { it != BackupState.Working })
        }

    @Test
    fun relockingCancelsThePendingSheetAndAllowsANewOperation() =
        runTest {
            var dismissed = false
            var opens = 0
            var authorized = true
            val files =
                object : BackupFiles {
                    override suspend fun share(
                        fileName: String,
                        contents: String,
                    ): Boolean {
                        opens++
                        return suspendCancellableCoroutine { continuation ->
                            continuation.invokeOnCancellation { dismissed = true }
                        }
                    }

                    override suspend fun pick(): String? = null
                }
            val backup = backup(Stores(), files, isAuthorized = { authorized })
            backup.export()
            runCurrent()
            assertEquals(1, opens)
            authorized = false
            backup.cancelPending()
            runCurrent()
            assertTrue(dismissed)
            assertIs<BackupState.Idle>(backup.state.value)

            backup.export()
            runCurrent()
            assertEquals(1, opens, "locked calls must not present a sheet")
            authorized = true
            backup.export()
            runCurrent()
            assertEquals(2, opens)
            backup.cancelPending()
        }

    @Test
    fun aLatePickerCallbackAfterRelockAndUnlockCannotRestoreData() =
        runTest {
            var selected: Continuation<String?>? = null
            val files =
                object : BackupFiles {
                    override suspend fun share(
                        fileName: String,
                        contents: String,
                    ): Boolean = false

                    override suspend fun pick(): String? = suspendCoroutine { selected = it }
                }
            val from = Stores()
            from.settings.setThemeMode(AppThemeMode.DARK)
            val json = backup(from).exportToJson()
            val to = Stores()
            val before = to.settings.settings.first()
            var authorized = true
            val backup = backup(to, files, isAuthorized = { authorized })
            backup.import()
            runCurrent()
            authorized = false
            backup.cancelPending()
            authorized = true
            selected!!.resume(json)
            runCurrent()
            assertEquals(before, to.settings.settings.first())
            assertIs<BackupState.Idle>(backup.state.value)
        }

    @Test
    fun lossOfAuthorizationAtPickerCompletionRejectsTheRestore() =
        runTest {
            val from = Stores()
            from.settings.setThemeMode(AppThemeMode.DARK)
            val json = backup(from).exportToJson()
            var authorized = true
            val files =
                object : BackupFiles {
                    override suspend fun share(
                        fileName: String,
                        contents: String,
                    ): Boolean = false

                    override suspend fun pick(): String {
                        authorized = false
                        return json
                    }
                }
            val to = Stores()
            val before = to.settings.settings.first()
            val backup = backup(to, files, isAuthorized = { authorized })
            backup.import()
            runCurrent()
            assertIs<BackupState.Failed>(backup.state.value)
            assertEquals(before, to.settings.settings.first())
        }
}
