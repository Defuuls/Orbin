package com.orbin.feature.settings

import com.google.common.truth.Truth.assertThat
import com.orbin.core.model.AppIconVariant
import com.orbin.core.model.AppSettings
import com.orbin.core.model.AppThemeMode
import com.orbin.core.model.BoardId
import com.orbin.core.model.Bookmark
import com.orbin.core.model.ColorTheme
import com.orbin.core.model.DohProvider
import com.orbin.core.model.FeedRefreshInterval
import com.orbin.core.model.FeedThreadLimit
import com.orbin.core.model.MediaFilter
import com.orbin.core.model.PreloadOption
import com.orbin.core.model.PreloadThrottleMode
import com.orbin.core.model.ProviderId
import com.orbin.core.model.SavedSearch
import com.orbin.core.model.SearchContentType
import com.orbin.core.model.SearchFilters
import com.orbin.core.model.ThreadId
import com.orbin.core.model.ThreadKey
import com.orbin.core.model.ThreadPresentation
import com.orbin.core.model.ThumbnailSize
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
            personalizedHomeFeed = false,
            hiddenTags = "spoilers",
            mutedTags = "wip",
            hideNsfwBoards = true,
            hideTextOnlyThreads = true,
            mediaFilter = MediaFilter.VIDEOS,
            feedRefreshInterval = FeedRefreshInterval.FIFTEEN_MINUTES,
            threadPresentation = ThreadPresentation.OVERLAY,
            themeMode = AppThemeMode.DARK,
            colorTheme = ColorTheme.TOMORROW_NIGHT,
            dynamicColor = false,
            amoled = true,
            fontScale = 1.2f,
            appIconVariant = AppIconVariant.DUAL_GRADIENT,
            fullScreenFeedChrome = true,
            thumbnailSize = ThumbnailSize.FILL,
            autoplayVideos = true,
            muteByDefault = false,
            fullscreenVideoPlayback = true,
            autoRotateVideoFullscreen = true,
            preloadImages = false,
            preloadOption = PreloadOption.NONE,
            preloadThrottleMode = PreloadThrottleMode.UNLIMITED,
            imageCacheLimitMb = 512,
            feedThreadLimit = FeedThreadLimit.ALL,
            userAgent = "OrbinTest/1.0",
            dohProvider = DohProvider.NEXTDNS,
            connectTimeoutSeconds = 30,
            readTimeoutSeconds = 60,
            disableOcspChecking = false,
            biometricLockEnabled = true,
            saveRecentSearches = true,
            internalUpdaterEnabled = false,
            threadWatchNotificationsEnabled = false,
            quietHoursStart = "23:00",
            quietHoursEnd = "07:00",
            onboardingCompleted = true,
            mediaScrollThreadView = false,
            mediaScrollBoardView = true,
        )

    @Test
    fun everySettingSurvivesExportAndImport() =
        runTest {
            val exported = service(FakeSettingsRepository(populatedSettings)).exportToJson("test")

            val destinationSettings = FakeSettingsRepository()
            service(destinationSettings).importFromJson(exported).getOrThrow()
            val restored = destinationSettings.settings.first()

            // downloadFolderUri and httpsOnly are deliberately not restored; normalise them so the
            // comparison covers every other field without listing them one by one.
            assertThat(restored.copy(downloadFolderUri = "", httpsOnly = true))
                .isEqualTo(populatedSettings.copy(downloadFolderUri = "", httpsOnly = true))
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

    /** Importing must merge, never replace: a restore cannot be allowed to destroy existing data. */
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
    fun aBackupFromANewerFormatIsRejected() =
        runTest {
            val result = service(FakeSettingsRepository()).importFromJson("""{ "formatVersion": 99 }""")

            assertThat(result.isFailure).isTrue()
        }

    private fun service(
        settings: FakeSettingsRepository,
        bookmarks: BookmarkRepository = FakeBookmarkRepository(),
        searches: FakeSearchRepository = FakeSearchRepository(),
    ) = BackupService(
        settings,
        FakeBoardPreferencesRepository(),
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
