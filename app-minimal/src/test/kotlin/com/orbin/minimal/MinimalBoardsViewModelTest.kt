package com.orbin.minimal

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.orbin.core.common.result.DataError
import com.orbin.core.common.result.OrbinResult
import com.orbin.core.model.Board
import com.orbin.core.model.BoardId
import com.orbin.core.model.FeedThreadLimit
import com.orbin.core.model.ProviderId
import com.orbin.core.testing.MainDispatcherRule
import com.orbin.core.testing.repository.FakeProviderRegistry
import com.orbin.core.testing.repository.FakeSettingsRepository
import com.orbin.domain.repository.BoardPreferencesRepository
import com.orbin.domain.repository.BoardRepository
import com.orbin.domain.usecase.ObserveActiveProviderUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

class MinimalBoardsViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `catalogue is sorted and subscription state is joined`() =
        runTest {
            val repository = MutableBoardRepository(listOf(BOARD_Z, BOARD_A))
            val preferences = MutableBoardPreferences(setOf(BOARD_A.id))
            val viewModel = createViewModel(repository, preferences)

            viewModel.uiState.test {
                val success = awaitState { it.boards.size == 2 && !it.isRefreshing }
                assertThat(success.boards.map { it.board.id.value }).containsExactly("a", "z").inOrder()
                assertThat(success.boards.first().isSubscribed).isTrue()
                assertThat(success.boards.last().isSubscribed).isFalse()
            }
        }

    @Test
    fun `failed refresh keeps cached boards visible and reports the failure`() =
        runTest {
            val repository = MutableBoardRepository(listOf(BOARD_A), refreshResult = OrbinResult.Failure(DataError.Offline()))
            val viewModel = createViewModel(repository, MutableBoardPreferences(setOf(BOARD_A.id)))

            viewModel.uiState.test {
                val failed = awaitState { !it.isRefreshing && it.refreshError != null }
                assertThat(failed.boards.map { it.board }).containsExactly(BOARD_A)
                assertThat(failed.refreshError).isEqualTo("No network connection")
            }
        }

    @Test
    fun `subscription state is unresolved until the backing flow emits`() =
        runTest {
            val preferences = MutableBoardPreferences(initialSubscribed = null)
            val viewModel = createViewModel(MutableBoardRepository(listOf(BOARD_A)), preferences)

            viewModel.hasSubscriptions.test {
                assertThat(awaitItem()).isNull()
                preferences.emitSubscribed(emptySet())
                assertThat(awaitItem()).isFalse()
                preferences.emitSubscribed(setOf(BOARD_A.id))
                assertThat(awaitItem()).isTrue()
            }
        }

    @Test
    fun `subscription write failure is surfaced without corrupting the list`() =
        runTest {
            val preferences = MutableBoardPreferences(setOf(BOARD_A.id), failWrites = true)
            val viewModel = createViewModel(MutableBoardRepository(listOf(BOARD_A)), preferences)

            viewModel.uiState.test {
                awaitState { !it.isRefreshing && it.boards.isNotEmpty() }
                viewModel.setSubscribed(BOARD_A.id, false)
                val failed = awaitState { it.subscriptionError != null }
                assertThat(failed.boards.single().isSubscribed).isTrue()
                assertThat(failed.subscriptionError).contains("subscription")
            }
        }

    private fun createViewModel(
        boardRepository: BoardRepository,
        preferences: BoardPreferencesRepository,
    ): MinimalBoardsViewModel {
        val registry = FakeProviderRegistry()
        val settings = FakeSettingsRepository()
        return MinimalBoardsViewModel(
            providerRegistry = registry,
            observeActiveProvider = ObserveActiveProviderUseCase(registry, settings),
            boardRepository = boardRepository,
            boardPreferencesRepository = preferences,
        )
    }

    private suspend fun app.cash.turbine.ReceiveTurbine<MinimalBoardsUiState>.awaitState(
        predicate: (MinimalBoardsUiState) -> Boolean,
    ): MinimalBoardsUiState {
        var item = awaitItem()
        while (!predicate(item)) item = awaitItem()
        return item
    }

    private class MutableBoardRepository(
        initialBoards: List<Board>,
        private var refreshResult: OrbinResult<List<Board>> = OrbinResult.Success(initialBoards),
    ) : BoardRepository {
        private val boards = MutableStateFlow(initialBoards)

        override fun observeBoards(provider: ProviderId): Flow<List<Board>> = boards

        override suspend fun refreshBoards(provider: ProviderId): OrbinResult<List<Board>> = refreshResult
    }

    private class MutableBoardPreferences(
        initialSubscribed: Set<BoardId>?,
        private val failWrites: Boolean = false,
    ) : BoardPreferencesRepository {
        private val subscribed = MutableStateFlow(initialSubscribed)

        fun emitSubscribed(value: Set<BoardId>) {
            subscribed.value = value
        }

        override fun observeFavoriteBoards(provider: ProviderId): Flow<Set<BoardId>> =
            MutableStateFlow(emptySet())

        override fun observeSubscribedBoards(provider: ProviderId): Flow<Set<BoardId>> =
            subscribed.map { it ?: emptySet() }

        override suspend fun setFavoriteBoard(provider: ProviderId, board: BoardId, favorite: Boolean) = Unit

        override suspend fun setSubscribedBoard(provider: ProviderId, board: BoardId, subscribed: Boolean) {
            if (failWrites) error("Could not update subscription")
            val current = this.subscribed.value ?: emptySet()
            this.subscribed.value = if (subscribed) current + board else current - board
        }

        override fun observeFeedThreadLimit(provider: ProviderId, board: BoardId): Flow<FeedThreadLimit?> =
            MutableStateFlow(null)

        override suspend fun setFeedThreadLimit(
            provider: ProviderId,
            board: BoardId,
            limit: FeedThreadLimit?,
        ) = Unit
    }

    private companion object {
        val BOARD_A = Board(BoardId("a"), "Anime")
        val BOARD_Z = Board(BoardId("z"), "Zed")
    }
}
