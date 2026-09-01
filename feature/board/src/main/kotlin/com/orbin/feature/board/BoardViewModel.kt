package com.orbin.feature.board

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.filter
import androidx.paging.map
import com.orbin.core.model.BoardId
import com.orbin.core.model.Bookmark
import com.orbin.core.model.CatalogSort
import com.orbin.core.model.CatalogThread
import com.orbin.core.model.MediaFilter
import com.orbin.core.model.ProviderId
import com.orbin.core.model.ThreadKey
import com.orbin.core.model.ThumbnailSize
import com.orbin.core.model.filteredBy
import com.orbin.core.model.hiddenTagTokens
import com.orbin.core.model.matchesFilterTokens
import com.orbin.domain.repository.BookmarkRepository
import com.orbin.domain.repository.CatalogRepository
import com.orbin.domain.repository.HistoryRepository
import com.orbin.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
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
        settingsRepository: SettingsRepository,
    ) : ViewModel() {
        val providerId: String = savedStateHandle.get<String>("provider").orEmpty()
        val boardId: String = savedStateHandle.get<String>("board").orEmpty()
        val title: String = savedStateHandle.get<String>("title").orEmpty()

        private val provider = ProviderId(providerId)
        private val board = BoardId(boardId)

        private val presentationSettings: Flow<CatalogPresentationSettings> =
            settingsRepository.settings
                .map { settings ->
                    CatalogPresentationSettings(
                        hiddenTokens = settings.hiddenTagTokens(),
                        includeHarsh = settings.harshContentFilter,
                        mediaFilter = settings.mediaFilter,
                        mediaScroll = settings.mediaScrollBoardView,
                    )
                }.distinctUntilChanged()

        val catalogSort: StateFlow<CatalogSort> =
            savedStateHandle.getStateFlow(CATALOG_SORT_KEY, CatalogSort.BUMP_ORDER)

        fun cycleCatalogSort() {
            val values = CatalogSort.entries
            val current = catalogSort.value
            savedStateHandle[CATALOG_SORT_KEY] = values[(values.indexOf(current) + 1) % values.size]
        }

        /**
         * The catalog as the grid shows it. Network pages are cached first; local settings then
         * transform those cached pages in one pass, so display changes never refetch the board.
         */
        val catalog: Flow<PagingData<CatalogThread>> =
            catalogSort
                .flatMapLatest { current -> catalogRepository.catalogStream(provider, board, current) }
                .cachedIn(viewModelScope)
                .presentedBy(presentationSettings)

        /** One board-scoped bookmark query produces both watched ids and unread counts. */
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

        /** Thread ids on this board already present in reading history, for "already read" title styling. */
        val visitedThreadIds: StateFlow<Set<Long>> =
            historyRepository
                .observeVisitedKeys()
                .map { keys ->
                    keys
                        .filter { it.provider == provider && it.board == board }
                        .mapTo(mutableSetOf()) { it.thread.value }
                }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptySet())

        val thumbnailSize: StateFlow<ThumbnailSize> =
            settingsRepository.settings
                .map { it.thumbnailSize }
                .distinctUntilChanged()
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), ThumbnailSize.MEDIUM)

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

private data class BoardBookmarkState(
    val watchedIds: Set<Long> = emptySet(),
    val unreadByThread: Map<Long, Int> = emptyMap(),
)

private fun List<Bookmark>.toBoardState(): BoardBookmarkState {
    val watched = mutableSetOf<Long>()
    val unread = mutableMapOf<Long, Int>()
    for (bookmark in this) {
        val threadId = bookmark.key.thread.value
        watched += threadId
        if (bookmark.hasUnread) unread[threadId] = bookmark.unreadCount
    }
    return BoardBookmarkState(watched, unread)
}

internal fun Flow<PagingData<CatalogThread>>.presentedBy(
    settings: Flow<CatalogPresentationSettings>,
): Flow<PagingData<CatalogThread>> =
    kotlinx.coroutines.flow.combine(this, settings) { pagingData, presentation ->
        pagingData
            .filter { thread ->
                !thread.matchesFilterTokens(presentation.hiddenTokens, presentation.includeHarsh)
            }
            .map { thread ->
                if (presentation.mediaFilter.isActive) thread.filteredBy(presentation.mediaFilter) else thread
            }
            .filter { thread ->
                !presentation.mediaFilter.isActive || thread.originalPost.attachments.isNotEmpty()
            }
            .map { thread ->
                if (presentation.mediaScroll || thread.originalPost.attachments.size <= 1) {
                    thread
                } else {
                    thread.copy(
                        originalPost =
                            thread.originalPost.copy(
                                attachments = thread.originalPost.attachments.take(1).toImmutableList(),
                            ),
                    )
                }
            }
    }
