package com.orbin.feature.settings

import com.google.common.truth.Truth.assertThat
import com.orbin.core.model.AppSettings
import com.orbin.core.model.AppThemeMode
import com.orbin.core.model.BoardId
import com.orbin.core.model.Bookmark
import com.orbin.core.model.CatalogMode
import com.orbin.core.model.PlaybackPreferences
import com.orbin.core.model.PlaybackScope
import com.orbin.core.model.PlaybackSettings
import com.orbin.core.model.ProviderId
import com.orbin.core.model.SavedSearch
import com.orbin.core.model.SearchContentType
import com.orbin.core.model.SearchFilters
import com.orbin.core.model.ThreadId
import com.orbin.core.model.ThreadKey
import com.orbin.core.testing.repository.FakeBoardPreferencesRepository
import com.orbin.core.testing.repository.FakeImageBoardProvider
import com.orbin.core.testing.repository.FakeProviderRegistry
import com.orbin.core.testing.repository.FakeSearchRepository
import com.orbin.core.testing.repository.FakeSettingsRepository
import com.orbin.domain.repository.BookmarkRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Test

/**
 * Round-trips a backup through export and import.
 *
 * `restoreSettings` is a hand-written list of setter calls, and it has already fallen out of step
 * with [AppSettings] once: settings added after it was written were exported and then silently
 * dropped on import, so a restore quietly lost exactly the values a user had bothered to change.
 * [everySettingSurvivesExportAndImport] fails mechanically the next time that happens.
 */
class BackupServiceTest {
    private val populatedSettings =
        AppSettings(
            hideNsfwBoards = true,
            themeMode = AppThemeMode.DARK,
            catalogMode = CatalogMode.GRID,
            amoled = true,
            biometricLockEnabled = true,
            onboardingCompleted = true,
            openThreadComments = true,
            playbackPreferences =
                PlaybackPreferences(
                    defaults = PlaybackSettings(autoplay = false, startMuted = false),
                ).withSettings(PlaybackScope("fake", "g"), PlaybackSettings(loopVideos = false)),
        )

    @Test
    fun everySettingSurvivesExportAndImport() =
        runTest {
            val exported = service(FakeSettingsRepository(populatedSettings)).exportToJson("test")
            val exportedSettings =
                Json
                    .parseToJsonElement(exported)
                    .jsonObject
                    .getValue("settings")
                    .jsonObject
            assertThat(exportedSettings.getValue("catalogMode").toString()).isEqualTo("\"GRID\"")
            assertThat(exportedSettings.keys)
                .containsNoneOf("colorTheme", "color_theme", "tabletCatalogColumns", "tablet_catalog_columns")

            val destinationSettings = FakeSettingsRepository()
            service(destinationSettings).importFromJson(exported).getOrThrow()
            val restored = destinationSettings.settings.first()

            // biometricLockEnabled is deliberately not restored; normalise it so the comparison
            // covers every other field without listing them.
            assertThat(restored.copy(biometricLockEnabled = false))
                .isEqualTo(populatedSettings.copy(biometricLockEnabled = false))
        }

    @Test
    fun restoringPlaybackReplacesOldOverridesAndKeepsInheritedScopesLive() =
        runTest {
            val exported = service(FakeSettingsRepository(populatedSettings)).exportToJson("test")
            val destination =
                FakeSettingsRepository(
                    AppSettings(
                        playbackPreferences =
                            PlaybackPreferences().withSettings(
                                PlaybackScope("fake", "old"),
                                PlaybackSettings(startMuted = false),
                            ),
                    ),
                )
            service(destination).importFromJson(exported).getOrThrow()
            assertThat(destination.current.playbackPreferences).isEqualTo(populatedSettings.playbackPreferences)
            assertThat(destination.current.playbackPreferences.hasOverride(PlaybackScope("fake", "old"))).isFalse()
        }

    @Test
    fun olderBackupsWithoutAColorThemeRestoreGridAndVideoFirstChatDefaults() =
        runTest {
            for (legacyColumns in listOf("", """, "tabletCatalogColumns": 6""")) {
                val destination =
                    FakeSettingsRepository(AppSettings(openThreadComments = true, catalogMode = CatalogMode.GRID))

                service(destination)
                    .importFromJson("""{ "settings": { "themeMode": "DARK" $legacyColumns } }""")
                    .getOrThrow()

                assertThat(destination.current.openThreadComments).isFalse()
                assertThat(destination.current.themeMode).isEqualTo(AppThemeMode.DARK)
                assertThat(destination.current.catalogMode).isEqualTo(CatalogMode.GRID)
            }
        }

