package com.orbin.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.orbin.core.common.lock.AppLockController
import com.orbin.core.model.AppSettings
import com.orbin.core.model.Board
import com.orbin.core.model.BoardId
import com.orbin.core.model.CatalogRequest
import com.orbin.core.model.CatalogThread
import com.orbin.core.model.FeedRefreshInterval
import com.orbin.core.model.FeedThreadLimit
import com.orbin.core.model.ThreadKey
import com.orbin.core.model.hiddenTagTokens
import com.orbin.domain.repository.BoardPreferencesRepository
import com.orbin.domain.repository.BoardRepository
import com.orbin.domain.repository.HistoryRepository
import com.orbin.domain.repository.SettingsRepository
import com.orbin.domain.usecase.ObserveActiveProviderUseCase
import com.orbin.provider.api.ImageBoardProvider
import com.orbin.provider.api.ProviderException
import com.orbin.provider.api.ProviderRegistry
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import javax.inject.Inject

sealed interface SubscribedFeedUiState {
    data object Loading : SubscribedFeedUiState

    data class Error(
        val message: String,
    ) : SubscribedFeedUiState

    data class Success(
        val boards: ImmutableList<SubscribedBoardFeed>,
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
        settingsRepository: SettingsRepository,
        historyRepository: HistoryRepository,
        private val appLockController: AppLockController,
    ) : ViewModel() {
        private val activeProvider: StateFlow<ImageBoardProvider> =
            observeActiveProvider()
                .stateIn(viewModelScope, SharingStarted.Eagerly, registry.default())

        val providerId: StateFlow<String> =
            activeProvider
                .map { it.metadata.id.value }
                .stateIn(viewModelScope, SharingStarted.Eagerly, activeProvider.value.metadata.id.value)

        private val refreshRequests = MutableStateFlow(0)

        val settings: StateFlow<AppSettings> =
            settingsRepository.settings
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), AppSettings.Default)

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
                                observeThreadLimitOverrides(provider, subscribedIds),
                                settings,
                                refreshRequests,
                            ) { boards, limitOverrides, settings, refreshCount ->
                                val inputs =
                                    FeedInputs(
                                        provider.metadata.id.value,
                                        subscribedIds,
                                        boards,
                                        limitOverrides,
                                        settings,
                                        refreshCount,
                                    )
                                loadOrReuseFeeds(inputs) {
                                    loadSubscribedFeeds(provider, boards, subscribedIds, limitOverrides, settings)
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

        /**
         * Drives the pull-to-refresh indicator. Cleared when [uiState] next emits rather than when
         * [refresh] returns: refresh only bumps [refreshRequests], and the load it provokes happens
         * downstream, so clearing it at the call site would retract the spinner before the feed
         * had actually reloaded.
         */
        val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

        /**
         * Leaving the feed for longer than [STOP_TIMEOUT_MS] (e.g. while reading a thread) stops
         * the upstream flow, and returning restarts it with the same inputs. "Refresh feed on
         * return" decides whether that restart reloads: the last successful load is kept, and
         * reused when the inputs are unchanged and it is still within the chosen interval.
         */
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
            return load().also { state ->
                if (state is SubscribedFeedUiState.Success) {
                    lastLoad = CachedFeed(inputs, state, System.currentTimeMillis())
                }
            }
        }

        init {
            activeProvider.onEach { refresh() }.launchIn(viewModelScope)
        }

        fun refresh() {
            _isRefreshing.value = true
            viewModelScope.launch {
                boardRepository.refreshBoards(activeProvider.value.metadata.id)
                refreshRequests.value += 1
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

        private fun observeThreadLimitOverrides(
            provider: ImageBoardProvider,
            subscribedIds: Set<BoardId>,
        ): Flow<Map<BoardId, FeedThreadLimit?>> {
            if (subscribedIds.isEmpty()) return flowOf(emptyMap())
            return combine(
                subscribedIds.map { id ->
                    boardPreferencesRepository
                        .observeFeedThreadLimit(provider.metadata.id, id)
                        .map { id to it }
                },
            ) { pairs -> pairs.toMap() }
        }

        private suspend fun loadSubscribedFeeds(
            provider: ImageBoardProvider,
            boards: List<Board>,
            subscribedIds: Set<BoardId>,
            limitOverrides: Map<BoardId, FeedThreadLimit?>,
            settings: AppSettings,
        ): SubscribedFeedUiState {
            if (subscribedIds.isEmpty()) {
                return SubscribedFeedUiState.Success(emptyList<SubscribedBoardFeed>().toImmutableList())
            }

            val subscribedBoards =
                boards
                    .filter { it.id in subscribedIds }
                    .filterNot { board -> settings.hideNsfwBoards && board.isNsfw }
                    .sortedBy { it.id.value }

            if (subscribedBoards.isEmpty()) {
                return SubscribedFeedUiState.Success(emptyList<SubscribedBoardFeed>().toImmutableList())
            }

            return try {
                val requestLimit = Semaphore(MAX_CONCURRENT_BOARD_LOADS)
                val feeds =
                    kotlinx.coroutines.coroutineScope {
                        subscribedBoards
                            .map { board ->
                                async {
                                    val override = limitOverrides[board.id]
                                    val threads =
                                        requestLimit.withPermit {
                                            loadBoardThreads(
                                                provider,
                                                board,
                                                override,
                                                settings,
                                            )
                                        }
                                    SubscribedBoardFeed(board, threads, override)
                                }
                            }.map { it.await() }
                    }
                SubscribedFeedUiState.Success(feeds.toImmutableList())
            } catch (e: ProviderException) {
                SubscribedFeedUiState.Error(e.message ?: "Unable to load subscribed boards")
            }
        }

        private suspend fun loadBoardThreads(
            provider: ImageBoardProvider,
            board: Board,
            limitOverride: FeedThreadLimit?,
            settings: AppSettings,
        ): ImmutableList<CatalogThread> {
            val catalog = provider.getCatalog(CatalogRequest(provider.metadata.id, board.id))
            val effectiveLimit = limitOverride ?: settings.feedThreadLimit
            return (effectiveLimit.count?.let(catalog::take) ?: catalog)
                .filterNot { thread -> thread.matchesAny(settings.hiddenTagTokens()) }
                .filterNot { thread -> settings.hideTextOnlyThreads && thread.originalPost.attachments.isEmpty() }
                .toImmutableList()
        }

        private companion object {
            const val STOP_TIMEOUT_MS = 5_000L
            const val MAX_CONCURRENT_BOARD_LOADS = 4
        }
    }

/**
 * Cache key for a subscribed-feed load: every local input that goes into it. Equal keys mean the
 * flow merely restarted (nothing was subscribed, refreshed, or reconfigured in between), so the
 * previous result can stand in for a re-fetch.
 */
private data class FeedInputs(
    val providerId: String,
    val subscribedIds: Set<BoardId>,
    val boards: List<Board>,
    val limitOverrides: Map<BoardId, FeedThreadLimit?>,
    val settings: AppSettings,
    val refreshCount: Int,
)

private fun CatalogThread.matchesAny(tokens: Set<String>): Boolean {
    if (tokens.isEmpty()) return false
    val haystack = listOfNotNull(originalPost.subject, originalPost.comment).joinToString(" ").lowercase()
    return tokens.any(haystack::contains)
}

/** A feed load kept for reuse, with the moment it was loaded so its age can be judged. */
private data class CachedFeed(
    val inputs: FeedInputs,
    val state: SubscribedFeedUiState.Success,
    val loadedAtMillis: Long,
)

/**
 * Whether a cached feed [ageMillis] old is still fresh enough to show instead of reloading.
 *
 * The two ends carry the behaviour of the on/off setting this replaced: `ALWAYS` has a staleness
 * bound of zero, so nothing is ever fresh enough, and `NEVER` has none at all, so everything is.
 */
internal fun FeedRefreshInterval.allowsReuse(ageMillis: Long): Boolean {
    val staleAfter = staleAfterMillis ?: return true
    return ageMillis < staleAfter
}
