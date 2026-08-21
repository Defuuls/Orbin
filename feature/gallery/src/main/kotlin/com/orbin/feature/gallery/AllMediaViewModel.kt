package com.orbin.feature.gallery

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.orbin.core.model.AppSettings
import com.orbin.core.model.Board
import com.orbin.core.model.CatalogRequest
import com.orbin.core.model.CatalogThread
import com.orbin.core.model.MediaAttachment
import com.orbin.core.model.MediaFilter
import com.orbin.core.model.ThreadKey
import com.orbin.core.model.hiddenTagTokens
import com.orbin.core.model.isPermanentlyFiltered
import com.orbin.core.model.matchesFilterTokens
import com.orbin.domain.repository.BoardRepository
import com.orbin.domain.repository.SettingsRepository
import com.orbin.domain.usecase.ObserveActiveProviderUseCase
import com.orbin.provider.api.ImageBoardProvider
import com.orbin.provider.api.ProviderException
import com.orbin.provider.api.ProviderRegistry
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
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
 * One file on the wall, carrying enough of where it came from to open it and to label the tile.
 */
data class AllMediaItem(
    val attachment: MediaAttachment,
    val key: ThreadKey,
    val boardTitle: String,
    val threadTitle: String,
) {
    /** Stable across rescans and unique within a scan, so the grid can key its items by it. */
    val id: String get() = "${key.board.value}/${key.thread.value}/${attachment.id}"
}