    @Test
    fun backupsContainingALegacyColorThemeAreRefusedBeforeAnyDataIsApplied() =
        runTest {
            for (mode in RestoreMode.entries) {
                for (field in listOf("colorTheme", "color_theme")) {
                    for (value in listOf("\"PURPLE\"", "null", "\"UNRECOGNIZED\"")) {
                        val settings = FakeSettingsRepository(populatedSettings)
                        val boards =
                            FakeBoardPreferencesRepository(
                                subscribed = setOf(BoardId("g")),
                                favorites = setOf(BoardId("a")),
                            )
                        val keptBookmark = bookmark("g", 7L, watched = true)
                        val bookmarks = FakeBookmarkRepository(listOf(keptBookmark))
                        val searches = FakeSearchRepository()
                        searches.saveSearch(savedSearch("keep"))
                        val beforeSearches = searches.observeSavedSearches().first()
                        val json =
                            """
                            {
                              "settings": {
                                "$field": $value,
                                "hideNsfwBoards": false,
                                "themeMode": "LIGHT",
                                "catalogMode": "LIST"
                              },
                              "subscribedBoards": [{"providerId":"fake","boardId":"x"}],
                              "favoriteBoards": [{"providerId":"fake","boardId":"x"}],
                              "bookmarks": [{"providerId":"fake","boardId":"x","threadId":42}],
                              "savedSearches": [{"text":"replace"}]
                            }
                            """.trimIndent()

                        val result = service(settings, bookmarks, searches, boards).importFromJson(json, mode)

                        assertThat(result.exceptionOrNull()?.message)
                            .isEqualTo("This backup was made by an older version of Orbin and cannot be restored.")
                        assertThat(settings.current).isEqualTo(populatedSettings)
                        assertThat(boards.observeSubscribedBoards(ProviderId("fake")).first())
                            .containsExactly(BoardId("g"))
                        assertThat(boards.observeFavoriteBoards(ProviderId("fake")).first())
                            .containsExactly(BoardId("a"))
                        assertThat(bookmarks.observeBookmarks().first()).containsExactly(keptBookmark)
                        assertThat(searches.observeSavedSearches().first()).isEqualTo(beforeSearches)
                    }
                }
            }
        }

    @Test
    fun colorThemeMentionedInUserContentDoesNotRefuseACompatibleBackup() =
        runTest {
            val json = """{"savedSearches":[{"text":"colorTheme"}]}"""
            val searches = FakeSearchRepository()

            service(FakeSettingsRepository(), searches = searches).importFromJson(json).getOrThrow()

            assertThat(
                searches
                    .observeSavedSearches()
                    .first()
                    .single()
                    .text,
            ).isEqualTo("colorTheme")
        }

    @Test
    fun biometricLockPolicyIsNeverRestoredFromBackup() =
        runTest {
            val unlockedBackup =
                service(FakeSettingsRepository(populatedSettings.copy(biometricLockEnabled = false)))
                    .exportToJson("test")
            val lockedDestination = FakeSettingsRepository(populatedSettings.copy(biometricLockEnabled = true))
            service(lockedDestination).importFromJson(unlockedBackup).getOrThrow()
            assertThat(lockedDestination.settings.first().biometricLockEnabled).isTrue()

            val lockedBackup = service(FakeSettingsRepository(populatedSettings)).exportToJson("test")
            val unlockedDestination = FakeSettingsRepository(populatedSettings.copy(biometricLockEnabled = false))
            service(unlockedDestination).importFromJson(lockedBackup).getOrThrow()
            assertThat(unlockedDestination.settings.first().biometricLockEnabled).isFalse()
        }

