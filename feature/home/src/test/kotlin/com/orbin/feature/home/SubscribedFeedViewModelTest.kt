package com.orbin.feature.home

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.orbin.core.common.lock.AppLockController
import com.orbin.core.model.Board
import com.orbin.core.model.BoardId
import com.orbin.core.model.CatalogRequest
import com.orbin.core.model.CatalogThread
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
import com.orbin.provider.api.ImageBoardProvider
import com.orbin.provider.api.ProviderCapabilities
import com.orbin.provider.api.ProviderException
import com.orbin.provider.api.ProviderMetadata
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

/**
 * A single dead/pruned board 404ing must not wipe out every other subscribed board's feed with a
 * blanket error — the whole point of catching per board in [SubscribedFeedViewModel].
 */
class SubscribedFeedViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val healthyBoard = BoardId("g")
    private val deadBoard = BoardId("a")

    @Test
    fun `one board 404ing does not fail the whole feed`() =
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
                )

            viewModel.uiState.test {
                var state = awaitItem()
                while (state !is SubscribedFeedUiState.Success) state = awaitItem()

                assertThat(state.boards.map { it.board.id }).containsExactly(healthyBoard, deadBoard)
                assertThat(state.boards.first { it.board.id == healthyBoard }.threads).hasSize(1)
                assertThat(state.boards.first { it.board.id == deadBoard }.threads).isEmpty()
            }
        }
}
