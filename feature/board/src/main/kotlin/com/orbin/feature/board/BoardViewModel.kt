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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Backs the board catalog screen. Navigation arguments are read from [SavedStateHandle] by the
 * field names of the type-safe route, so this feature does not depend on the app's route types.
 */
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

        /**
         * The catalog as the grid shows it: cached pages, filtered per collection. Filtering after
         * [cachedIn] means changing the setting re-filters the pages already loaded instead of
         * re-fetching the board.
         */
        val catalog: Flow<PagingData<CatalogThread>> =
            catalogRepository
                .catalogStream(ProviderId(providerId), BoardId(boardId), CatalogSort.BUMP_ORDER)
                .cachedIn(viewModelScope)
                .hidingMatches(hiddenTokens)
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

        /** Thread ids on this board already present in reading history, for "already read" title styling. */
        val visitedThreadIds: StateFlow<Set<Long>> =
            historyRepository
                .observeVisitedKeys()
                .map { keys ->
                    keys
                        .filter { it.provider.value == providerId && it.board.value == boardId }
                        .mapTo(mutableSetOf()) { it.thread.value }
                }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptySet())

        // The default for this session, from Settings. The grid's size toggle can temporarily
        // override it without changing the persisted preference.
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

/** Drops threads whose opening post matches one of the reader's hidden keywords. */
internal fun Flow<PagingData<CatalogThread>>.hidingMatches(tokens: Flow<Set<String>>): Flow<PagingData<CatalogThread>> =
    combine(this, tokens) { pagingData, hidden ->
        if (hidden.isEmpty()) {
            pagingData
        } else {
            pagingData.filter { thread -> !thread.matchesFilterTokens(hidden) }
        }
    }

/**
 * Applies the reader's [filters] to a catalog stream: each thread keeps only the media its filter
 * allows, and threads left with none drop out — a catalog cell is its OP's thumbnail, so a thread
 * with nothing to show would be an empty tile.
 */

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
