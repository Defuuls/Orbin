package com.orbin.feature.board

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.LoadState
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.filter
import androidx.paging.map
import com.orbin.core.model.AppSettings
import com.orbin.core.model.BoardId
import com.orbin.core.model.Bookmark
import com.orbin.core.model.CatalogMode
import com.orbin.core.model.CatalogSort
import com.orbin.core.model.CatalogThread
import com.orbin.core.model.MediaFilter
import com.orbin.core.model.ProviderId
import com.orbin.core.model.ThreadKey
import com.orbin.core.model.filteredBy
import com.orbin.core.model.matchesFilterTokens
import com.orbin.core.model.unreadByThread
import com.orbin.core.reader.PreparedCatalogThread
import com.orbin.core.reader.prepareCatalogThread
import com.orbin.domain.repository.BookmarkRepository
import com.orbin.domain.repository.CatalogRepository
import com.orbin.domain.repository.HistoryRepository
import com.orbin.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Backs the board catalog screen. Navigation arguments are read from [SavedStateHandle] by the
 * field names of the type-safe route, so this feature does not depend on the app's route types.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class BoardViewModel
    @Inject
    constructor(
        private val savedStateHandle: SavedStateHandle,
        catalogRepository: CatalogRepository,
        private val bookmarkRepository: BookmarkRepository,
        historyRepository: HistoryRepository,
        private val settingsRepository: SettingsRepository,
    ) : ViewModel() {
        val providerId: String = savedStateHandle.get<String>("provider").orEmpty()
        val boardId: String = savedStateHandle.get<String>("board").orEmpty()
        val title: String = savedStateHandle.get<String>("title").orEmpty()

        private val provider = ProviderId(providerId)
        private val board = BoardId(boardId)

        // Nothing about how a catalog is presented is configurable: no reader tags, every kind of
        // media, and the OP's first attachment only. The permanent filter still applies.
        private val presentationSettings: Flow<CatalogPresentationSettings> =
            flowOf(
                CatalogPresentationSettings(
                    hiddenTokens = emptySet(),
                    includeHarsh = false,
                    mediaFilter = MediaFilter.ALL,
                    mediaScroll = false,
                ),
            )

        /** Device-wide catalog presentation, shared by settings and the catalog overflow. */
        val settings: StateFlow<AppSettings> =
            settingsRepository.settings
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), AppSettings.Default)

        val catalogSort: StateFlow<CatalogSort> =
            savedStateHandle.getStateFlow(CATALOG_SORT_KEY, CatalogSort.BUMP_ORDER)

        fun cycleCatalogSort() {
            savedStateHandle[CATALOG_SORT_KEY] = catalogSort.value.next()
        }

        fun setCatalogSort(sort: CatalogSort) {
            savedStateHandle[CATALOG_SORT_KEY] = sort
        }

        fun setCatalogMode(mode: CatalogMode) {
            viewModelScope.launch { settingsRepository.setCatalogMode(mode) }
        }

        private val catalogVisible = MutableStateFlow(true)
        private val _catalogRefreshFailure = MutableStateFlow<Throwable?>(null)
        val catalogRefreshFailure: StateFlow<Throwable?> = _catalogRefreshFailure.asStateFlow()

        /** The rendered catalog stays active in two panes and stops remote work when hidden. */
        fun setCatalogVisible(visible: Boolean) {
            catalogVisible.value = visible
        }

        private val coverViolentMedia: Flow<Boolean> =
            settingsRepository.settings
                .map { it.coverViolentMedia }
                .distinctUntilChanged()

        val catalog: Flow<PagingData<CatalogThread>> =
            combine(catalogSort, coverViolentMedia) { sort, _ -> sort }
                .flatMapLatest { current ->
                    _catalogRefreshFailure.value = null
                    catalogRepository.catalogStreamWhileVisible(provider, board, current, catalogVisible) {
                        _catalogRefreshFailure.value = it
                    }
                }.cachedIn(viewModelScope)
                .presentedBy(presentationSettings)

        /** Immutable row formatting runs once per loaded page, never in a composition. */
        val preparedCatalog: Flow<PagingData<PreparedCatalogThread>> =
            catalog
                .map { page ->
                    val nowMillis = System.currentTimeMillis()
                    page.map { thread -> prepareCatalogThread(thread, nowMillis) }
                }.cachedIn(viewModelScope)

        private val bookmarkState: StateFlow<BoardBookmarkState> =
            bookmarkRepository
                .observeBookmarks(provider, board)
                .map(List<Bookmark>::toBoardState)
                .stateIn(
                    viewModelScope,
                    SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                    BoardBookmarkState(),
                )

        val watchedThreadIds: StateFlow<Set<Long>> =
            bookmarkState
                .map { it.watchedIds }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptySet())

        val watchedUnread: StateFlow<Map<Long, Int>> =
            bookmarkState
                .map { it.unreadByThread }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyMap())

        /** Uses a board-scoped Room query rather than materializing the user's entire history. */
        val visitedThreadIds: StateFlow<Set<Long>> =
            historyRepository
                .observeVisitedThreadIds(provider, board)
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptySet())

        fun toggleThreadSubscription(thread: CatalogThread) {
            viewModelScope.launch {
                val key = thread.key
                if (thread.key.thread.value in watchedThreadIds.value) {
                    bookmarkRepository.setWatched(key, false)
                } else {
                    val existing = bookmarkRepository.getBookmark(key)
                    if (existing != null) {
                        bookmarkRepository.setWatched(key, true)
                    } else {
                        bookmarkRepository.addBookmark(thread.toWatchedBookmark())
                    }
                }
            }
        }

        private fun CatalogThread.toWatchedBookmark(): Bookmark =
            Bookmark(
                key = ThreadKey(provider, board, key.thread),
                title = originalPost.subject ?: "/$boardId/",
                thumbnailUrl = originalPost.attachments.firstOrNull()?.thumbnailUrl,
                createdAtMillis = System.currentTimeMillis(),
                isWatched = true,
                lastSeenReplyCount = stats.replyCount,
                latestReplyCount = stats.replyCount,
                isThreadDead = stats.isClosed || stats.isArchived,
            )

        private companion object {
            const val STOP_TIMEOUT_MS = 5_000L
            const val CATALOG_SORT_KEY = "catalogSort"
        }
    }

