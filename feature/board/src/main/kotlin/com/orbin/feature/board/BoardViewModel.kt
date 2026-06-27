package com.orbin.feature.board

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.orbin.core.model.BoardId
import com.orbin.core.model.Bookmark
import com.orbin.core.model.CatalogSort
import com.orbin.core.model.CatalogThread
import com.orbin.core.model.ProviderId
import com.orbin.core.model.ThreadKey
import com.orbin.domain.repository.BookmarkRepository
import com.orbin.domain.repository.CatalogRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
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
    ) : ViewModel() {
        val providerId: String = savedStateHandle.get<String>("provider").orEmpty()
        val boardId: String = savedStateHandle.get<String>("board").orEmpty()
        val title: String = savedStateHandle.get<String>("title").orEmpty()

        val catalog: Flow<PagingData<CatalogThread>> =
            catalogRepository
                .catalogStream(ProviderId(providerId), BoardId(boardId), CatalogSort.BUMP_ORDER)
                .cachedIn(viewModelScope)

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

        fun toggleThreadSubscription(thread: CatalogThread) {
            viewModelScope.launch {
                val key = thread.key
                if (thread.key.thread.value in watchedThreadIds.value) {
                    bookmarkRepository.setWatched(key, false)
                } else {
                    bookmarkRepository.addBookmark(thread.toWatchedBookmark())
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
