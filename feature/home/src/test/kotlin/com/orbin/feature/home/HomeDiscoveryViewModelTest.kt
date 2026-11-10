package com.orbin.feature.home

import androidx.lifecycle.viewModelScope
import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.orbin.core.common.lock.AppLockController
import com.orbin.core.common.result.DataError
import com.orbin.core.common.result.OrbinResult
import com.orbin.core.model.AppSettings
import com.orbin.core.model.Board
import com.orbin.core.model.BoardId
import com.orbin.core.model.CatalogRequest
import com.orbin.core.model.CatalogThread
import com.orbin.core.model.HomeFeedMode
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
import com.orbin.core.testing.repository.FakeSettingsRepository
import com.orbin.domain.repository.BoardPreferencesRepository
import com.orbin.domain.repository.BoardRepository
import com.orbin.domain.repository.SettingsRepository
import com.orbin.domain.usecase.ObserveActiveProviderUseCase
import com.orbin.provider.api.ImageBoardProvider
import com.orbin.provider.api.ProviderCapabilities
import com.orbin.provider.api.ProviderException
import com.orbin.provider.api.ProviderMetadata
import com.orbin.provider.api.ProviderRegistry
import io.mockk.mockk
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeDiscoveryViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    @Test
    fun `For You discovers unfollowed boards while Following uses subscriptions and favorites`() =
        runTest {
            val directory = listOf(board("a"), board("g"), board("p"))
            val requested = mutableListOf<BoardId>()
            val preferences = FakeBoardPreferencesRepository(setOf(BoardId("g")), setOf(BoardId("p")))
            val vm = viewModel(directory, preferences = preferences, provider = provider { requested += it.board })
            try {
                vm.uiState.test {
                    assertThat(completePage().boards.map { it.board.id.value }).containsExactly("a", "g", "p")
                    assertThat(requested).hasSize(3)

                    // Changing what is followed must not silently narrow or refetch discovery.
                    preferences.setSubscribedBoard(ProviderId("fourchan"), BoardId("a"), true)
                    advanceUntilIdle()
                    assertThat(requested).hasSize(3)
                    expectMostRecentItem()

                    vm.setFeedMode(HomeFeedMode.FOLLOWING)
                    val following = completePage(HomeFeedMode.FOLLOWING)
                    assertThat(following.mode).isEqualTo(HomeFeedMode.FOLLOWING)
                    assertThat(following.boards.map { it.board.id.value }).containsExactly("a", "g", "p")
                    preferences.setSubscribedBoard(ProviderId("fourchan"), BoardId("a"), false)
                    val narrowed = completePage()
                    assertThat(narrowed.boards.map { it.board.id.value }).containsExactly("g", "p")
                    assertThat(narrowed.boards.flatMap { it.threads }).hasSize(2)
                    // These are followed boards with no watched threads or bookmark state at all.
                    assertThat(requested).hasSize(3)
                }
            } finally {
                vm.viewModelScope.cancel()
            }
        }

    @Test
    fun `discovery loads small pages and reuses earlier catalogs`() =
        runTest {
            val directory = (0..17).map { board("b%02d".format(it)) }
            val requested = mutableListOf<BoardId>()
            val vm = viewModel(directory, provider = provider { requested += it.board })
            try {
                vm.uiState.test {
                    val first = completePage()
                    assertThat(first.boards).hasSize(8)
                    assertThat(first.canLoadMore).isTrue()
                    assertThat(requested).hasSize(8)

                    vm.loadMoreDiscovery()
                    assertThat(completePage().boards).hasSize(16)
                    assertThat(requested).hasSize(16)
                    vm.loadMoreDiscovery()
                    val last = completePage()
                    assertThat(last.boards).hasSize(18)
                    assertThat(last.canLoadMore).isFalse()
                    assertThat(requested.distinct()).hasSize(18)
                    assertThat(requested).hasSize(18)
                    vm.loadMoreDiscovery()
                    advanceUntilIdle()
                    assertThat(requested).hasSize(18)
                }
            } finally {
                vm.viewModelScope.cancel()
            }
        }

    @Test
    fun `returning from Following retains the discovered window without fetching it again`() =
        runTest {
            val requested = mutableListOf<BoardId>()
            val vm =
                viewModel(
                    (0..23).map { board("b%02d".format(it)) },
                    preferences = FakeBoardPreferencesRepository(setOf(BoardId("b00"))),
                    provider = provider { requested += it.board },
                )
            try {
                vm.uiState.test {
                    assertThat(completePage().boards).hasSize(8)
                    vm.loadMoreDiscovery()
                    assertThat(completePage().boards).hasSize(16)
                    assertThat(requested).hasSize(16)
                    vm.setFeedMode(HomeFeedMode.FOLLOWING)
                    assertThat(completePage(HomeFeedMode.FOLLOWING).boards).hasSize(1)
                    vm.setFeedMode(HomeFeedMode.FOR_YOU)
                    assertThat(completePage(HomeFeedMode.FOR_YOU).boards).hasSize(16)
                    assertThat(requested).hasSize(16)
                    // A source choice is a new discovery context and starts its own small page.
                    vm.setProviderFilter("fourchan")
                    var selectedSource = completePage()
                    while (selectedSource.providerFilter != "fourchan") selectedSource = completePage()
                    assertThat(selectedSource.boards).hasSize(8)
                    assertThat(requested).hasSize(16)
                }
            } finally {
                vm.viewModelScope.cancel()
            }
        }

    @Test
    fun `refreshing one source leaves another source's catalog cache fresh`() =
        runTest {
            val calls = mutableListOf<ProviderId>()
            val first = provider("fourchan") { calls += it.provider }
            val second = provider("bbwchan") { calls += it.provider }
            val vm = viewModel(listOf(board("g"), board("p")), provider = first, providers = listOf(first, second))
            try {
                vm.uiState.test {
                    completePage()
                    assertThat(calls.count { it.value == "bbwchan" }).isEqualTo(2)
                    vm.setProviderFilter("fourchan")
                    completePage()
                    vm.refresh()
                    completePage()
                    assertThat(calls.count { it.value == "fourchan" }).isEqualTo(4)
                    vm.setProviderFilter("bbwchan")
                    val secondSource = completePage()
                    assertThat(secondSource.providerFilter).isEqualTo("bbwchan")
                    assertThat(calls.count { it.value == "bbwchan" }).isEqualTo(2)
                }
            } finally {
                vm.viewModelScope.cancel()
            }
        }

    @Test
    fun `a queued refresh retains the source selected when it was requested`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val metadataRequests = mutableListOf<ProviderId>()
            val calls = mutableListOf<ProviderId>()
            val first = provider("fourchan") { calls += it.provider }
            val second = provider("bbwchan") { calls += it.provider }
            val directory = listOf(board("g"))
            val repository =
                object : BoardRepository {
                    override fun observeBoards(provider: ProviderId): Flow<List<Board>> = flowOf(directory)

                    override suspend fun refreshBoards(provider: ProviderId): OrbinResult<List<Board>> {
                        metadataRequests += provider
                        return OrbinResult.Success(directory)
                    }
                }
            val vm = viewModel(directory, provider = first, providers = listOf(first, second), repository = repository)
            try {
                vm.uiState.test {
                    runCurrent()
                    completePage()
                    vm.setProviderFilter("fourchan")
                    runCurrent()
                    completePage()
                    metadataRequests.clear()
                    vm.refresh()
                    vm.setProviderFilter("bbwchan")
                    runCurrent()
                    val selected = completePage()
                    assertThat(selected.providerFilter).isEqualTo("bbwchan")
                    assertThat(metadataRequests).containsExactly(ProviderId("fourchan"))
                    assertThat(calls.count { it.value == "bbwchan" }).isEqualTo(1)
                }
            } finally {
                vm.viewModelScope.cancel()
                Dispatchers.setMain(main.testDispatcher)
            }
        }

    @Test
    fun `initial discovery includes an outsider even when the reader follows more than one page`() =
        runTest {
            val familiar = (0..9).map { board("a%02d".format(it)) }
            val outsider = board("z")
            val requested = mutableListOf<BoardId>()
            val vm =
                viewModel(
                    familiar + outsider,
                    preferences = FakeBoardPreferencesRepository(familiar.mapTo(mutableSetOf()) { it.id }),
                    provider = provider { requested += it.board },
                )
            try {
                vm.uiState.test {
                    val discovery = completePage()
                    assertThat(discovery.boards).hasSize(8)
                    assertThat(requested).contains(outsider.id)
                    vm.setFeedMode(HomeFeedMode.FOLLOWING)
                    val following = completePage(HomeFeedMode.FOLLOWING)
                    assertThat(following.boards).hasSize(10)
                    assertThat(following.boards.map { it.board.id }).doesNotContain(outsider.id)
                }
            } finally {
                vm.viewModelScope.cancel()
            }
        }

    @Test
    fun `repeated near-end requests before state catches up request only one next page`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val requested = mutableListOf<BoardId>()
            val vm = viewModel((0..31).map { board("b%02d".format(it)) }, provider = provider { requested += it.board })
            try {
                vm.uiState.test {
                    runCurrent()
                    assertThat(completePage().boards).hasSize(8)
                    vm.loadMoreDiscovery()
                    vm.loadMoreDiscovery()
                    vm.loadMoreDiscovery()
                    runCurrent()
                    assertThat(completePage().boards).hasSize(16)
                    assertThat(requested).hasSize(16)
                }
            } finally {
                vm.viewModelScope.cancel()
                Dispatchers.setMain(main.testDispatcher)
            }
        }

    @Test
    fun `failed catalogs are requested once per refresh generation while discovery pages expand`() =
        runTest {
            var unavailable = true
            val calls = mutableListOf<BoardId>()
            val vm =
                viewModel(
                    (0..15).map { board("b%02d".format(it)) },
                    provider =
                        provider {
                            calls += it.board
                            if (unavailable && it.board.value < "b07") throw ProviderException.NotFound("offline")
                        },
                )
            try {
                vm.uiState.test {
                    assertThat(completePage().failedBoards).hasSize(7)
                    vm.loadMoreDiscovery()
                    val later = completePage()
                    assertThat(later.boards).hasSize(16)
                    assertThat(later.failedBoards).hasSize(7)
                    assertThat(calls).hasSize(16)
                    assertThat(calls.distinct()).hasSize(16)
                    unavailable = false
                    vm.refresh()
                    val refreshed = completePage()
                    assertThat(refreshed.failedBoards).isEmpty()
                    assertThat(calls).hasSize(32)
                }
            } finally {
                vm.viewModelScope.cancel()
            }
        }

    @Test
    fun `an unavailable discovery page fails fast instead of sweeping every remaining board`() =
        runTest {
            var unavailable = true
            val calls = mutableListOf<BoardId>()
            val vm =
                viewModel(
                    (0..23).map { board("b%02d".format(it)) },
                    provider =
                        provider {
                            calls += it.board
                            if (unavailable) throw ProviderException.NotFound("offline")
                        },
                )
            try {
                vm.uiState.test {
                    var current = awaitItem()
                    while (current !is SubscribedFeedUiState.Error) current = awaitItem()
                    assertThat(current.message).isEqualTo("Couldn't load videos. Try again.")
                    assertThat(calls).hasSize(8)
                    vm.loadMoreDiscovery()
                    runCurrent()
                    assertThat(calls).hasSize(8)
                    unavailable = false
                    vm.refresh()
                    val ready = completePage()
                    assertThat(ready.boards).hasSize(8)
                    assertThat(ready.canLoadMore).isTrue()
                    assertThat(calls).hasSize(16)
                }
            } finally {
                vm.viewModelScope.cancel()
            }
        }

    @Test
    fun `Following with no board follows is ready while directory refresh is still pending`() =
        runTest {
            val pending = CompletableDeferred<Unit>()
            val directory =
                object : BoardRepository {
                    override fun observeBoards(provider: ProviderId): Flow<List<Board>> = flowOf(emptyList())

                    override suspend fun refreshBoards(provider: ProviderId): OrbinResult<List<Board>> {
                        pending.await()
                        return OrbinResult.Success(emptyList())
                    }
                }
            val vm = viewModel(emptyList(), repository = directory)
            try {
                vm.setFeedMode(HomeFeedMode.FOLLOWING)
                vm.uiState.test {
                    val ready = completePage(HomeFeedMode.FOLLOWING)
                    assertThat(ready.boards).isEmpty()
                    assertThat(pending.isCompleted).isFalse()
                }
            } finally {
                vm.viewModelScope.cancel()
            }
        }

    @Test
    fun `Following retains a directory failure when stored follows cannot be checked safely`() =
        runTest {
            val requested = mutableListOf<BoardId>()
            val first = provider { requested += it.board }
            val second = provider("bbwchan") { requested += it.board }
            val preferences =
                object : BoardPreferencesRepository by FakeBoardPreferencesRepository(subscribed = emptySet()) {
                    override fun observeSubscribedBoards(provider: ProviderId): Flow<Set<BoardId>> =
                        flowOf(if (provider.value == "fourchan") setOf(BoardId("g")) else emptySet())
                }
            val repository =
                object : BoardRepository {
                    override fun observeBoards(provider: ProviderId): Flow<List<Board>> = flowOf(emptyList())

                    override suspend fun refreshBoards(provider: ProviderId): OrbinResult<List<Board>> =
                        if (provider.value ==
                            "fourchan"
                        ) {
                            OrbinResult.Failure(DataError.Timeout())
                        } else {
                            OrbinResult.Success(emptyList())
                        }
                }
            val vm =
                viewModel(
                    emptyList(),
                    preferences = preferences,
                    settings = FakeSettingsRepository(AppSettings.Default.copy(hideNsfwBoards = true)),
                    provider = first,
                    providers = listOf(first, second),
                    repository = repository,
                )
            try {
                vm.setFeedMode(HomeFeedMode.FOLLOWING)
                vm.uiState.test {
                    var current = awaitItem()
                    while (current !is SubscribedFeedUiState.Error) current = awaitItem()
                    assertThat(current.message).isEqualTo("The request timed out")
                    assertThat(requested).isEmpty()
                }
            } finally {
                vm.viewModelScope.cancel()
            }
        }

    @Test
    fun `a followed source catalog outage is not masked by an unfollowed source`() =
        runTest {
            val first = provider("fourchan") { throw ProviderException.NotFound("offline") }
            val second = provider("bbwchan") {}
            val preferences =
                object : BoardPreferencesRepository by FakeBoardPreferencesRepository(subscribed = emptySet()) {
                    override fun observeSubscribedBoards(provider: ProviderId): Flow<Set<BoardId>> =
                        flowOf(if (provider.value == "fourchan") setOf(BoardId("g")) else emptySet())
                }
            val vm =
                viewModel(
                    listOf(board("g")),
                    preferences = preferences,
                    provider = first,
                    providers = listOf(first, second),
                )
            try {
                vm.setFeedMode(HomeFeedMode.FOLLOWING)
                vm.uiState.test {
                    var current = awaitItem()
                    while (current !is SubscribedFeedUiState.Error) current = awaitItem()
                    assertThat(current.message).isEqualTo("Couldn't load videos. Try again.")
                }
            } finally {
                vm.viewModelScope.cancel()
            }
        }

    @Test
    fun `known permanently filtered follows are never revived as unknown fallback boards`() =
        runTest {
            val requested = mutableListOf<BoardId>()
            val blocked = Board(BoardId("g"), "gore")
            val vm =
                viewModel(
                    listOf(blocked),
                    preferences = FakeBoardPreferencesRepository(setOf(blocked.id)),
                    provider = provider { requested += it.board },
                )
            try {
                vm.setFeedMode(HomeFeedMode.FOLLOWING)
                vm.uiState.test {
                    assertThat(completePage(HomeFeedMode.FOLLOWING).boards).isEmpty()
                    assertThat(requested).isEmpty()
                }
            } finally {
                vm.viewModelScope.cancel()
            }
        }

    @Test
    fun `one ready catalog becomes visible while other requests remain bounded and pending`() =
        runTest {
            val release = CompletableDeferred<Unit>()
            var active = 0
            var maximum = 0
            val requested = mutableListOf<BoardId>()
            val vm =
                viewModel(
                    (0..11).map { board("b%02d".format(it)) },
                    provider =
                        provider {
                            requested += it.board
                            active++
                            maximum = maxOf(maximum, active)
                            try {
                                if (it.board.value != "b00") release.await()
                            } finally {
                                active--
                            }
                        },
                )
            try {
                vm.uiState.test {
                    runCurrent()
                    val partial = expectMostRecentItem() as SubscribedFeedUiState.Success
                    assertThat(partial.boards.map { it.board.id.value }).containsExactly("b00")
                    assertThat(partial.isLoadingMore).isTrue()
                    assertThat(active).isEqualTo(4)
                    assertThat(maximum).isEqualTo(4)

                    // Repeated near-end callbacks do not queue another page while this one loads.
                    vm.loadMoreDiscovery()
                    release.complete(Unit)
                    val ready = completePage()
                    assertThat(ready.boards).hasSize(8)
                    assertThat(requested).hasSize(8)
                    assertThat(maximum).isAtMost(4)
                }
            } finally {
                vm.viewModelScope.cancel()
            }
        }

    @Test
    fun `the first discovery page includes both sources and a source switch fetches only that source`() =
        runTest {
            val directory = (0..11).map { board("b%02d".format(it)) }
            val calls = mutableListOf<Pair<ProviderId, BoardId>>()
            val first = provider("fourchan") { calls += it.provider to it.board }
            val second = provider("bbwchan") { calls += it.provider to it.board }
            val vm = viewModel(directory, provider = first, providers = listOf(first, second))
            try {
                vm.uiState.test {
                    val ready = completePage()
                    assertThat(ready.boards.groupingBy { it.providerId }.eachCount())
                        .containsExactly("fourchan", 4, "bbwchan", 4)
                    assertThat(calls).hasSize(8)
                    vm.setProviderFilter("bbwchan")
                    val scoped = completePage()
                    assertThat(scoped.providerFilter).isEqualTo("bbwchan")
                    assertThat(scoped.boards).hasSize(8)
                    assertThat(scoped.boards.map { it.providerId }.toSet()).containsExactly("bbwchan")
                    assertThat(calls.count { it.first.value == "fourchan" }).isEqualTo(4)
                    assertThat(calls.count { it.first.value == "bbwchan" }).isEqualTo(8)
                }
            } finally {
                vm.viewModelScope.cancel()
            }
        }

    @Test
    fun `changing to Following cancels obsolete discovery instead of showing its late results`() =
        runTest {
            val release = CompletableDeferred<Unit>()
            var cancellations = 0
            val followed = board("z")
            val vm =
                viewModel(
                    (0..9).map { board("b%02d".format(it)) } + followed,
                    preferences = FakeBoardPreferencesRepository(setOf(followed.id)),
                    provider =
                        provider {
                            if (it.board != followed.id) {
                                try {
                                    release.await()
                                } finally {
                                    if (!release.isCompleted) cancellations++
                                }
                            }
                        },
                )
            try {
                vm.uiState.test {
                    runCurrent()
                    vm.setFeedMode(HomeFeedMode.FOLLOWING)
                    val selected = completePage(HomeFeedMode.FOLLOWING)
                    assertThat(selected.mode).isEqualTo(HomeFeedMode.FOLLOWING)
                    assertThat(selected.boards.map { it.board.id }).containsExactly(followed.id)
                    assertThat(cancellations).isEqualTo(4)
                    release.complete(Unit)
                    advanceUntilIdle()
                    val latest = vm.uiState.value as SubscribedFeedUiState.Success
                    assertThat(latest.mode).isEqualTo(HomeFeedMode.FOLLOWING)
                    assertThat(latest.boards.map { it.board.id }).containsExactly(followed.id)
                }
            } finally {
                vm.viewModelScope.cancel()
            }
        }

    @Test
    fun `persisted content settings arrive before discovery can request adult or permanently filtered boards`() =
        runTest {
            val settingsFlow = MutableSharedFlow<AppSettings>(replay = 1)
            val settings =
                object : SettingsRepository by FakeSettingsRepository() {
                    override val settings: Flow<AppSettings> = settingsFlow
                }
            val requested = mutableListOf<BoardId>()
            val vm =
                viewModel(
                    listOf(board("safe"), board("adult").copy(isNsfw = true), Board(BoardId("blocked"), "gore")),
                    settings = settings,
                    provider = provider { requested += it.board },
                )
            try {
                vm.uiState.test {
                    runCurrent()
                    assertThat(requested).isEmpty()
                    settingsFlow.emit(AppSettings.Default.copy(hideNsfwBoards = true))
                    val ready = completePage()
                    assertThat(ready.boards.map { it.board.id.value }).containsExactly("safe")
                    assertThat(ready.hideNsfwBoards).isTrue()
                    assertThat(requested.map { it.value }).containsExactly("safe")
                }
            } finally {
                vm.viewModelScope.cancel()
            }
        }
}

