package com.orbin.core.testing.repository

import com.orbin.core.model.Bookmark
import com.orbin.core.model.ThreadKey
import com.orbin.domain.repository.BookmarkRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** In-memory [BookmarkRepository] for ViewModel tests. Deterministic and free of Android/Room. */
class FakeBookmarkRepository(
    initial: List<Bookmark> = emptyList(),
) : BookmarkRepository {
    private val state = MutableStateFlow(initial.associateBy { it.key })

    override fun observeBookmarks(): Flow<List<Bookmark>> = state.map { it.values.toList() }

    override fun observeBookmark(key: ThreadKey): Flow<Bookmark?> = state.map { it[key] }

    override suspend fun addBookmark(bookmark: Bookmark) {
        state.value = state.value + (bookmark.key to bookmark)
    }

    override suspend fun removeBookmark(key: ThreadKey) {
        state.value = state.value - key
    }

    override suspend fun setWatched(
        key: ThreadKey,
        watched: Boolean,
    ) {
        state.value[key]?.let { state.value = state.value + (key to it.copy(isWatched = watched)) }
    }

    override suspend fun markRead(key: ThreadKey) {
        state.value[key]?.let {
            state.value = state.value + (key to it.copy(lastSeenReplyCount = it.latestReplyCount))
        }
    }

    override suspend fun watchedBookmarks(): List<Bookmark> = state.value.values.filter { it.isWatched }

    override suspend fun updateLatest(
        key: ThreadKey,
        latestReplyCount: Int,
        isThreadDead: Boolean,
    ) {
        state.value[key]?.let {
            state.value =
                state.value + (key to it.copy(latestReplyCount = latestReplyCount, isThreadDead = isThreadDead))
        }
    }
}
