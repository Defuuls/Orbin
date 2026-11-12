package com.orbin.feature.search

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.orbin.core.common.result.DataError
import com.orbin.core.common.result.OrbinResult
import com.orbin.core.model.AppSettings
import com.orbin.core.model.Board
import com.orbin.core.model.BoardId
import com.orbin.core.model.PostId
import com.orbin.core.model.ProviderId
import com.orbin.core.model.SearchQuery
import com.orbin.core.model.SearchResult
import com.orbin.core.model.ThreadId
import com.orbin.core.model.ThreadKey
import com.orbin.core.testing.MainDispatcherRule
import com.orbin.core.testing.repository.FakeBoardPreferencesRepository
import com.orbin.core.testing.repository.FakeBoardRepository
import com.orbin.core.testing.repository.FakeProviderRegistry
import com.orbin.core.testing.repository.FakeSearchRepository
import com.orbin.core.testing.repository.FakeSettingsRepository
import com.orbin.domain.repository.BoardPreferencesRepository
import com.orbin.domain.repository.SearchRepository
import com.orbin.domain.usecase.ObserveActiveProviderUseCase
import com.orbin.provider.api.ImageBoardProvider
import com.orbin.provider.api.ProviderMetadata
import com.orbin.provider.api.ProviderRegistry
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
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
    fun `an empty scope asks for followed boards rather than a separate favorites setup`() =
        runTest {
            val viewModel = createViewModel(FakeSearchRepository(), subscribed = emptySet())

            viewModel.search("kotlin")

            val state = viewModel.state.first { it is SearchUiState.Error } as SearchUiState.Error
            assertThat(state.message).isEqualTo("Follow a few boards to search them")
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
    fun `a search is stored when saveSearchHistory is enabled`() =
        runTest {
            val repo = FakeSearchRepository(listOf(result))
            val viewModel =
                createViewModel(
                    repo,
                    settings = AppSettings.Default.copy(saveSearchHistory = true),
                )
            viewModel.searchableBoards.first { it.isNotEmpty() }

            viewModel.search("kotlin")
            viewModel.state.first { it is SearchUiState.Results }

            assertThat(repo.observeRecentQueries().first()).containsExactly("kotlin")
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

    @Test
    fun `a board that fails leaves the other boards' results`() =
        runTest {
            val boards = listOf(Board(BoardId("g"), "Technology"), Board(BoardId("v"), "Games"))
            val flaky =
                object : SearchRepository by FakeSearchRepository() {
                    override suspend fun search(query: SearchQuery): OrbinResult<List<SearchResult>> =
                        if (query.board == BoardId("g")) {
                            OrbinResult.Failure(DataError.Offline())
                        } else {
                            OrbinResult.Success(listOf(result))
                        }
                }
            val viewModel = createViewModel(flaky, boards = boards, subscribed = boards.map { it.id }.toSet())
            viewModel.searchableBoards.first { it.size == 2 }

            viewModel.search("match")

            val state = viewModel.state.first { it is SearchUiState.Results || it is SearchUiState.Error }
            assertThat(state).isInstanceOf(SearchUiState.Results::class.java)
            assertThat((state as SearchUiState.Results).results.map { it.title }).containsExactly("Match")
        }

    @Test
    fun `favorite and subscribed matching board codes are searched on their own sites`() =
        runTest {
            val firstId = ProviderId("fourchan")
            val secondId = ProviderId("bbwchan")
            val providers =
                listOf(firstId, secondId).map { id ->
                    mockk<ImageBoardProvider> {
                        every { metadata } returns ProviderMetadata(id, id.value, "https://example.org")
                    }
                }
            val registry =
                object : ProviderRegistry {
                    override fun all(): List<ImageBoardProvider> = providers

                    override fun default(): ImageBoardProvider = providers.first()

                    override fun get(id: ProviderId): ImageBoardProvider? = providers.find { it.metadata.id == id }
                }
            val preferences =
                object : BoardPreferencesRepository by FakeBoardPreferencesRepository(emptySet()) {
                    override fun observeSubscribedBoards(provider: ProviderId): Flow<Set<BoardId>> =
                        flowOf(if (provider == firstId) setOf(BoardId("g")) else emptySet())

                    override fun observeFavoriteBoards(provider: ProviderId): Flow<Set<BoardId>> =
                        flowOf(if (provider == secondId) setOf(BoardId("g")) else emptySet())
                }
            val queries = mutableListOf<SearchQuery>()
            val repository =
                object : SearchRepository by FakeSearchRepository() {
                    override suspend fun search(query: SearchQuery): OrbinResult<List<SearchResult>> {
                        queries += query
                        return OrbinResult.Success(
                            listOf(result.copy(key = result.key.copy(provider = query.provider))),
                        )
                    }
                }
            val settings = FakeSettingsRepository()
            val model =
                SearchViewModel(
                    searchRepository = repository,
                    boardRepository = FakeBoardRepository(),
                    boardPreferencesRepository = preferences,
                    settingsRepository = settings,
                    registry = registry,
                    observeActiveProvider = ObserveActiveProviderUseCase(registry, settings),
                )
            model.searchableBoards.first { it.size == 2 }
            model.search("match")
            val found = model.state.first { it is SearchUiState.Results } as SearchUiState.Results

            assertThat(queries.map { it.provider }).containsExactly(firstId, secondId)
            assertThat(queries.map { it.board }).containsExactly(BoardId("g"), BoardId("g"))
            assertThat(found.results.map { it.key.provider }).containsExactly(firstId, secondId)
        }

    @Test
    fun `a board that is both favorite and subscribed is searched once`() =
        runTest {
            val queries = mutableListOf<SearchQuery>()
            val repository =
                object : SearchRepository by FakeSearchRepository() {
                    override suspend fun search(query: SearchQuery): OrbinResult<List<SearchResult>> {
                        queries += query
                        return OrbinResult.Success(emptyList())
                    }
                }
            val model = createViewModel(repository, favorites = setOf(BoardId("g")))
            model.searchableBoards.first { it.isNotEmpty() }
            model.search("match")
            model.state.first { it is SearchUiState.Results }
            assertThat(queries).hasSize(1)
        }

    private fun createViewModel(
        repository: SearchRepository,
        settings: AppSettings = AppSettings.Default,
        boards: List<Board> = listOf(Board(BoardId("g"), "Technology")),
        subscribed: Set<BoardId> = setOf(BoardId("g")),
        favorites: Set<BoardId> = emptySet(),
    ): SearchViewModel {
        val registry = FakeProviderRegistry()
        val settingsRepository = FakeSettingsRepository(settings)
        return SearchViewModel(
            searchRepository = repository,
            boardRepository = FakeBoardRepository(boards),
            boardPreferencesRepository = FakeBoardPreferencesRepository(subscribed, favorites),
            settingsRepository = settingsRepository,
            registry = registry,
            observeActiveProvider = ObserveActiveProviderUseCase(registry, settingsRepository),
        )
    }
}
