package com.orbin.feature.search

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.orbin.core.model.AppSettings
import com.orbin.core.model.Board
import com.orbin.core.model.BoardId
import com.orbin.core.model.PostId
import com.orbin.core.model.ProviderId
import com.orbin.core.model.SearchResult
import com.orbin.core.model.ThreadId
import com.orbin.core.model.ThreadKey
import com.orbin.core.testing.MainDispatcherRule
import com.orbin.core.testing.repository.FakeBoardPreferencesRepository
import com.orbin.core.testing.repository.FakeBoardRepository
import com.orbin.core.testing.repository.FakeProviderRegistry
import com.orbin.core.testing.repository.FakeSearchRepository
import com.orbin.core.testing.repository.FakeSettingsRepository
import com.orbin.domain.usecase.ObserveActiveProviderUseCase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

class SearchViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val result =
        SearchResult(
            key = ThreadKey(ProviderId("fourchan"), BoardId("g"), ThreadId(42)),
            title = "Match",
            snippet = "a match",
            matchedPost = PostId(42),
        )

    @Test
    fun `search populates results from the boards you follow`() =
        runTest {
            val viewModel = createViewModel(FakeSearchRepository(listOf(result)))
            viewModel.searchableBoards.first { it.isNotEmpty() }

            viewModel.search("match")

            viewModel.state.test {
                var state = awaitItem()
                while (state !is SearchUiState.Results) state = awaitItem()
                assertThat(state.results.map { it.title }).containsExactly("Match")
            }
        }

    @Test
    fun `blank query is ignored`() =
        runTest {
            val viewModel = createViewModel(FakeSearchRepository(listOf(result)))

            viewModel.search("   ")

            viewModel.state.test {
                assertThat(awaitItem()).isEqualTo(SearchUiState.Idle)
            }
        }

    @Test
    fun `a search is never stored`() =
        runTest {
            val repo = FakeSearchRepository(listOf(result))
            val viewModel = createViewModel(repo)
            viewModel.searchableBoards.first { it.isNotEmpty() }

            viewModel.search("kotlin")
            viewModel.state.first { it is SearchUiState.Results }

            assertThat(repo.observeRecentQueries().first()).isEmpty()
        }

    @Test
    fun `nsfw boards are skipped when settings hides them`() =
        runTest {
            val boards = listOf(Board(BoardId("g"), "Technology"), Board(BoardId("b"), "Random", isNsfw = true))
            val subscribed = setOf(BoardId("g"), BoardId("b"))

            val hiding =
                createViewModel(
                    FakeSearchRepository(),
                    AppSettings.Default.copy(hideNsfwBoards = true),
                    boards,
                    subscribed,
                )
            val showing = createViewModel(FakeSearchRepository(), AppSettings.Default, boards, subscribed)

            assertThat(hiding.searchableBoards.first { it.isNotEmpty() }.map { it.id.value }).containsExactly("g")
            assertThat(showing.searchableBoards.first { it.size == 2 }.map { it.id.value }).containsExactly("b", "g")
        }

    private fun createViewModel(
        repository: FakeSearchRepository,
        settings: AppSettings = AppSettings.Default,
        boards: List<Board> = listOf(Board(BoardId("g"), "Technology")),
        subscribed: Set<BoardId> = setOf(BoardId("g")),
    ): SearchViewModel {
        val registry = FakeProviderRegistry()
        val settingsRepository = FakeSettingsRepository(settings)
        return SearchViewModel(
            searchRepository = repository,
            boardRepository = FakeBoardRepository(boards),
            boardPreferencesRepository = FakeBoardPreferencesRepository(subscribed),
            settingsRepository = settingsRepository,
            registry = registry,
            observeActiveProvider = ObserveActiveProviderUseCase(registry, settingsRepository),
        )
    }
}
