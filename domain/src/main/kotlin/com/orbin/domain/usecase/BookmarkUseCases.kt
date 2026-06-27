package com.orbin.domain.usecase

import com.orbin.core.model.Bookmark
import com.orbin.core.model.ThreadKey
import com.orbin.domain.repository.BookmarkRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/** Streams the user's bookmarks for the bookmarks screen. */
class ObserveBookmarksUseCase
    @Inject
    constructor(
        private val repository: BookmarkRepository,
    ) {
        operator fun invoke(): Flow<List<Bookmark>> = repository.observeBookmarks()
    }

/**
 * Toggles a bookmark on/off for a thread. Adding requires the thread metadata so the bookmark can
 * be shown without re-fetching; removing only needs the key.
 */
class ToggleBookmarkUseCase
    @Inject
    constructor(
        private val repository: BookmarkRepository,
    ) {
        suspend operator fun invoke(
            bookmark: Bookmark,
            isCurrentlyBookmarked: Boolean,
        ) {
            if (isCurrentlyBookmarked) {
                repository.removeBookmark(bookmark.key)
            } else {
                repository.addBookmark(bookmark)
            }
        }
    }

/** Enables/disables background watching (update polling + notifications) for a bookmark. */
class SetThreadWatchedUseCase
    @Inject
    constructor(
        private val repository: BookmarkRepository,
    ) {
        suspend operator fun invoke(
            key: ThreadKey,
            watched: Boolean,
        ) = repository.setWatched(key, watched)
    }
