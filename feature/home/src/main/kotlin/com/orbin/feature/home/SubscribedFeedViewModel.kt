package com.orbin.feature.home

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.orbin.core.common.lock.AppLockController
import com.orbin.core.model.AppSettings
import com.orbin.core.model.Board
import com.orbin.core.model.BoardId
import com.orbin.core.model.CatalogRequest
import com.orbin.core.model.CatalogThread
import com.orbin.core.model.FeedRefreshInterval
import com.orbin.core.model.FeedSort
import com.orbin.core.model.FeedThreadLimit
import com.orbin.core.model.MediaFilter
import com.orbin.core.model.ThreadKey
import com.orbin.core.model.filteredCatalogBy
import com.orbin.core.model.hiddenTagTokens
import com.orbin.core.model.isPermanentlyFiltered
import com.orbin.core.model.matchesFilterTokens
import com.orbin.domain.repository.BoardPreferencesRepository
import com.orbin.domain.repository.BoardRepository
import com.orbin.domain.repository.HistoryRepository
import com.orbin.domain.repository.SettingsRepository
import com.orbin.domain.usecase.ObserveActiveProviderUseCase
import com.orbin.provider.api.ImageBoardProvider
import com.orbin.provider.api.ProviderRegistry
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flatMapMerge
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface SubscribedFeedUiState {
    data object Loading : SubscribedFeedUiState

    data class Error(
        val message: String,
    ) : SubscribedFeedUiState

    data class Success(
        val boards: ImmutableList<SubscribedBoardFeed>,
        val failedBoards: ImmutableList<BoardId> = persistentListOf(),
        val loadedAtMillis: Long = System.currentTimeMillis(),
        val stale: Boolean = false,
    ) : SubscribedFeedUiState
}