    @Test
    fun importedOnboardingCannotSuppressTheExistingLock() =
        runTest {
            for (json in listOf("""{"settings":{"onboardingCompleted":false}}""", "{}")) {
                val destination = FakeSettingsRepository(populatedSettings)
                service(destination).importFromJson(json).getOrThrow()
                val restored = destination.settings.first()
                assertThat(restored.biometricLockEnabled).isTrue()
                assertThat(restored.onboardingCompleted).isTrue()
            }
        }

    @Test
    fun importSummaryCountsOnlyValidSavedSearches() =
        runTest {
            val backupJson =
                """{"savedSearches":[{"text":"all boards"},{"text":"valid board","boardId":"g"},{"text":"invalid board","boardId":"../g"}]}"""
            for (mode in RestoreMode.entries) {
                val searches = FakeSearchRepository()
                val summary =
                    service(FakeSettingsRepository(), searches = searches)
                        .importFromJson(backupJson, mode)
                        .getOrThrow()
                assertThat(summary.savedSearches).isEqualTo(2)
                assertThat(searches.observeSavedSearches().first().map { it.text })
                    .containsExactly("all boards", "valid board")
            }
        }

    @Test
    fun bookmarksAndSavedSearchesSurviveExportAndImport() =
        runTest {
            val bookmarks = FakeBookmarkRepository(listOf(bookmark("g", 42L, watched = true)))
            val searches = FakeSearchRepository()
            searches.saveSearch(savedSearch("kotlin"))

            val exported =
                service(FakeSettingsRepository(), bookmarks = bookmarks, searches = searches)
                    .exportToJson("test")

            val destinationBookmarks = FakeBookmarkRepository()
            val destinationSearches = FakeSearchRepository()
            val summary =
                service(
                    FakeSettingsRepository(),
                    bookmarks = destinationBookmarks,
                    searches = destinationSearches,
                ).importFromJson(exported).getOrThrow()

            assertThat(summary.bookmarks).isEqualTo(1)
            assertThat(summary.savedSearches).isEqualTo(1)
            assertThat(
                destinationBookmarks
                    .observeBookmarks()
                    .first()
                    .single()
                    .isWatched,
            ).isTrue()
            assertThat(
                destinationSearches
                    .observeSavedSearches()
                    .first()
                    .single()
                    .text,
            ).isEqualTo("kotlin")
        }

    /** Importing must merge by default, while RestoreMode.REPLACE atomically replaces existing collections. */
    @Test
    fun importAddsToExistingBookmarksRatherThanReplacingThem() =
        runTest {
            val exported =
                service(FakeSettingsRepository(), bookmarks = FakeBookmarkRepository(listOf(bookmark("g", 1L))))
                    .exportToJson("test")

            val destination = FakeBookmarkRepository(listOf(bookmark("a", 2L)))
            service(FakeSettingsRepository(), bookmarks = destination).importFromJson(exported).getOrThrow()

            assertThat(destination.observeBookmarks().first().map { it.key.thread.value })
                .containsExactly(1L, 2L)
        }

    @Test
    fun importWithReplaceModeReplacesExistingCollections() =
        runTest {
            val exported =
                service(FakeSettingsRepository(), bookmarks = FakeBookmarkRepository(listOf(bookmark("g", 1L))))
                    .exportToJson("test")

            val destination = FakeBookmarkRepository(listOf(bookmark("a", 2L)))
            service(FakeSettingsRepository(), bookmarks = destination)
                .importFromJson(exported, mode = RestoreMode.REPLACE)
                .getOrThrow()

            assertThat(destination.observeBookmarks().first().map { it.key.thread.value })
                .containsExactly(1L)
        }

    /**
     * A backup can reference a provider the destination build doesn't ship (a plugin was removed,
     * or the file came from a fork with extra providers). Those refs must be skipped rather than
     * crashing the whole import or being restored against a provider that doesn't exist.
     */
    @Test
    fun bookmarksFromAnUnregisteredProviderAreSkippedOnImport() =
        runTest {
            val exported =
                service(FakeSettingsRepository(), bookmarks = FakeBookmarkRepository(listOf(bookmark("g", 1L))))
                    .exportToJson("test")

            val destination = FakeBookmarkRepository()
            val summary =
                BackupService(
                    FakeSettingsRepository(),
                    FakeBoardPreferencesRepository(),
                    destination,
                    FakeSearchRepository(),
                    FakeProviderRegistry(FakeImageBoardProvider(id = "someOtherProvider")),
                ).importFromJson(exported).getOrThrow()

            assertThat(destination.observeBookmarks().first()).isEmpty()
            assertThat(summary.bookmarks).isEqualTo(0)
            // The bookmark plus the "fake" provider's default subscribed board ("g"), both
            // referencing a provider this registry doesn't know.
            assertThat(summary.skippedUnknownProvider).isEqualTo(2)
        }

