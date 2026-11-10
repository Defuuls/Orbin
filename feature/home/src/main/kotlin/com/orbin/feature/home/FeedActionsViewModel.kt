package com.orbin.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.orbin.core.model.Bookmark
import com.orbin.core.model.ThreadKey
import com.orbin.domain.repository.BookmarkRepository
import com.orbin.domain.repository.ThreadRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject

/** Feed actions use the full source-qualified key, matching the thread reader's saved state. */
@HiltViewModel
internal class FeedActionsViewModel
    @Inject
    constructor(
        private val bookmarkRepository: BookmarkRepository,
        private val threadRepository: ThreadRepository,
    ) : ViewModel() {
        private val actions = Mutex()

        val bookmarks: StateFlow<List<Bookmark>> =
            bookmarkRepository
                .observeBookmarks()
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

        /** Unwatching stops refreshes while keeping the existing saved reference. */
        fun toggleWatch(entry: FeedEntry) {
            viewModelScope.launch {
                actions.withLock {
                    val existing = bookmarkRepository.getBookmark(entry.key)
                    if (existing == null) {
                        bookmarkRepository.addBookmark(entry.bookmark(watched = true))
                    } else {
                        bookmarkRepository.setWatched(entry.key, !existing.isWatched)
                    }
                }
            }
        }

        /** Saved is the same bookmark contract used by the reader and Saved destination. */
        fun toggleSaved(entry: FeedEntry) {
            viewModelScope.launch {
                actions.withLock {
                    val existing = bookmarkRepository.getBookmark(entry.key)
                    if (existing == null) {
                        bookmarkRepository.addBookmark(entry.bookmark(watched = false))
                    } else if (!existing.isWatched) {
                        // A watch requires this reference; the UI explains that Watch must be
                        // turned off before the reader can remove it from Saved.
                        bookmarkRepository.removeBookmark(entry.key)
                    }
                }
            }
        }

        fun shareUrl(key: ThreadKey): String? = threadRepository.threadWebUrl(key)

        private fun FeedEntry.bookmark(watched: Boolean): Bookmark =
            Bookmark(
                key = key,
                title = title,
                thumbnailUrl = attachment?.thumbnailUrl,
                createdAtMillis = System.currentTimeMillis(),
                isWatched = watched,
                lastSeenReplyCount = row.replies,
                latestReplyCount = row.replies,
            )

        private companion object {
            const val STOP_TIMEOUT_MS = 5_000L
        }
    }
