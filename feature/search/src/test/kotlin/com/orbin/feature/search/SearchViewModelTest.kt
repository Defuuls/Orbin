package com.orbin.feature.search

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.orbin.core.model.AppSettings
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
    fun `search populates results`() =
        runTest {
            val viewModel = createViewModel(FakeSearchRepository(listOf(result)))

            viewModel.search(text = "match", board = "g")

            viewModel.state.test {
                // The terminal state after a successful search holds the results.
                var state = awaitItem()
                while (state !is SearchUiState.Results) state = awaitItem()
                assertThat(state.results.map { it.title }).containsExactly("Match")
            }
        }

    @Test
    fun `blank query is ignored`() =
        runTest {
            val viewModel = createViewModel(FakeSearchRepository(listOf(result)))

            viewModel.search(text = "   ", board = "g")

            viewModel.state.test {
                assertThat(awaitItem()).isEqualTo(SearchUiState.Idle)
            }
        }

    @Test
    fun `searching does not record the recent query by default`() =
        runTest {
            val repo = FakeSearchRepository(listOf(result))
            val viewModel = createViewModel(repo)

            viewModel.search(text = "kotlin", board = "g")

            viewModel.recentQueries.test {
                assertThat(awaitItem()).isEmpty()
            }
        }

    @Test
    fun `searching records the recent query when enabled`() =
        runTest {
            val repo = FakeSearchRepository(listOf(result))
            val viewModel = createViewModel(repo, AppSettings.Default.copy(saveRecentSearches = true))

            viewModel.search(text = "kotlin", board = "g")

            viewModel.recentQueries.test {
                var recents = awaitItem()
                while (recents.isEmpty()) recents = awaitItem()
                assertThat(recents).contains("kotlin")
            }
        }

    private fun createViewModel(
        repository: FakeSearchRepository,
        settings: AppSettings = AppSettings.Default,
    ): SearchViewModel =
        SearchViewModel(
            searchRepository = repository,
            boardRepository = FakeBoardRepository(),
            boardPreferencesRepository = FakeBoardPreferencesRepository(),
            settingsRepository = FakeSettingsRepository(settings),
            registry = FakeProviderRegistry(),
        )
}