data class SubscribedBoardFeed(
    val board: Board,
    val threads: ImmutableList<CatalogThread>,
    /** This board's thread-count override, if any; null means it follows the global default. */
    val threadLimitOverride: FeedThreadLimit?,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class SubscribedFeedViewModel
    @Inject
    constructor(
        registry: ProviderRegistry,
        observeActiveProvider: ObserveActiveProviderUseCase,
        private val boardRepository: BoardRepository,
        private val boardPreferencesRepository: BoardPreferencesRepository,
        private val settingsRepository: SettingsRepository,
        historyRepository: HistoryRepository,
        private val appLockController: AppLockController,
        private val savedStateHandle: SavedStateHandle,
    ) : ViewModel() {
        private val activeProvider: StateFlow<ImageBoardProvider> =
            observeActiveProvider()
                .stateIn(viewModelScope, SharingStarted.Eagerly, registry.default())

        val providerId: StateFlow<String> =
            activeProvider
                .map { it.metadata.id.value }
                .stateIn(viewModelScope, SharingStarted.Eagerly, activeProvider.value.metadata.id.value)

        /** Reading layout is session UI state, but SavedStateHandle lets it survive process recreation. */
        val feedLayoutName: StateFlow<String> =
            savedStateHandle.getStateFlow(FEED_LAYOUT_KEY, DEFAULT_FEED_LAYOUT)

        fun setFeedLayoutName(name: String) {
            savedStateHandle[FEED_LAYOUT_KEY] = name
        }

        private val refreshRequests = MutableStateFlow(0)

        val settings: StateFlow<AppSettings> =
            settingsRepository.settings
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), AppSettings.Default)

        /**
         * Only settings that actually alter fetched/filtered feed content participate in the load
         * cache key. Appearance, playback, downloads, privacy and other unrelated preferences can
         * now change without invalidating every subscribed catalog.
         */
        private val loadSettings: StateFlow<FeedLoadSettings> =
            settingsRepository.settings
                .map(AppSettings::toFeedLoadSettings)
                .distinctUntilChanged()
                .stateIn(
                    viewModelScope,
                    SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                    AppSettings.Default.toFeedLoadSettings(),
                )

        /** Threads already present in reading history, for "already read" title styling in the feed. */
        val visitedThreadKeys: StateFlow<Set<ThreadKey>> =
            historyRepository
                .observeVisitedKeys()
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptySet())

        val uiState: StateFlow<SubscribedFeedUiState> =
            activeProvider
                .flatMapLatest { provider ->
                    boardPreferencesRepository
                        .observeSubscribedBoards(provider.metadata.id)
                        .flatMapLatest { subscribedIds ->
                            combine(
                                boardRepository.observeBoards(provider.metadata.id),
                                boardPreferencesRepository.observeFeedThreadLimits(provider.metadata.id, subscribedIds),
                                loadSettings,
                                refreshRequests,
                            ) { boards, limitOverrides, feedSettings, refreshCount ->
                                val inputs =
                                    FeedInputs(
                                        providerId = provider.metadata.id.value,
                                        subscribedIds = subscribedIds,
                                        boards = boards,
                                        limitOverrides = limitOverrides,
                                        settings = feedSettings,
                                        refreshCount = refreshCount,
                                    )
                                loadOrReuseFeeds(inputs) {
                                    loadSubscribedFeeds(
                                        provider,
                                        boards,
                                        subscribedIds,
                                        limitOverrides,
                                        feedSettings,
                                    )
                                }
                            }
                        }
                }.onEach { _isRefreshing.value = false }
                .stateIn(
                    viewModelScope,
                    SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                    SubscribedFeedUiState.Loading,
                )

        private val _isRefreshing = MutableStateFlow(false)

        val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

        private var lastLoad: CachedFeed? = null

        private suspend fun loadOrReuseFeeds(
            inputs: FeedInputs,
            load: suspend () -> SubscribedFeedUiState,
        ): SubscribedFeedUiState {
            lastLoad?.let { cached ->
                val age = System.currentTimeMillis() - cached.loadedAtMillis
                if (cached.inputs == inputs && inputs.settings.feedRefreshInterval.allowsReuse(age)) {
                    return cached.state
                }
            }

            return try {
                when (val state = load()) {
                    is SubscribedFeedUiState.Success -> {
                        val merged = state.withCachedFailures(lastLoad?.state)
                        if (merged.failedBoards.size < merged.boards.size || merged.boards.isEmpty()) {
                            lastLoad = CachedFeed(inputs, merged, merged.loadedAtMillis)
                        }
                        merged
                    }

                    else -> state
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Log.w(TAG, "Failed to refresh subscribed feed", error)
                lastLoad?.state?.copy(stale = true)
                    ?: SubscribedFeedUiState.Error(error.message ?: "Feed refresh failed")
            }
        }

        init {
            activeProvider.onEach { refresh() }.launchIn(viewModelScope)
        }

        fun refresh() {
            _isRefreshing.value = true
            viewModelScope.launch {
                try {
                    boardRepository.refreshBoards(activeProvider.value.metadata.id)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    Log.w(TAG, "Failed to refresh board index", error)
                } finally {
                    // Always trigger the catalog pass. If board-index refresh failed, the existing
                    // board list can still refresh and stale content remains available on failure.
                    refreshRequests.value += 1
                }
            }
        }

        /** Failsafe: lock the app immediately, regardless of the background/foreground cycle. */
        fun lockNow() {
            appLockController.requestLock()
        }

        fun setBoardThreadLimit(
            board: BoardId,
            limit: FeedThreadLimit?,
        ) {
            viewModelScope.launch {
                boardPreferencesRepository.setFeedThreadLimit(activeProvider.value.metadata.id, board, limit)
            }
        }

        /** Cycles Board → Active → Replies → Images → Created → A-Z and persists the choice. */
        fun cycleFeedSort() {
            viewModelScope.launch {
                val values = FeedSort.entries
                val current = settings.value.feedSort
                settingsRepository.setFeedSort(values[(values.indexOf(current) + 1) % values.size])
            }
        }

        private suspend fun loadSubscribedFeeds(
            provider: ImageBoardProvider,
            boards: List<Board>,
            subscribedIds: Set<BoardId>,
            limitOverrides: Map<BoardId, FeedThreadLimit>,
            settings: FeedLoadSettings,
        ): SubscribedFeedUiState {
            if (subscribedIds.isEmpty()) {
                return SubscribedFeedUiState.Success(persistentListOf())
            }

            val subscribedBoards =
                boards
                    .filter { it.id in subscribedIds }
                    .filterNot { board -> settings.hideNsfwBoards && board.isNsfw }
                    .filterNot { board -> board.isPermanentlyFiltered(settings.harshContentFilter) }
                    .sortedBy { it.id.value }

            if (subscribedBoards.isEmpty()) {
                return SubscribedFeedUiState.Success(persistentListOf())
            }

            // flatMapMerge keeps only MAX_CONCURRENT_BOARD_LOADS child flows active. The old
            // async+semaphore version created one suspended coroutine for every subscribed board.
            val results =
                subscribedBoards
                    .withIndex()
                    .asFlow()
                    .flatMapMerge(concurrency = MAX_CONCURRENT_BOARD_LOADS) { indexed ->
                        flow {
                            emit(
                                indexed.index to
                                    loadBoardResult(
                                        provider = provider,
                                        board = indexed.value,
                                        override = limitOverrides[indexed.value.id],
                                        settings = settings,
                                    ),
                            )
                        }
                    }.toList()
                    .sortedBy { it.first }
                    .map { it.second }

            return SubscribedFeedUiState.Success(
                boards = results.map { it.feed }.toImmutableList(),
                failedBoards = results.filter { it.failed }.map { it.feed.board.id }.toImmutableList(),
                loadedAtMillis = System.currentTimeMillis(),
                stale = results.any { it.failed },
            )
        }

        private suspend fun loadBoardResult(
            provider: ImageBoardProvider,
            board: Board,
            override: FeedThreadLimit?,
            settings: FeedLoadSettings,
        ): BoardLoadResult =
            try {
                BoardLoadResult(
                    SubscribedBoardFeed(
                        board = board,
                        threads = loadBoardThreads(provider, board, override, settings),
                        threadLimitOverride = override,
                    ),
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Log.w(TAG, "Failed to load catalog for /${board.id.value}/", error)
                BoardLoadResult(
                    feed = SubscribedBoardFeed(board, persistentListOf(), override),
                    failed = true,
                )
            }

        private suspend fun loadBoardThreads(
            provider: ImageBoardProvider,
            board: Board,
            limitOverride: FeedThreadLimit?,
            settings: FeedLoadSettings,
        ): ImmutableList<CatalogThread> {
            val catalog = provider.getCatalog(CatalogRequest(provider.metadata.id, board.id))
            val effectiveLimit = limitOverride ?: settings.feedThreadLimit
            return (effectiveLimit.count?.let(catalog::take) ?: catalog)
                .filterNot { thread ->
                    thread.matchesFilterTokens(settings.hiddenTokens, settings.harshContentFilter)
                }.filterNot { thread -> settings.hideTextOnlyThreads && thread.originalPost.attachments.isEmpty() }
                .filteredCatalogBy(settings.mediaFilter)
                .toImmutableList()
        }

        private companion object {
            const val TAG = "SubscribedFeedViewModel"
            const val STOP_TIMEOUT_MS = 5_000L
            const val MAX_CONCURRENT_BOARD_LOADS = 4
            const val FEED_LAYOUT_KEY = "feedLayout"
            const val DEFAULT_FEED_LAYOUT = "LIST"
        }
    }

private data class BoardLoadResult(
    val feed: SubscribedBoardFeed,
    val failed: Boolean = false,
)

private data class FeedLoadSettings(
    val feedThreadLimit: FeedThreadLimit,
    val hideNsfwBoards: Boolean,
    val hideTextOnlyThreads: Boolean,
    val harshContentFilter: Boolean,
    val hiddenTokens: Set<String>,
    val mediaFilter: MediaFilter,
    val feedRefreshInterval: FeedRefreshInterval,
)

private fun AppSettings.toFeedLoadSettings(): FeedLoadSettings =
    FeedLoadSettings(
        feedThreadLimit = feedThreadLimit,
        hideNsfwBoards = hideNsfwBoards,
        hideTextOnlyThreads = hideTextOnlyThreads,
        harshContentFilter = harshContentFilter,
        hiddenTokens = hiddenTagTokens(),
        mediaFilter = mediaFilter,
        feedRefreshInterval = feedRefreshInterval,
    )

private data class FeedInputs(
    val providerId: String,
    val subscribedIds: Set<BoardId>,
    val boards: List<Board>,
    val limitOverrides: Map<BoardId, FeedThreadLimit>,
    val settings: FeedLoadSettings,
    val refreshCount: Int,
)

private data class CachedFeed(
    val inputs: FeedInputs,
    val state: SubscribedFeedUiState.Success,
    val loadedAtMillis: Long,
)

private fun SubscribedFeedUiState.Success.withCachedFailures(
    cached: SubscribedFeedUiState.Success?,
): SubscribedFeedUiState.Success {
    if (failedBoards.isEmpty() || cached == null) return this
    val cachedByBoard = cached.boards.associateBy { it.board.id }
    return copy(
        boards =
            boards
                .map { current ->
                    if (current.board.id in failedBoards) cachedByBoard[current.board.id] ?: current else current
                }.toImmutableList(),
        stale = true,
    )
}

internal fun FeedRefreshInterval.allowsReuse(ageMillis: Long): Boolean {
    val staleAfter = staleAfterMillis ?: return true
    return ageMillis < staleAfter
}
