@file:Suppress("TooGenericExceptionCaught")

package com.orbin.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import com.orbin.core.common.dispatchers.Dispatcher
import com.orbin.core.common.dispatchers.OrbinDispatcher
import com.orbin.core.common.lock.AppLockController
import com.orbin.core.model.AppSettings
import com.orbin.core.model.Board
import com.orbin.core.model.BoardId
import com.orbin.core.model.CatalogThread
import com.orbin.core.model.FeedThreadLimit
import com.orbin.core.model.HomeFeedMode
import com.orbin.core.model.PlaybackPreferences
import com.orbin.core.model.PlaybackSettings
import com.orbin.core.model.ThreadKey
import com.orbin.domain.feed.SubscribedFeedLoader
import com.orbin.domain.repository.BoardPreferencesRepository
import com.orbin.domain.repository.BoardRepository
import com.orbin.domain.repository.HistoryRepository
import com.orbin.domain.repository.SettingsRepository
import com.orbin.domain.usecase.ObserveActiveProviderUseCase
import com.orbin.media.ImagePreloader
import com.orbin.media.video.VideoPreloader
import com.orbin.provider.api.ImageBoardProvider
import com.orbin.provider.api.ProviderRegistry
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
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
        val isLoadingMore: Boolean = false,
        val canLoadMore: Boolean = false,
        val mode: HomeFeedMode = HomeFeedMode.FOR_YOU,
        val providerFilter: String? = null,
        /** The published request token coalesces repeated end-of-page callbacks. */
        val discoveryLimit: Int = 0,
        val hideNsfwBoards: Boolean = false,
        val coverViolentMedia: Boolean = true,
        /** Preserves a source outage even when healthy catalogs contain no eligible videos. */
        val errorMessage: String? = null,
    ) : SubscribedFeedUiState
}