data class AllMediaUiState(
    val items: ImmutableList<AllMediaItem> = persistentListOf(),
    val boardsScanned: Int = 0,
    val boardsTotal: Int = 0,
    val isScanning: Boolean = false,
    /** Boards whose catalog could not be fetched this sweep; the rest of the wall still stands. */
    val failedBoards: Int = 0,
) {
    val isInitialLoad: Boolean get() = isScanning && items.isEmpty()
}

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
 * What this cannot show is media from *replies* beyond the teaser replies a catalog happens to
 * carry. Reaching those means one request per thread — thousands per sweep — which is exactly the
 * traffic pattern that gets a reader rate-limited, so the sweep stops at the catalog.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class AllMediaViewModel
    @Inject
    constructor(
        providerRegistry: ProviderRegistry,
        observeActiveProvider: ObserveActiveProviderUseCase,
        private val boardRepository: BoardRepository,
        settingsRepository: SettingsRepository,
    ) : ViewModel() {
        private val activeProvider: StateFlow<ImageBoardProvider> =
            observeActiveProvider()
                .stateIn(viewModelScope, SharingStarted.Eagerly, providerRegistry.default())

        private val refreshRequests = MutableStateFlow(0)

        private val _uiState = MutableStateFlow(AllMediaUiState())
        val uiState: StateFlow<AllMediaUiState> = _uiState.asStateFlow()

        private val _isRefreshing = MutableStateFlow(false)
        val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

        /**
         * Everything the sweep found, before [MediaFilter] is applied. Kept so switching between
         * images-only and videos-only re-filters what is already on screen instead of costing a
         * fresh sweep of every board.
         */
        private var collected: List<AllMediaItem> = emptyList()

        /** Source URLs already on the wall, so the same file reposted in two threads appears once. */
        private val seenUrls = mutableSetOf<String>()

        private var scanJob: Job? = null

        /** Drives tile sizing on the wall from the reader's thumbnail-size preference. */
        val settings: StateFlow<AppSettings> =
            settingsRepository.settings
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), AppSettings.Default)

        private val mediaFilter: StateFlow<MediaFilter> =
            settingsRepository.settings
                .map { it.mediaFilter }
                .distinctUntilChanged()
                .stateIn(viewModelScope, SharingStarted.Eagerly, MediaFilter.ALL)

        init {
            mediaFilter
                .onEach { filter ->
                    _uiState.update { it.copy(items = collected.visibleUnder(filter)) }
                }.launchIn(viewModelScope)

            activeProvider
                .flatMapLatest { provider ->
                    combine(
                        boardRepository.observeBoards(provider.metadata.id).distinctUntilChanged(),
                        settingsRepository.settings.map { ScanSettings(it) }.distinctUntilChanged(),
                        refreshRequests,
                    ) { boards, scanSettings, _ -> Triple(provider, boards, scanSettings) }
                }.onEach { (provider, boards, scanSettings) ->
                    startScan(provider, boards, scanSettings)
                }.launchIn(viewModelScope)
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

        private fun startScan(
            provider: ImageBoardProvider,
            boards: List<Board>,
            scanSettings: ScanSettings,
        ) {
            scanJob?.cancel()
            collected = emptyList()
            seenUrls.clear()

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
                    coroutineScope {
                        visibleBoards
                            .map { board ->
                                async { permits.withPermit { fetchBoardMedia(provider, board, scanSettings) } }
                            }.forEachIndexed { index, deferred ->
                                // Awaited in submission order rather than completion order: see the
                                // class comment on why the wall must not reshuffle as it fills.
                                deferred.await()?.let(::append)
                                _uiState.update { it.copy(boardsScanned = index + 1) }
                            }
                    }
                    _uiState.update { it.copy(isScanning = false) }
                    _isRefreshing.value = false
                }
        }

        /**
         * One board's attachments, or null if its catalog could not be fetched.
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
        ): List<AllMediaItem>? =
            try {
                val catalog = provider.getCatalog(CatalogRequest(provider.metadata.id, board.id))
                mediaItemsFrom(board, catalog, scanSettings.hiddenTokens)
            } catch (e: ProviderException) {
                Log.w(TAG, "Failed to sweep /${board.id.value}/", e)
                _uiState.update { it.copy(failedBoards = it.failedBoards + 1) }
                null
            }

        /**
         * Adds a board's haul to the wall. New files land at the end, so nothing already rendered
         * moves; duplicates of a file already shown are dropped.
         */
        private fun append(items: List<AllMediaItem>) {
            val fresh = items.filter { seenUrls.add(it.attachment.sourceUrl) }
            if (fresh.isEmpty()) return
            collected = collected + fresh
            _uiState.update { it.copy(items = collected.visibleUnder(mediaFilter.value)) }
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

/**
 * The settings a sweep actually depends on. Isolated from [AppSettings] so that changing an
 * unrelated preference — a theme, a download folder — does not throw away the whole wall and
 * re-fetch every board.
 */
internal data class ScanSettings(
    val hideNsfwBoards: Boolean,
    val hiddenTokens: Set<String>,
) {
    constructor(settings: AppSettings) : this(settings.hideNsfwBoards, settings.hiddenTagTokens())
}

/** The boards a sweep should visit: the reader's board filters, applied as the feed applies them. */
internal fun List<Board>.visibleUnder(scanSettings: ScanSettings): List<Board> =
    filterNot { board -> scanSettings.hideNsfwBoards && board.isNsfw }
        // Catches the permanent filter too, so a board it hides is never even fetched.
        .filterNot { board -> board.matchesFilterTokens(scanSettings.hiddenTokens) }
        .sortedBy { it.id.value }

/**
 * Every attachment a board's catalog carries, as wall items.
 *
 * Teaser replies count as well as opening posts: they cost nothing extra, and they are the only
 * reply-level media a catalog sweep can reach.
 */
internal fun mediaItemsFrom(
    board: Board,
    catalog: List<CatalogThread>,
    hiddenTokens: Set<String>,
): List<AllMediaItem> =
    catalog
        .filterNot { thread -> thread.matchesFilterTokens(hiddenTokens) }
        .flatMap { thread ->
            val title = thread.title()
            (listOf(thread.originalPost) + thread.previewReplies)
                .filterNot { post -> post.matchesFilterTokens(hiddenTokens) }
                .flatMap { post -> post.attachments }
                .filterNot { attachment -> attachment.isPermanentlyFiltered() }
                .map { attachment ->
                    AllMediaItem(
                        attachment = attachment,
                        key = thread.key,
                        boardTitle = board.id.value,
                        threadTitle = title,
                    )
                }
        }

private fun List<AllMediaItem>.visibleUnder(filter: MediaFilter): ImmutableList<AllMediaItem> =
    filter { filter.allows(it.attachment) }.toImmutableList()

/** A short label for the tile: the subject if the thread has one, otherwise the start of the OP. */
private fun CatalogThread.title(): String {
    val subject = originalPost.subject?.trim().orEmpty()
    if (subject.isNotEmpty()) return subject
    val comment = originalPost.comment.raw.trim()
    return if (comment.length > TITLE_MAX_CHARS) comment.take(TITLE_MAX_CHARS) + "…" else comment
}

private const val TITLE_MAX_CHARS = 60
