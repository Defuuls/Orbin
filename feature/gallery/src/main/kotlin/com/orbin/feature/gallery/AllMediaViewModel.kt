package com.orbin.feature.gallery

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import com.orbin.core.model.AppSettings
import com.orbin.core.model.Board
import com.orbin.core.model.CatalogRequest
import com.orbin.domain.media.AllMediaItem
import com.orbin.domain.media.AllMediaUiState
import com.orbin.domain.media.BoardSweep
import com.orbin.domain.media.DeepScanTarget
import com.orbin.domain.media.MediaWallCollector
import com.orbin.domain.media.ScanSettings
import com.orbin.domain.media.mediaItemsFor
import com.orbin.domain.media.sweepBoard
import com.orbin.domain.media.visibleUnder
import com.orbin.domain.repository.BoardRepository
import com.orbin.domain.repository.DownloadRepository
import com.orbin.domain.repository.SettingsRepository
import com.orbin.domain.usecase.ObserveActiveProviderUseCase
import com.orbin.provider.api.ImageBoardProvider
import com.orbin.provider.api.ProviderException
import com.orbin.provider.api.ProviderRegistry
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import javax.inject.Inject

/**
 * Every file on every board, in one list.
 *
 * The gallery browser next door is a drill-down — pick a board, pick a thread, then look at its
 * media. This is the opposite: it sweeps every board's catalog up front and pours every attachment
 * it finds into a single grid, so there is nothing to pick and nothing to come back out of.
 *
 * Two properties matter more than they might look:
 *
 * - **Boards are awaited in order, not as they finish.** Requests still overlap up to
 *   [MAX_CONCURRENT_BOARD_LOADS], but results are appended in board order, so what is already on
 *   screen never reshuffles under a finger that is mid-scroll, and the wall reads the same way
 *   twice.
 * - **A board that fails is skipped, not fatal.** Sweeping ~70 boards means some will 404 or time
 *   out; one of them must not take the other sixty-nine down with it.
 *
 * A catalog carries each thread's opening post and, on some engines, a few teaser replies — so the
 * sweep alone cannot reach media attached deeper inside a thread. The optional deep scan does,
 * at the cost of one request per thread; see [runDeepScan].
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class AllMediaViewModel
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        providerRegistry: ProviderRegistry,
        observeActiveProvider: ObserveActiveProviderUseCase,
        private val boardRepository: BoardRepository,
        private val settingsRepository: SettingsRepository,
        private val downloadRepository: DownloadRepository,
    ) : ViewModel() {
        /** Set when the wall was opened from a board's catalog: sweep that board alone. */
        private val onlyBoard: String? = savedStateHandle.get<String>("board")

        private val activeProvider: StateFlow<ImageBoardProvider> =
            observeActiveProvider()
                .stateIn(viewModelScope, SharingStarted.Eagerly, providerRegistry.default())

        private val refreshRequests = MutableStateFlow(0)

        private val _uiState = MutableStateFlow(AllMediaUiState())
        val uiState: StateFlow<AllMediaUiState> = _uiState.asStateFlow()

        private val _isRefreshing = MutableStateFlow(false)
        val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

        /** What the sweep has found so far, deduplicated and capped; see [MediaWallCollector]. */
        private val wall = MediaWallCollector()

        private var scanJob: Job? = null
        private var deepScanJob: Job? = null

        /**
         * Threads the catalog sweep found, for the deep scan to walk. Held separately from
         * [wall] because a thread whose opening post has no media contributes nothing to the
         * wall and would be invisible there — and a text-only OP with image replies is exactly the
         * case the deep scan exists for.
         */
        private var deepScanTargets: List<DeepScanTarget> = emptyList()

        /** What the deep scan needs to resume against once the catalog sweep has finished. */
        private var sweptWith: SweepContext? = null

        /** Drives tile sizing on the wall from the reader's thumbnail-size preference. */
        val settings: StateFlow<AppSettings> =
            settingsRepository.settings
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), AppSettings.Default)

        private val deepScanEnabled: StateFlow<Boolean> =
            settingsRepository.settings
                .map { it.deepMediaScan }
                .distinctUntilChanged()
                .stateIn(viewModelScope, SharingStarted.Eagerly, false)

        init {
            // Toggling the deep scan acts on the sweep already done rather than provoking a new
            // one: switching it on picks up the threads the catalog sweep found, and switching it
            // off stops the walk without disturbing what is already on the wall.
            deepScanEnabled
                .onEach { enabled -> if (enabled) startDeepScan() else stopDeepScan() }
                .launchIn(viewModelScope)

            activeProvider
                .flatMapLatest { provider ->
                    combine(
                        boardRepository
                            .observeBoards(provider.metadata.id)
                            .map { boards -> boards.filter { onlyBoard == null || it.id.value == onlyBoard } }
                            .distinctUntilChanged(),
                        settingsRepository.settings.map { ScanSettings(it) }.distinctUntilChanged(),
                        refreshRequests,
                    ) { boards, scanSettings, _ -> Triple(provider, boards, scanSettings) }
                }.onEach { (provider, boards, scanSettings) ->
                    startScan(provider, boards, scanSettings)
                }.launchIn(viewModelScope)
        }

        /** Saves one file from the wall to Downloads/Orbin, in its board and thread folder. */
        fun save(item: AllMediaItem) {
            viewModelScope.launch {
                runCatching {
                    downloadRepository.enqueue(
                        url = item.attachment.sourceUrl,
                        fileName = item.attachment.originalFileName,
                        boardId = item.key.board.value,
                        threadId = item.key.thread.value,
                        threadTitle = item.threadTitle,
                    )
                }
            }
        }

        /**
         * Re-sweeps every board's catalog. Deliberately does not re-fetch the board *list*: boards
         * change on the order of months, and refreshing them here would emit a new list and kick
         * off a second sweep on top of this one.
         */
        fun refresh() {
            _isRefreshing.value = true
            refreshRequests.value += 1
        }

        /** Turns the deep scan on or off; the wall reacts through [deepScanEnabled]. */
        fun setDeepScan(enabled: Boolean) {
            viewModelScope.launch { settingsRepository.setDeepMediaScan(enabled) }
        }

        private fun startScan(
            provider: ImageBoardProvider,
            boards: List<Board>,
            scanSettings: ScanSettings,
        ) {
            scanJob?.cancel()
            stopDeepScan()
            wall.clear()
            deepScanTargets = emptyList()
            sweptWith = null

            val visibleBoards = boards.visibleUnder(scanSettings)
            _uiState.value =
                AllMediaUiState(boardsTotal = visibleBoards.size, isScanning = visibleBoards.isNotEmpty())
            if (visibleBoards.isEmpty()) {
                _isRefreshing.value = false
                return
            }

            scanJob =
                viewModelScope.launch {
                    val permits = Semaphore(MAX_CONCURRENT_BOARD_LOADS)
                    val targets = mutableListOf<DeepScanTarget>()
                    coroutineScope {
                        visibleBoards
                            .map { board ->
                                async { permits.withPermit { fetchBoardMedia(provider, board, scanSettings) } }
                            }.forEachIndexed { index, deferred ->
                                // Awaited in submission order rather than completion order: see the
                                // class comment on why the wall must not reshuffle as it fills.
                                deferred.await()?.let { sweep ->
                                    append(sweep.items)
                                    targets += sweep.targets
                                }
                                _uiState.update { it.copy(boardsScanned = index + 1) }
                            }
                    }
                    deepScanTargets = targets
                    sweptWith = SweepContext(provider, scanSettings)
                    _uiState.update { it.copy(isScanning = false) }
                    _isRefreshing.value = false
                    if (deepScanEnabled.value) startDeepScan()
                }
        }

        /**
         * One board's attachments and its threads, or null if its catalog could not be fetched.
         *
         * Only [ProviderException] is caught, and cancellation is left to propagate: a sweep that
         * swallowed cancellation would keep running after the screen was closed, or race the next
         * sweep. Sweeping every board means some will 404 or time out, so a failure here is an
         * ordinary outcome and costs that board only.
         */
        private suspend fun fetchBoardMedia(
            provider: ImageBoardProvider,
            board: Board,
            scanSettings: ScanSettings,
        ): BoardSweep? =
            try {
                val catalog = provider.getCatalog(CatalogRequest(provider.metadata.id, board.id))
                sweepBoard(board, catalog, scanSettings.hiddenTokens)
            } catch (e: ProviderException) {
                Logger.w(tag = TAG) { "Failed to sweep board (${e::class.simpleName})" }
                _uiState.update { it.copy(failedBoards = it.failedBoards + 1) }
                null
            }

        private fun startDeepScan() {
            val context = sweptWith ?: return
            if (deepScanJob?.isActive == true || deepScanTargets.isEmpty()) return
            deepScanJob = viewModelScope.launch { runDeepScan(context) }
        }

        private fun stopDeepScan() {
            deepScanJob?.cancel()
            deepScanJob = null
            _uiState.update { it.copy(isDeepScanning = false) }
        }

        /**
         * Walks every thread the catalog sweep found and adds the media hanging off its replies.
         *
         * This is one request per *thread* rather than per board — thousands of them — so it is
         * trickled out one at a time with [MediaWallCollector.DEEP_SCAN_REQUEST_INTERVAL_MS] between requests, which
         * is what providers ask of a client. That makes it a background trickle measured in hours,
         * not a load that finishes: it fills the wall gradually while the reader scrolls, keeps its
         * place is at the end so nothing above it moves, and stops the moment the screen closes or
         * the setting is switched off. Hence opt-in, and hence off by default.
         */
        private suspend fun runDeepScan(context: SweepContext) {
            val targets = deepScanTargets
            _uiState.update {
                it.copy(isDeepScanning = true, threadsTotal = targets.size, threadsScanned = 0)
            }
            targets.forEachIndexed { index, target ->
                if (wall.isFull) {
                    _uiState.update {
                        it.copy(isDeepScanning = false, threadsScanned = index)
                    }
                    return
                }
                fetchThreadMedia(context, target)?.let(::append)
                _uiState.update { it.copy(threadsScanned = index + 1) }
                delay(MediaWallCollector.DEEP_SCAN_REQUEST_INTERVAL_MS)
            }
            _uiState.update { it.copy(isDeepScanning = false) }
        }

        /** One thread's attachments, or null if it could not be fetched — pruned, 404, timed out. */
        private suspend fun fetchThreadMedia(
            context: SweepContext,
            target: DeepScanTarget,
        ): List<AllMediaItem>? =
            try {
                val thread = context.provider.getThread(target.key.board, target.key.thread)
                thread.allPosts.mediaItemsFor(target, context.scanSettings.hiddenTokens)
            } catch (e: ProviderException) {
                Logger.w(tag = TAG) { "Deep scan skipped thread (${e::class.simpleName})" }
                null
            }

        /**
         * Adds a board's haul to the wall. New files land at the end, so nothing already rendered
         * moves; duplicates of a file already shown are dropped — which is also what keeps the deep
         * scan from re-adding the opening-post media the catalog sweep already found.
         */
        private fun append(items: List<AllMediaItem>) {
            if (wall.append(items)) _uiState.update { it.copy(items = wall.items.toImmutableList()) }
        }

        private companion object {
            const val TAG = "AllMediaViewModel"
            const val STOP_TIMEOUT_MS = 5_000L

            /**
             * Matches the subscribed feed. A sweep is one catalog request per board — around
             * seventy on a large provider — and four in flight keeps it brisk without looking like
             * a scraper to the server.
             */
            const val MAX_CONCURRENT_BOARD_LOADS = 4
        }
    }

/** The provider and filters a completed sweep ran under, so the deep scan matches it exactly. */
private data class SweepContext(
    val provider: ImageBoardProvider,
    val scanSettings: ScanSettings,
)