private suspend fun ReceiveTurbine<SubscribedFeedUiState>.completePage(
    mode: HomeFeedMode? = null,
): SubscribedFeedUiState.Success {
    while (true) {
        val state = awaitItem()
        if (state !is SubscribedFeedUiState.Success || state.isLoadingMore) continue
        if (mode == null || state.mode == mode) return state
    }
}

private fun board(id: String) = Board(BoardId(id), "Board $id")

private fun viewModel(
    boards: List<Board>,
    preferences: BoardPreferencesRepository = FakeBoardPreferencesRepository(subscribed = emptySet()),
    settings: SettingsRepository = FakeSettingsRepository(),
    provider: ImageBoardProvider = provider {},
    providers: List<ImageBoardProvider> = listOf(provider),
    repository: BoardRepository = FakeBoardRepository(boards),
): SubscribedFeedViewModel {
    val registry =
        object : ProviderRegistry {
            override fun all(): List<ImageBoardProvider> = providers

            override fun default(): ImageBoardProvider = provider

            override fun get(id: ProviderId): ImageBoardProvider? = providers.firstOrNull { it.metadata.id == id }
        }
    return SubscribedFeedViewModel(
        registry,
        ObserveActiveProviderUseCase(registry, settings),
        repository,
        preferences,
        settings,
        FakeHistoryRepository(),
        AppLockController(),
        mockk(relaxed = true),
    )
}

private fun provider(
    id: String = "fourchan",
    onCatalog: suspend (CatalogRequest) -> Unit,
) = object : ImageBoardProvider {
    override val metadata = ProviderMetadata(ProviderId(id), id, "https://example.org")
    override val capabilities = ProviderCapabilities()

    override suspend fun getBoards(): List<Board> = emptyList()

    override suspend fun getCatalog(request: CatalogRequest): List<CatalogThread> {
        onCatalog(request)
        val post =
            Post(
                id = PostId(1),
                board = request.board,
                threadId = ThreadId(1),
                isOriginalPost = true,
                attachments =
                    persistentListOf(
                        MediaAttachment(
                            "video",
                            "video.mp4",
                            "mp4",
                            MediaType.VIDEO,
                            "https://example.org/video.mp4",
                            "https://example.org/thumb.jpg",
                        ),
                    ),
            )
        return listOf(CatalogThread(ThreadKey(metadata.id, request.board, ThreadId(1)), post, ThreadStats()))
    }

    override suspend fun getThread(
        board: BoardId,
        thread: ThreadId,
    ): Thread = throw ProviderException.NotFound("unused")
}
