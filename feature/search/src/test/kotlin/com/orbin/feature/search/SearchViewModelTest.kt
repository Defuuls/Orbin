package com.orbin.feature.search

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.orbin.core.model.BoardId
import com.orbin.core.model.PostId
import com.orbin.core.model.ProviderId
import com.orbin.core.model.SearchResult
import com.orbin.core.model.ThreadId
import com.orbin.core.model.ThreadKey
import com.orbin.core.testing.MainDispatcherRule
import com.orbin.core.testing.repository.FakeProviderRegistry
import com.orbin.core.testing.repository.FakeSearchRepository
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
            val viewModel = SearchViewModel(FakeSearchRepository(listOf(result)), FakeProviderRegistry())

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
            val viewModel = SearchViewModel(FakeSearchRepository(listOf(result)), FakeProviderRegistry())

            viewModel.search(text = "   ", board = "g")

            viewModel.state.test {
                assertThat(awaitItem()).isEqualTo(SearchUiState.Idle)
            }
        }

    @Test
    fun `searching records the recent query`() =
        runTest {
            val repo = FakeSearchRepository(listOf(result))
            val viewModel = SearchViewModel(repo, FakeProviderRegistry())

            viewModel.search(text = "kotlin", board = "g")

            viewModel.recentQueries.test {
                var recents = awaitItem()
                while (recents.isEmpty()) recents = awaitItem()
                assertThat(recents).contains("kotlin")
            }
        }
}