    @Test
    fun importKeepsOnlyThumbnailsOnTheProvidersOwnHttpsHosts() =
        runTest {
            val exported =
                service(
                    FakeSettingsRepository(),
                    bookmarks =
                        FakeBookmarkRepository(
                            listOf(
                                bookmark("g", 1L).copy(thumbnailUrl = "https://example.org/g/1s.jpg"),
                                bookmark("g", 2L).copy(thumbnailUrl = "https://tracker.example/u123.png"),
                                bookmark("g", 3L).copy(thumbnailUrl = "http://example.org/g/3s.jpg"),
                            ),
                        ),
                ).exportToJson("test")

            val destination = FakeBookmarkRepository()
            service(FakeSettingsRepository(), bookmarks = destination).importFromJson(exported).getOrThrow()

            val thumbnails = destination.observeBookmarks().first().associate { it.key.thread.value to it.thumbnailUrl }
            assertThat(thumbnails).containsExactly(1L, "https://example.org/g/1s.jpg", 2L, null, 3L, null)
        }

    @Test
    fun aBackupFromANewerFormatIsRejected() =
        runTest {
            val result = service(FakeSettingsRepository()).importFromJson("""{ "formatVersion": 99 }""")

            assertThat(result.isFailure).isTrue()
        }

    private fun service(
        settings: FakeSettingsRepository,
        bookmarks: BookmarkRepository = FakeBookmarkRepository(),
        searches: FakeSearchRepository = FakeSearchRepository(),
        boards: FakeBoardPreferencesRepository = FakeBoardPreferencesRepository(),
    ) = BackupService(
        settings,
        boards,
        bookmarks,
        searches,
        FakeProviderRegistry(FakeImageBoardProvider(id = "fake")),
    )

    private fun bookmark(
        board: String,
        thread: Long,
        watched: Boolean = false,
    ) = Bookmark(
        key = ThreadKey(ProviderId("fake"), BoardId(board), ThreadId(thread)),
        title = "Thread $thread",
        createdAtMillis = 1_000L,
        isWatched = watched,
    )

    private fun savedSearch(text: String) =
        SavedSearch(
            text = text,
            filters = SearchFilters(mediaOnly = true, contentTypes = setOf(SearchContentType.IMAGE)),
            createdAtMillis = 1_000L,
        )
}

/** Minimal in-memory [BookmarkRepository]; core:testing does not ship one. */
private class FakeBookmarkRepository(
    initial: List<Bookmark> = emptyList(),
) : BookmarkRepository {
    private val state = MutableStateFlow(initial)

    override fun observeBookmarks(): Flow<List<Bookmark>> = state

    override fun observeBookmark(key: ThreadKey): Flow<Bookmark?> =
        state.map { list -> list.firstOrNull { it.key == key } }

    override suspend fun addBookmark(bookmark: Bookmark) {
        state.value = state.value.filterNot { it.key == bookmark.key } + bookmark
    }

    override suspend fun replaceBookmarks(bookmarks: List<Bookmark>) {
        state.value = bookmarks
    }

    override suspend fun removeBookmark(key: ThreadKey) {
        state.value = state.value.filterNot { it.key == key }
    }

    override suspend fun setWatched(
        key: ThreadKey,
        watched: Boolean,
    ) {
        state.value = state.value.map { if (it.key == key) it.copy(isWatched = watched) else it }
    }

    override suspend fun markRead(key: ThreadKey) = Unit

    override suspend fun watchedBookmarks(): List<Bookmark> = state.value.filter { it.isWatched }

    override suspend fun updateLatest(
        key: ThreadKey,
        latestReplyCount: Int,
        isThreadDead: Boolean,
    ) = Unit

    override suspend fun getBookmark(key: ThreadKey): Bookmark? = state.value.firstOrNull { it.key == key }
}
