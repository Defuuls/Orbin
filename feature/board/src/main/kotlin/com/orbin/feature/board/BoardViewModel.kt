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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
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
        savedStateHandle: SavedStateHandle,
        catalogRepository: CatalogRepository,
        private val bookmarkRepository: BookmarkRepository,
        historyRepository: HistoryRepository,
        settingsRepository: SettingsRepository,
    ) : ViewModel() {
        val providerId: String = savedStateHandle.get<String>("provider").orEmpty()
        val boardId: String = savedStateHandle.get<String>("board").orEmpty()
        val title: String = savedStateHandle.get<String>("title").orEmpty()

        private val mediaFilter: Flow<MediaFilter> =
            settingsRepository.settings.map { it.mediaFilter }.distinctUntilChanged()

        /**
         * The reader's hidden keywords. The subscribed feed has always applied these; the board
         * catalog never did, so a keyword you had hidden reappeared the moment you opened the
         * board it was posted on — which is where you actually read.
         */
        private val hiddenTokens: Flow<Set<String>> =
            settingsRepository.settings.map { it.hiddenTagTokens() }.distinctUntilChanged()

        private val includeHarsh: Flow<Boolean> =
            settingsRepository.settings.map { it.harshContentFilter }.distinctUntilChanged()

        private val sort = MutableStateFlow(CatalogSort.BUMP_ORDER)

        val catalogSort: StateFlow<CatalogSort> = sort

        fun cycleCatalogSort() {
            val values = CatalogSort.entries
            sort.value = values[(values.indexOf(sort.value) + 1) % values.size]
        }

        /**
         * The catalog as the grid shows it: cached pages, filtered per collection. Filtering after
         * [cachedIn] means changing the setting re-filters the pages already loaded instead of
         * re-fetching the board. Changing [catalogSort] starts a new stream.
         */
        val catalog: Flow<PagingData<CatalogThread>> =
            sort
                .flatMapLatest { current ->
                    catalogRepository.catalogStream(ProviderId(providerId), BoardId(boardId), current)
                }.cachedIn(viewModelScope)
                .hidingMatches(hiddenTokens, includeHarsh)
                .filteredBy(mediaFilter)

        val watchedThreadIds: StateFlow<Set<Long>> =
            bookmarkRepository
                .observeBookmarks()
                .map { bookmarks ->
                    bookmarks
                        .filter {
                            it.isWatched &&
                                it.key.provider.value == providerId &&
                                it.key.board.value == boardId
                        }.map { it.key.thread.value }
                        .toSet()
                }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptySet())

        val watchedUnread: StateFlow<Map<Long, Int>> =
            bookmarkRepository
                .observeBookmarks()
                .map { bookmarks ->
                    bookmarks
                        .filter {
                            it.isWatched &&
                                it.hasUnread &&
                                it.key.provider.value == providerId &&
                                it.key.board.value == boardId
                        }.associate { it.key.thread.value to it.unreadCount }
                }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyMap())

        /** Thread ids on this board already present in reading history, for "already read" title styling. */
        val visitedThreadIds: StateFlow<Set<Long>> =
            historyRepository
                .observeVisitedKeys()
                .map { keys ->
                    keys
                        .filter { it.provider.value == providerId && it.board.value == boardId }
                        .mapTo(mutableSetOf()) { it.thread.value }
                }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptySet())

        val thumbnailSize: StateFlow<ThumbnailSize> =
            settingsRepository.settings
                .map { it.thumbnailSize }
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
                key = ThreadKey(ProviderId(providerId), BoardId(boardId), key.thread),
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
        }
    }

internal fun Flow<PagingData<CatalogThread>>.hidingMatches(
    tokens: Flow<Set<String>>,
    includeHarsh: Flow<Boolean>,
): Flow<PagingData<CatalogThread>> =
    combine(this, tokens, includeHarsh) { pagingData, hidden, harsh ->
        pagingData.filter { thread -> !thread.matchesFilterTokens(hidden, harsh) }
    }

internal fun Flow<PagingData<CatalogThread>>.filteredBy(filters: Flow<MediaFilter>): Flow<PagingData<CatalogThread>> =
    combine(this, filters) { pagingData, filter ->
        if (!filter.isActive) {
            pagingData
        } else {
            pagingData
                .map { thread -> thread.filteredBy(filter) }
                .filter { thread -> thread.originalPost.attachments.isNotEmpty() }
        }
    }
