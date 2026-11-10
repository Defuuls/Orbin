package com.orbin.feature.home

import androidx.lifecycle.viewModelScope
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.orbin.core.common.lock.AppLockController
import com.orbin.core.common.result.OrbinResult
import com.orbin.core.model.AppSettings
import com.orbin.core.model.Board
import com.orbin.core.model.BoardId
import com.orbin.core.model.CatalogRequest
import com.orbin.core.model.CatalogThread
import com.orbin.core.model.MediaAttachment
import com.orbin.core.model.MediaType
import com.orbin.core.model.Post
import com.orbin.core.model.PostId
import com.orbin.core.model.ProviderId
import com.orbin.core.model.Thread
import com.orbin.core.model.ThreadId
import com.orbin.core.model.ThreadKey
import com.orbin.core.model.ThreadStats
import com.orbin.core.testing.MainDispatcherRule
import com.orbin.core.testing.repository.FakeBoardPreferencesRepository
import com.orbin.core.testing.repository.FakeHistoryRepository
import com.orbin.core.testing.repository.FakeProviderRegistry
import com.orbin.core.testing.repository.FakeSettingsRepository
import com.orbin.domain.repository.BoardRepository
import com.orbin.domain.usecase.ObserveActiveProviderUseCase
import com.orbin.provider.api.ImageBoardProvider
import com.orbin.provider.api.ProviderCapabilities
import com.orbin.provider.api.ProviderException
import com.orbin.provider.api.ProviderMetadata
import com.orbin.provider.api.ViolentMediaCoverProvider
import io.mockk.mockk
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

/** Provider decorators apply covers while fetching; stale cached attachments cannot substitute. */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeFeedPrivacyTest {
    @get:Rule
    val main = MainDispatcherRule()

    @Test
    fun `turning violent covers on drops uncovered cache until decorated data arrives`() =
        runTest {
            changingCoverRefetches(initial = false, failure = false)
        }

    @Test
    fun `turning violent covers off refetches instead of keeping decorated cache`() =
        runTest {
            changingCoverRefetches(initial = true, failure = false)
        }

    @Test
    fun `policy changes remove old rows before a delayed directory subscription can emit`() =
        runTest {
            changingCoverRefetches(initial = false, failure = false, delayedDirectory = true)
        }

    @Test
    fun `cover enablement cannot fall back to uncovered cache when the request fails`() =
        runTest {
            changingCoverRefetches(initial = false, failure = true)
        }

    @Test
    fun `cover disablement cannot restore a cache from a different policy when the request fails`() =
        runTest {
            changingCoverRefetches(initial = true, failure = true)
        }

    // The shared fixture keeps real provider decoration and delayed-store timing in one place.
    @Suppress("LongMethod")
    private suspend fun TestScope.changingCoverRefetches(
        initial: Boolean,
        failure: Boolean,
        delayedDirectory: Boolean = false,
    ) {
        val settings = FakeSettingsRepository(AppSettings.Default.copy(coverViolentMedia = initial))
        val response = CompletableDeferred<Unit>()
        val directoryReady = CompletableDeferred<Unit>()
        var directorySubscriptions = 0
        var calls = 0
        val base =
            object : ImageBoardProvider {
                override val metadata = ProviderMetadata(ProviderId("fourchan"), "4chan", "https://example.org")
                override val capabilities = ProviderCapabilities()

                override suspend fun getBoards(): List<Board> = emptyList()

                override suspend fun getCatalog(request: CatalogRequest): List<CatalogThread> {
                    calls++
                    if (calls > 1) {
                        response.await()
                        if (failure) throw ProviderException.NotFound("offline")
                    }
                    return listOf(video(request))
                }

                override suspend fun getThread(
                    board: BoardId,
                    thread: ThreadId,
                ): Thread = throw ProviderException.NotFound("unused")
            }
        val decorated = ViolentMediaCoverProvider(base) { settings.settings.first().coverViolentMedia }
        val registry = FakeProviderRegistry(decorated)
        val directory =
            object : BoardRepository {
                private val boards = listOf(Board(BoardId("g"), "Technology"))

                override fun observeBoards(provider: ProviderId): Flow<List<Board>> =
                    flow {
                        directorySubscriptions++
                        if (delayedDirectory && directorySubscriptions > 1) directoryReady.await()
                        emit(boards)
                    }

                override suspend fun refreshBoards(provider: ProviderId): OrbinResult<List<Board>> =
                    OrbinResult.Success(boards)
            }
        val vm =
            SubscribedFeedViewModel(
                registry,
                ObserveActiveProviderUseCase(registry, settings),
                directory,
                FakeBoardPreferencesRepository(),
                settings,
                FakeHistoryRepository(),
                AppLockController(),
                mockk(relaxed = true),
                workDispatcher = main.testDispatcher,
            )
        try {
            vm.uiState.test {
                var current = awaitItem()
                while (current !is SubscribedFeedUiState.Success || current.isLoadingMore) current = awaitItem()
                assertThat(current.coverViolentMedia).isEqualTo(initial)
                assertThat(
                    current.boards
                        .single()
                        .threads
                        .single()
                        .originalPost.attachments
                        .single()
                        .isSpoiler,
                ).isEqualTo(initial)
                assertThat(calls).isEqualTo(1)

                settings.setCoverViolentMedia(!initial)
                runCurrent()
                // Old covered/uncovered rows are removed immediately, before the next fetch
                // resolves; a same-generation board or page cache must not satisfy this load.
                assertThat(vm.uiState.value).isEqualTo(SubscribedFeedUiState.Loading)
                if (delayedDirectory) {
                    assertThat(calls).isEqualTo(1)
                    directoryReady.complete(Unit)
                    runCurrent()
                }
                assertThat(calls).isEqualTo(2)
                response.complete(Unit)
                if (failure) {
                    do {
                        current = awaitItem()
                        if (current is SubscribedFeedUiState.Success) {
                            assertThat(current.boards.flatMap { it.threads }).isEmpty()
                        }
                    } while (current !is SubscribedFeedUiState.Error)
                } else {
                    do {
                        current = awaitItem()
                    } while (current !is SubscribedFeedUiState.Success || current.isLoadingMore)
                    assertThat(current.coverViolentMedia).isEqualTo(!initial)
                    assertThat(
                        current.boards
                            .single()
                            .threads
                            .single()
                            .originalPost.attachments
                            .single()
                            .isSpoiler,
                    ).isEqualTo(!initial)
                }
            }
        } finally {
            vm.viewModelScope.cancel()
        }
    }

    private fun video(request: CatalogRequest): CatalogThread =
        CatalogThread(
            ThreadKey(request.provider, request.board, ThreadId(1)),
            Post(
                id = PostId(1),
                board = request.board,
                threadId = ThreadId(1),
                isOriginalPost = true,
                subject = "fight",
                attachments =
                    persistentListOf(
                        MediaAttachment("clip", "clip.mp4", "mp4", MediaType.VIDEO, "https://example.org/clip.mp4", ""),
                    ),
            ),
            ThreadStats(),
        )
}
