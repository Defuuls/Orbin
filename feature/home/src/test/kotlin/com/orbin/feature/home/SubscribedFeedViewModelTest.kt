package com.orbin.feature.home

import androidx.lifecycle.ViewModelStore
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.orbin.core.common.lock.AppLockController
import com.orbin.core.model.Board
import com.orbin.core.model.BoardId
import com.orbin.core.model.CatalogMode
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
import com.orbin.core.testing.repository.FakeBoardRepository
import com.orbin.core.testing.repository.FakeHistoryRepository
import com.orbin.core.testing.repository.FakeProviderRegistry
import com.orbin.core.testing.repository.FakeSettingsRepository
import com.orbin.domain.usecase.ObserveActiveProviderUseCase
import com.orbin.media.ImagePreloader
import com.orbin.provider.api.ImageBoardProvider
import com.orbin.provider.api.ProviderCapabilities
import com.orbin.provider.api.ProviderException
import com.orbin.provider.api.ProviderMetadata
import com.orbin.provider.api.ProviderRegistry
import io.mockk.every
import io.mockk.mockk
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.CompletableJob
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

/**
 * A single dead/pruned board must not wipe out every other subscribed board's feed.
 */
class SubscribedFeedViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val healthyBoard = BoardId("g")
    private val deadBoard = BoardId("a")

    @Test
    fun `one board failing keeps healthy boards and reports partial failure`() =
        runTest {
            val provider =
                object : ImageBoardProvider {
                    override val metadata = ProviderMetadata(ProviderId("fourchan"), "Test", "https://example.org")
                    override val capabilities = ProviderCapabilities()

                    override suspend fun getBoards(): List<Board> = emptyList()

                    override suspend fun getCatalog(request: CatalogRequest): List<CatalogThread> =
                        when (request.board) {
                            deadBoard -> throw ProviderException.NotFound("Resource not found")
                            else ->
                                listOf(
                                    CatalogThread(
                                        key = ThreadKey(ProviderId("fourchan"), healthyBoard, ThreadId(1)),
                                        originalPost =
                                            Post(
                                                id = PostId(1),
                                                board = healthyBoard,
                                                threadId = ThreadId(1),
                                                isOriginalPost = true,
                                            ),
                                        stats = ThreadStats(),
                                    ),
                                )
                        }

                    override suspend fun getThread(
                        board: BoardId,
                        thread: ThreadId,
                    ): Thread = throw ProviderException.NotFound("not used")
                }
            val registry = FakeProviderRegistry(provider)
            val settingsRepository = FakeSettingsRepository()

            val viewModel =
                SubscribedFeedViewModel(
                    registry = registry,
                    observeActiveProvider = ObserveActiveProviderUseCase(registry, settingsRepository),
                    boardRepository =
                        FakeBoardRepository(
                            boards = listOf(Board(healthyBoard, "Technology"), Board(deadBoard, "Anime")),
                        ),
                    boardPreferencesRepository =
                        FakeBoardPreferencesRepository(subscribed = setOf(healthyBoard, deadBoard)),
                    settingsRepository = settingsRepository,
                    historyRepository = FakeHistoryRepository(),
                    appLockController = AppLockController(),
                    imagePreloader = mockk(relaxed = true),
                    workDispatcher = mainDispatcherRule.testDispatcher,
                )

            viewModel.uiState.test {
                var state = awaitItem()
                while (state !is SubscribedFeedUiState.Success || state.isLoadingMore) state = awaitItem()

                assertThat(state.boards.map { it.board.id }).containsExactly(healthyBoard, deadBoard)
                assertThat(state.boards.first { it.board.id == healthyBoard }.threads).hasSize(1)
                assertThat(state.boards.first { it.board.id == deadBoard }.threads).isEmpty()
                assertThat(state.failedBoards).containsExactly(deadBoard)
                assertThat(state.stale).isTrue()
            }
        }

    @Test
    fun `maximum amount of threads loads from each board by default`() =
        runTest {
            val manyThreads = (1L..25L).map { catalogThread(it, attachment("img$it", MediaType.IMAGE)) }
            val provider = catalogProvider(manyThreads)
            val registry = FakeProviderRegistry(provider)
            val settingsRepository = FakeSettingsRepository()

            val viewModel = createViewModel(registry, settingsRepository, subscribed = setOf(healthyBoard))

            viewModel.uiState.test {
                var state = awaitItem()
                while (state !is SubscribedFeedUiState.Success || state.isLoadingMore) state = awaitItem()

                val threads = state.boards.single().threads
                assertThat(threads).hasSize(25)
            }
        }

    @Test
    fun `favorite only boards never populate Home or request their catalogs`() =
        runTest {
            val catalogCalls = AtomicInteger()
            val provider = catalogProvider(listOf(catalogThread(1L)), catalogCalls)
            val registry = FakeProviderRegistry(provider)
            val settingsRepository = FakeSettingsRepository()
            val viewModel =
                SubscribedFeedViewModel(
                    registry = registry,
                    observeActiveProvider = ObserveActiveProviderUseCase(registry, settingsRepository),
                    boardRepository = FakeBoardRepository(),
                    boardPreferencesRepository =
                        FakeBoardPreferencesRepository(subscribed = emptySet(), favorites = setOf(healthyBoard)),
                    settingsRepository = settingsRepository,
                    historyRepository = FakeHistoryRepository(),
                    appLockController = AppLockController(),
                    imagePreloader = mockk(relaxed = true),
                    workDispatcher = mainDispatcherRule.testDispatcher,
                )

            viewModel.uiState.test {
                var state = awaitItem()
                while (state !is SubscribedFeedUiState.Success || state.isLoadingMore) state = awaitItem()
                assertThat(state.boards).isEmpty()
                assertThat(catalogCalls.get()).isEqualTo(0)
            }
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `returning to the feed reuses it and only a refresh fetches the catalogs again`() =
        runTest {
            val catalogCalls = AtomicInteger()
            val provider = catalogProvider(listOf(catalogThread(1L, attachment("jpg", MediaType.IMAGE))), catalogCalls)
            val registry = FakeProviderRegistry(provider)
            val viewModel = createViewModel(registry, FakeSettingsRepository(), subscribed = setOf(healthyBoard))

            viewModel.uiState.test {
                var state = awaitItem()
                while (state !is SubscribedFeedUiState.Success || state.isLoadingMore) state = awaitItem()
                cancelAndIgnoreRemainingEvents()
            }
            val callsAfterFirstLoad = catalogCalls.get()
            assertThat(callsAfterFirstLoad).isGreaterThan(0)

            // Leave the feed long enough for the shared state to stop, then come back to it.
            advanceTimeBy(LEAVE_FEED_MS)
            viewModel.uiState.test {
                assertThat(awaitItem()).isInstanceOf(SubscribedFeedUiState.Success::class.java)
                advanceUntilIdle()
                cancelAndIgnoreRemainingEvents()
            }
            assertThat(catalogCalls.get()).isEqualTo(callsAfterFirstLoad)

            viewModel.uiState.test {
                viewModel.refresh()
                advanceUntilIdle()
                cancelAndIgnoreRemainingEvents()
            }
            assertThat(catalogCalls.get()).isGreaterThan(callsAfterFirstLoad)
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `reader mode is persisted for a later Home without fetching its catalogs again`() =
        runTest {
            val catalogCalls = AtomicInteger()
            val registry = FakeProviderRegistry(catalogProvider(listOf(catalogThread(1L)), catalogCalls))
            val settings = FakeSettingsRepository()
            val model = createViewModel(registry, settings, subscribed = setOf(healthyBoard))

            model.uiState.test {
                var loaded = awaitItem()
                while (loaded !is SubscribedFeedUiState.Success || loaded.isLoadingMore) loaded = awaitItem()
                val callsAfterLoad = catalogCalls.get()

                model.setCatalogMode(CatalogMode.GRID)
                advanceUntilIdle()

                assertThat(settings.settings.first().catalogMode).isEqualTo(CatalogMode.GRID)
                assertThat(catalogCalls.get()).isEqualTo(callsAfterLoad)
                assertThat(model.uiState.value).isSameInstanceAs(loaded)
            }

            val laterHome = createViewModel(registry, settings, subscribed = setOf(healthyBoard))
            assertThat(laterHome.settings.first { it.catalogMode == CatalogMode.GRID }.catalogMode)
                .isEqualTo(CatalogMode.GRID)
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `combined feed loads both sites and preserves a failed site's own cached board`() =
        runTest {
            var secondSiteFails = false
            val first = catalogProvider(listOf(catalogThread(1L)), providerId = "fourchan")
            val second =
                catalogProvider(
                    listOf(catalogThread(2L)),
                    providerId = "bbwchan",
                    fail = { secondSiteFails },
                )
            val providers = listOf(first, second)
            val registry =
                object : ProviderRegistry {
                    override fun all(): List<ImageBoardProvider> = providers

                    override fun default(): ImageBoardProvider = first

                    override fun get(id: ProviderId): ImageBoardProvider? = providers.find { it.metadata.id == id }
                }
            val viewModel = createViewModel(registry, FakeSettingsRepository(), setOf(healthyBoard))

            viewModel.uiState.test {
                advanceUntilIdle()
                val initial = expectMostRecentItem() as SubscribedFeedUiState.Success
                assertThat(initial.boards.map { it.providerId }).containsExactly("fourchan", "bbwchan")
                assertThat(initial.boards.flatMap { it.threads }.map { it.key.provider.value })
                    .containsExactly("fourchan", "bbwchan")

                secondSiteFails = true
                viewModel.refresh()
                advanceUntilIdle()
                val refreshed = expectMostRecentItem() as SubscribedFeedUiState.Success
                val byProvider = refreshed.boards.associateBy { it.providerId }
                assertThat(
                    byProvider
                        .getValue("fourchan")
                        .threads
                        .single()
                        .key.thread.value,
                ).isEqualTo(1L)
                // Both sites use /g/: the failed source must never receive the other site's cache.
                assertThat(
                    byProvider
                        .getValue("bbwchan")
                        .threads
                        .single()
                        .key.thread.value,
                ).isEqualTo(2L)
                assertThat(refreshed.stale).isTrue()
            }
        }

    @Test
    fun `rapid source viewport changes retain only one thumbnail batch and clear it when leaving`() =
        runTest {
            val pending = mutableListOf<Pair<List<String>, CompletableJob>>()
            val imagePreloader = mockk<ImagePreloader>()
            every { imagePreloader.prefetchBatch(any(), any(), any()) } answers {
                Job().also { pending += firstArg<List<String>>() to it }
            }
            val registry = FakeProviderRegistry(catalogProvider(emptyList()))
            val viewModel = createViewModel(registry, FakeSettingsRepository(), emptySet(), imagePreloader)
            val firstViewport = listOf("https://i.4cdn.org/g/41s.jpg")
            val secondViewport = listOf("https://bbw.example/gen/41s.jpg")

            viewModel.prefetchFeedThumbs(firstViewport)
            viewModel.prefetchFeedThumbs(firstViewport)
            assertThat(pending).hasSize(1)
            assertThat(pending.single().second.isActive).isTrue()

            viewModel.prefetchFeedThumbs(secondViewport)
            assertThat(pending.last().first).isEqualTo(secondViewport)
            assertThat(pending.first().second.isCancelled).isTrue()
            assertThat(pending.count { it.second.isActive }).isEqualTo(1)

            // The screen's pause/dispose hook cancels pending I/O without waiting for the VM to die.
            viewModel.clearFeedImagePrefetch()
            assertThat(pending.count { it.second.isActive }).isEqualTo(0)
            viewModel.prefetchFeedThumbs(secondViewport)
            assertThat(pending.count { it.second.isActive }).isEqualTo(1)

            val store = ViewModelStore()
            store.put("feed", viewModel)
            store.clear()
            assertThat(pending.count { it.second.isActive }).isEqualTo(0)
        }

    private fun createViewModel(
        registry: ProviderRegistry,
        settingsRepository: FakeSettingsRepository,
        subscribed: Set<BoardId>,
        imagePreloader: ImagePreloader = mockk(relaxed = true),
    ) = SubscribedFeedViewModel(
        registry = registry,
        observeActiveProvider = ObserveActiveProviderUseCase(registry, settingsRepository),
        boardRepository = FakeBoardRepository(boards = subscribed.map { Board(it, "Board ${it.value}") }),
        boardPreferencesRepository = FakeBoardPreferencesRepository(subscribed = subscribed),
        settingsRepository = settingsRepository,
        historyRepository = FakeHistoryRepository(),
        appLockController = AppLockController(),
        imagePreloader = imagePreloader,
        workDispatcher = mainDispatcherRule.testDispatcher,
    )

    /** A provider that answers every catalog request with [threads]. */
    private fun catalogProvider(
        threads: List<CatalogThread>,
        catalogCalls: AtomicInteger = AtomicInteger(),
        providerId: String = "fourchan",
        fail: () -> Boolean = { false },
    ) = object : ImageBoardProvider {
        override val metadata = ProviderMetadata(ProviderId(providerId), "Test", "https://example.org")
        override val capabilities = ProviderCapabilities()

        override suspend fun getBoards(): List<Board> = emptyList()

        override suspend fun getCatalog(request: CatalogRequest): List<CatalogThread> {
            catalogCalls.incrementAndGet()
            if (fail()) throw ProviderException.NotFound("Unavailable")
            return threads.map { it.copy(key = it.key.copy(provider = metadata.id)) }
        }

        override suspend fun getThread(
            board: BoardId,
            thread: ThreadId,
        ): Thread = throw ProviderException.NotFound("not used")
    }

    private fun catalogThread(
        id: Long,
        vararg attachments: MediaAttachment,
    ) = CatalogThread(
        key = ThreadKey(ProviderId("fourchan"), healthyBoard, ThreadId(id)),
        originalPost =
            Post(
                id = PostId(id),
                board = healthyBoard,
                threadId = ThreadId(id),
                isOriginalPost = true,
                attachments = attachments.toList().toPersistentList(),
            ),
        stats = ThreadStats(),
    )

    private fun attachment(
        id: String,
        type: MediaType,
    ) = MediaAttachment(
        id = id,
        originalFileName = "$id.file",
        extension = "file",
        type = type,
        sourceUrl = "https://example.org/$id",
        thumbnailUrl = "https://example.org/$id/thumb",
    )

    private companion object {
        /** Longer than the view model's stop timeout, so its shared state really stops. */
        const val LEAVE_FEED_MS = 10_000L
    }
}