data class SubscribedBoardFeed(
    val board: Board,
    val threads: ImmutableList<CatalogThread>,
    /** This board's thread-count override, if any; null means it follows the global default. */
    val threadLimitOverride: FeedThreadLimit?,
    /** Explicit source identity also survives an empty or failed catalog. */
    val providerId: String =
        threads
            .firstOrNull()
            ?.key
            ?.provider
            ?.value
            .orEmpty(),
    val failed: Boolean = false,
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
        private val imagePreloader: ImagePreloader,
        private val videoPreloader: VideoPreloader? = null,
        @Dispatcher(OrbinDispatcher.Default) workDispatcher: CoroutineDispatcher = Dispatchers.Default,
    ) : ViewModel() {
        private val feedLoader = SubscribedFeedLoader()
        private val providers = registry.all()
        private val catalogs = HomeFeedCatalogLoader(workDispatcher)
        private val _feedMode = MutableStateFlow(HomeFeedMode.FOR_YOU)
        val feedMode: StateFlow<HomeFeedMode> = _feedMode.asStateFlow()
        private val providerFilter = MutableStateFlow<String?>(null)
        private val discoveryLimit = MutableStateFlow(DISCOVERY_PAGE_SIZE)
        private val indexResults = MutableStateFlow<Map<String, String?>>(emptyMap())

        fun setFeedMode(mode: HomeFeedMode) {
            if (_feedMode.value == mode) return
            clearFeedImagePrefetch()
            clearFeedVideoPrefetch()
            _feedMode.value = mode
        }

        fun setProviderFilter(id: String?) {
            if (id != null && providers.none { it.metadata.id.value == id }) return
            if (providerFilter.value == id) return
            clearFeedImagePrefetch()
            clearFeedVideoPrefetch()
            discoveryLimit.value = DISCOVERY_PAGE_SIZE
            providerFilter.value = id
        }

        /** Fetch another small page only when the current page has finished. */
        fun loadMoreDiscovery() {
            val current = uiState.value as? SubscribedFeedUiState.Success ?: return
            val selectionMatches =
                current.mode == _feedMode.value &&
                    current.providerFilter == providerFilter.value &&
                    current.discoveryLimit == discoveryLimit.value
            val pageReady = current.mode == HomeFeedMode.FOR_YOU && !current.isLoadingMore && current.canLoadMore
            if (!selectionMatches || !pageReady) return
            discoveryLimit.value += DISCOVERY_PAGE_SIZE
        }

        private var imagePrefetchJob: Job? = null
        private var imagePrefetchUrls: List<String> = emptyList()
        private var videoPrefetchJob: Job? = null
        private var videoPrefetchUrl: String? = null
        private val videoPrefetchOwner = Any()

        /** One bounded byte prefix, without creating another decoder or warming hidden media. */
        fun prefetchFeedVideo(url: String?) {
            if (videoPrefetchUrl == url && videoPrefetchJob?.isActive == true) return
            videoPrefetchJob?.cancel()
            videoPrefetchUrl = url
            videoPrefetchJob = url?.let { videoPreloader?.prefetch(it, viewModelScope, videoPrefetchOwner) }
            // Retain a completed prefix until the newly visible player opens it. The cache is
            // already bounded and expires; disabling preload or leaving/backgrounding clears it.
        }

        fun clearFeedVideoPrefetch() {
            videoPrefetchJob?.cancel()
            videoPrefetchJob = null
            videoPrefetchUrl = null
            videoPreloader?.clear(videoPrefetchOwner)
        }

        override fun onCleared() {
            clearFeedImagePrefetch()
            clearFeedVideoPrefetch()
            super.onCleared()
        }

        private val activeProvider: StateFlow<ImageBoardProvider> =
            observeActiveProvider()
                .stateIn(viewModelScope, SharingStarted.Eagerly, registry.default())

        val providerId: StateFlow<String> =
            activeProvider
                .map { it.metadata.id.value }
                .stateIn(viewModelScope, SharingStarted.Eagerly, activeProvider.value.metadata.id.value)

        /** Replace obsolete viewport work instead of accumulating batches during rapid swipes. */
        fun prefetchFeedThumbs(urls: List<String>) {
            val targets = urls.filter { it.isNotBlank() }.distinct()
            if (targets == imagePrefetchUrls && imagePrefetchJob?.isActive == true) return
            imagePrefetchJob?.cancel()
            imagePrefetchUrls = targets
            imagePrefetchJob =
                imagePreloader.prefetchBatch(targets, viewModelScope, maxDimensionPx = FEED_PREFETCH_MAX_PX)
        }

        fun clearFeedImagePrefetch() {
            imagePrefetchJob?.cancel()
            imagePrefetchJob = null
            imagePrefetchUrls = emptyList()
        }

        private val refreshRequests = MutableStateFlow<Map<String, Int>>(emptyMap())

        val settings: StateFlow<AppSettings> =
            settingsRepository.settings
                .stateIn(
                    viewModelScope,
                    SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                    AppSettings.Default.copy(
                        // Never start a default-on preview before persisted scoped preferences arrive.
                        playbackPreferences =
                            PlaybackPreferences(
                                defaults = PlaybackSettings(autoplay = false, preloadNextClip = false),
                            ),
                    ),
                )

        /**
         * Only settings that actually alter fetched/filtered feed content participate in the load
         * cache key. Appearance, playback, downloads, app locking and other unrelated preferences can
         * now change without invalidating every subscribed catalog.
         */
        private val loadSettings: Flow<FeedLoadSettings> =
            settingsRepository.settings
                .map(AppSettings::toFeedLoadSettings)
                .distinctUntilChanged()

        /** Threads already present in reading history, for "already read" title styling in the feed. */
        val visitedThreadKeys: StateFlow<Set<ThreadKey>> =
            historyRepository
                .observeVisitedKeys()
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptySet())

        val uiState: StateFlow<SubscribedFeedUiState> =
            combine(_feedMode, providerFilter, discoveryLimit, loadSettings, ::FeedSelection)
                .flatMapLatest { selection ->
                    // Privacy changes replace every source together before old cached rows can
                    // be mixed with a source that has already applied the new policy.
                    val policyChanged = catalogs.prepare(selection.settings)
                    val selected =
                        providers.filter {
                            selection.providerFilter == null ||
                                it.metadata.id.value == selection.providerFilter
                        }
                    val content: Flow<SubscribedFeedUiState> =
                        if (selected.isEmpty()) {
                            flowOf(
                                SubscribedFeedUiState.Success(
                                    persistentListOf(),
                                    mode = selection.mode,
                                    providerFilter = selection.providerFilter,
                                    discoveryLimit = selection.discoveryLimit,
                                    hideNsfwBoards = selection.settings.hideNsfwBoards,
                                    coverViolentMedia = selection.settings.coverViolentMedia,
                                ),
                            )
                        } else {
                            val quota = (selection.discoveryLimit + selected.size - 1) / selected.size
                            combine(selected.map { observeProviderFeed(it, selection, quota) }) { states ->
                                when (val merged = mergeProviderFeeds(states.toList())) {
                                    is SubscribedFeedUiState.Success ->
                                        merged.copy(
                                            mode = selection.mode,
                                            providerFilter = selection.providerFilter,
                                            discoveryLimit = selection.discoveryLimit,
                                            hideNsfwBoards = selection.settings.hideNsfwBoards,
                                            coverViolentMedia = selection.settings.coverViolentMedia,
                                        )
                                    else -> merged
                                }
                            }
                        }
                    content.onStart {
                        if (policyChanged) emit(SubscribedFeedUiState.Loading)
                    }
                }.onEach { state ->
                    if (state !is SubscribedFeedUiState.Loading &&
                        (state !is SubscribedFeedUiState.Success || !state.isLoadingMore)
                    ) {
                        _isRefreshing.value = false
                    }
                }.stateIn(
                    viewModelScope,
                    SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                    SubscribedFeedUiState.Loading,
                )

        /** Cache and failures stay source-scoped even when two sites reuse a board code. */
        private fun observeProviderFeed(
            provider: ImageBoardProvider,
            selection: FeedSelection,
            quota: Int,
        ): Flow<SubscribedFeedUiState> =
            combine(
                boardRepository.observeBoards(provider.metadata.id),
                boardPreferencesRepository.observeSubscribedBoards(provider.metadata.id),
                boardPreferencesRepository.observeFavoriteBoards(provider.metadata.id),
                indexResults,
            ) { boards, subscribed, favorites, indexes ->
                val settings = selection.settings
                val followed = subscribed + favorites
                val ids =
                    if (selection.mode ==
                        HomeFeedMode.FOLLOWING
                    ) {
                        followed
                    } else {
                        boards.mapTo(mutableSetOf()) { it.id }
                    }
                val eligible = feedLoader.feedBoards(boards, ids, settings.hideNsfwBoards)
                val wanted =
                    if (selection.mode == HomeFeedMode.FOR_YOU) discoveryBoards(eligible, followed, quota) else eligible
                val emptyFollowing = selection.mode == HomeFeedMode.FOLLOWING && followed.isEmpty()
                ProviderFeedSelection(
                    wanted,
                    settings,
                    eligible.size > wanted.size,
                    emptyFollowing || wanted.isNotEmpty() || provider.metadata.id.value in indexes,
                    indexes[provider.metadata.id.value].takeIf { wanted.isEmpty() && !emptyFollowing },
                )
            }.distinctUntilChanged()
                .flatMapLatest { chosen ->
                    if (chosen.boards.isEmpty() && !chosen.indexReady) {
                        flowOf(SubscribedFeedUiState.Loading)
                    } else if (chosen.boards.isEmpty() && chosen.indexError != null) {
                        flowOf(SubscribedFeedUiState.Error(chosen.indexError))
                    } else {
                        combine(
                            boardPreferencesRepository.observeFeedThreadLimits(
                                provider.metadata.id,
                                chosen.boards.mapTo(mutableSetOf()) { it.id },
                            ),
                            refreshRequests.map { it[provider.metadata.id.value] ?: 0 }.distinctUntilChanged(),
                        ) { limitOverrides, refreshCount ->
                            FeedInputs(
                                provider.metadata.id.value,
                                chosen.boards,
                                limitOverrides,
                                chosen.settings,
                                refreshCount,
                                selection.mode,
                                selection.providerFilter,
                                chosen.canLoadMore,
                            )
                        }.distinctUntilChanged().flatMapLatest { catalogs.load(provider, it) }
                    }
                }

        private val _isRefreshing = MutableStateFlow(false)

        val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

        init {
            providers.forEach { provider ->
                viewModelScope.launch { refreshBoardIndex(provider) }
            }
        }

        private suspend fun refreshBoardIndex(provider: ImageBoardProvider) {
            try {
                val result = boardRepository.refreshBoards(provider.metadata.id)
                indexResults.value =
                    indexResults.value +
                    (
                        provider.metadata.id.value to
                            (result as? com.orbin.core.common.result.OrbinResult.Failure)?.error?.message
                    )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Logger.w(tag = TAG) { "Failed to refresh board index (${error::class.simpleName})" }
                indexResults.value =
                    indexResults.value +
                    (provider.metadata.id.value to (error.message ?: "Couldn't load boards. Try again."))
            }
        }

        fun refresh() {
            val selectedSource = providerFilter.value
            val targets = providers.filter { selectedSource == null || it.metadata.id.value == selectedSource }
            _isRefreshing.value = true
            viewModelScope.launch {
                try {
                    coroutineScope {
                        targets
                            .map { provider -> async { refreshBoardIndex(provider) } }
                            .awaitAll()
                    }
                } finally {
                    // Always trigger the catalog pass. If board-index refresh failed, the existing
                    // board list can still refresh and stale content remains available on failure.
                    refreshRequests.update { previous ->
                        previous.toMutableMap().apply {
                            targets.forEach { provider ->
                                val id = provider.metadata.id.value
                                this[id] = (this[id] ?: 0) + 1
                            }
                        }
                    }
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

        private companion object {
            const val TAG = "SubscribedFeedViewModel"
            const val STOP_TIMEOUT_MS = 5_000L
            const val FEED_PREFETCH_MAX_PX = 480
            const val DISCOVERY_PAGE_SIZE = 8
        }
    }

/** The settings that shape the feed; everything else about it is fixed. */
internal data class FeedLoadSettings(
    val hideNsfwBoards: Boolean,
    val coverViolentMedia: Boolean,
)

private fun AppSettings.toFeedLoadSettings(): FeedLoadSettings =
    FeedLoadSettings(
        hideNsfwBoards = hideNsfwBoards,
        coverViolentMedia = coverViolentMedia,
    )

private data class FeedSelection(
    val mode: HomeFeedMode,
    val providerFilter: String?,
    val discoveryLimit: Int,
    val settings: FeedLoadSettings,
)

private data class ProviderFeedSelection(
    val boards: List<Board>,
    val settings: FeedLoadSettings,
    val canLoadMore: Boolean,
    val indexReady: Boolean,
    val indexError: String?,
)

/** Discovery mixes familiar and unfollowed boards before its bounded catalog pass. */
private fun discoveryBoards(
    boards: List<Board>,
    followed: Set<BoardId>,
    limit: Int,
): List<Board> {
    val (familiar, discover) = boards.partition { it.id in followed }
    return buildList {
        for (index in 0 until maxOf(familiar.size, discover.size)) {
            familiar.getOrNull(index)?.let(::add)
            discover.getOrNull(index)?.let(::add)
        }
    }.take(limit)
}

/** A source outage keeps the other sources readable and exposes the combined feed as stale. */
internal fun mergeProviderFeeds(states: List<SubscribedFeedUiState>): SubscribedFeedUiState {
    val successes = states.filterIsInstance<SubscribedFeedUiState.Success>()
    val errors = states.filterIsInstance<SubscribedFeedUiState.Error>()
    val loading = states.any { it is SubscribedFeedUiState.Loading }
    if (successes.isEmpty()) {
        return if (loading) {
            SubscribedFeedUiState.Loading
        } else {
            errors.firstOrNull()
                ?: SubscribedFeedUiState.Success(persistentListOf())
        }
    }
    if (!loading && successes.all { it.boards.isEmpty() } && errors.isNotEmpty()) return errors.first()
    return SubscribedFeedUiState.Success(
        boards = successes.flatMap { it.boards }.toImmutableList(),
        failedBoards = successes.flatMap { it.failedBoards }.toImmutableList(),
        loadedAtMillis = successes.minOf { it.loadedAtMillis },
        stale = successes.any { it.stale } || errors.isNotEmpty(),
        isLoadingMore = successes.any { it.isLoadingMore } || loading,
        canLoadMore = successes.any { it.canLoadMore },
        errorMessage = errors.firstOrNull()?.message ?: successes.firstNotNullOfOrNull { it.errorMessage },
    )
}