internal data class CatalogPresentationSettings(
    val hiddenTokens: Set<String>,
    val includeHarsh: Boolean,
    val mediaFilter: MediaFilter,
    val mediaScroll: Boolean,
)

/** The visible catalog state is derived from Paging's retained snapshot, never a second cache. */
internal data class BoardCatalogLoadState(
    val content: BoardCatalogContent,
    val isRefreshing: Boolean,
    val failure: Throwable? = null,
)

internal enum class BoardCatalogContent { LOADING, ERROR, EMPTY, CONTENT }

/** A refresh failure with rows stays CONTENT; only an initial failure takes over the pane. */
internal fun boardCatalogLoadState(
    itemCount: Int,
    refresh: LoadState,
): BoardCatalogLoadState =
    BoardCatalogLoadState(
        content =
            when {
                itemCount > 0 -> BoardCatalogContent.CONTENT
                refresh is LoadState.Loading -> BoardCatalogContent.LOADING
                refresh is LoadState.Error -> BoardCatalogContent.ERROR
                else -> BoardCatalogContent.EMPTY
            },
        isRefreshing = refresh is LoadState.Loading,
        failure = (refresh as? LoadState.Error)?.error,
    )

private data class BoardBookmarkState(
    val watchedIds: Set<Long> = emptySet(),
    val unreadByThread: Map<Long, Int> = emptyMap(),
)

private fun List<Bookmark>.toBoardState(): BoardBookmarkState =
    BoardBookmarkState(watchedIds = mapTo(mutableSetOf()) { it.key.thread.value }, unreadByThread = unreadByThread())

internal fun Flow<PagingData<CatalogThread>>.presentedBy(
    settings: Flow<CatalogPresentationSettings>,
): Flow<PagingData<CatalogThread>> =
    kotlinx.coroutines.flow.combine(this, settings) { pagingData, presentation ->
        pagingData
            .filter { thread ->
                withContext(Dispatchers.Default) {
                    !thread.matchesFilterTokens(presentation.hiddenTokens, presentation.includeHarsh)
                }
            }.map { thread ->
                if (presentation.mediaFilter.isActive) thread.filteredBy(presentation.mediaFilter) else thread
            }.filter { thread ->
                !presentation.mediaFilter.isActive || thread.originalPost.attachments.isNotEmpty()
            }.map { thread ->
                if (presentation.mediaScroll || thread.originalPost.attachments.size <= 1) {
                    thread
                } else {
                    thread.copy(
                        originalPost =
                            thread.originalPost.copy(
                                attachments =
                                    thread.originalPost.attachments
                                        .take(1)
                                        .toImmutableList(),
                            ),
                    )
                }
            }
    }
