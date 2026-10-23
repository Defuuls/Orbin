package com.orbin.feature.onboarding

import com.google.common.truth.Truth.assertThat
import com.orbin.core.common.result.DataError
import com.orbin.core.common.result.OrbinResult
import com.orbin.core.model.Board
import com.orbin.core.model.BoardId
import com.orbin.core.model.FeedThreadLimit
import com.orbin.core.model.ProviderId
import com.orbin.core.testing.MainDispatcherRule
import com.orbin.core.testing.repository.FakeBoardRepository
import com.orbin.core.testing.repository.FakeImageBoardProvider
import com.orbin.core.testing.repository.FakeProviderRegistry
import com.orbin.core.testing.repository.FakeSettingsRepository
import com.orbin.domain.repository.BoardPreferencesRepository
import com.orbin.domain.repository.BoardRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val provider = FakeImageBoardProvider(id = "fourchan")
    private val settings = FakeSettingsRepository()
    private val preferences = RecordingBoardPreferences()

    private fun viewModel(boards: BoardRepository = FakeBoardRepository(listOf(Board(BoardId("g"), "Technology")))) =
        OnboardingViewModel(FakeProviderRegistry(provider), boards, preferences, settings)

    @Test
    fun `the default site's boards load for the picker`() =
        runTest {
            val viewModel = viewModel()

            val loaded = viewModel.boards.first { it is OnboardingBoardsState.Success } as OnboardingBoardsState.Success

            assertThat(loaded.boards.map { it.id.value }).containsExactly("g")
        }

    @Test
    fun `a site that cannot be reached shows why and can be retried`() =
        runTest {
            val failing = FailingThenWorkingBoards()
            val viewModel = viewModel(failing)

            val error = viewModel.boards.first { it is OnboardingBoardsState.Error } as OnboardingBoardsState.Error
            assertThat(error.message).isEqualTo("No network connection")

            viewModel.loadBoards()
            assertThat(viewModel.boards.first { it !is OnboardingBoardsState.Loading })
                .isInstanceOf(OnboardingBoardsState.Success::class.java)
        }

    @Test
    fun `following a board writes it through and shows it as followed`() =
        runTest {
            val viewModel = viewModel()

            viewModel.setSubscribed("g", subscribed = true)
            advanceUntilIdle()

            assertThat(preferences.subscribed.value[provider.metadata.id]).containsExactly(BoardId("g"))
            assertThat(viewModel.subscribedBoardIds.first { it.isNotEmpty() }).containsExactly("g")
        }

    @Test
    fun `starting to browse finishes setup on the chosen site`() =
        runTest {
            val viewModel = viewModel()

            viewModel.complete()
            advanceUntilIdle()

            assertThat(settings.current.onboardingCompleted).isTrue()
            assertThat(settings.current.activeProviderId).isEqualTo("fourchan")
        }

    private class FailingThenWorkingBoards : BoardRepository {
        private var calls = 0

        override fun observeBoards(provider: ProviderId): Flow<List<Board>> = flowOf(emptyList())

        override suspend fun refreshBoards(provider: ProviderId): OrbinResult<List<Board>> =
            if (calls++ == 0) {
                OrbinResult.Failure(DataError.Offline())
            } else {
                OrbinResult.Success(listOf(Board(BoardId("g"), "Technology")))
            }
    }

    /** Keeps what is followed, per site, so a test can see the picker's writes. */
    private class RecordingBoardPreferences : BoardPreferencesRepository {
        val subscribed = MutableStateFlow<Map<ProviderId, Set<BoardId>>>(emptyMap())

        override fun observeFavoriteBoards(provider: ProviderId): Flow<Set<BoardId>> = flowOf(emptySet())

        override fun observeSubscribedBoards(provider: ProviderId): Flow<Set<BoardId>> =
            subscribed.map { it[provider].orEmpty() }

        override suspend fun setFavoriteBoard(
            provider: ProviderId,
            board: BoardId,
            favorite: Boolean,
        ) = Unit

        override suspend fun setSubscribedBoard(
            provider: ProviderId,
            board: BoardId,
            subscribed: Boolean,
        ) {
            this.subscribed.update { all ->
                val current = all[provider].orEmpty()
                all + (provider to if (subscribed) current + board else current - board)
            }
        }

        override fun observeFeedThreadLimit(
            provider: ProviderId,
            board: BoardId,
        ): Flow<FeedThreadLimit?> = flowOf(null)

        override suspend fun setFeedThreadLimit(
            provider: ProviderId,
            board: BoardId,
            limit: FeedThreadLimit?,
        ) = Unit
    }
}
