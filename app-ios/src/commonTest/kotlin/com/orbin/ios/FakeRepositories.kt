package com.orbin.ios

import com.orbin.core.model.Bookmark
import com.orbin.core.model.HistoryEntry
import com.orbin.core.model.PostId
import com.orbin.core.model.ThreadKey
import com.orbin.domain.repository.BookmarkRepository
import com.orbin.domain.repository.HistoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/** Bookmarks kept in memory. The Room-backed one is tested against real SQLite in `:storage`. */
class FakeBookmarks : BookmarkRepository {
    val saved = MutableStateFlow<Map<ThreadKey, Bookmark>>(emptyMap())

    override fun observeBookmarks(): Flow<List<Bookmark>> = saved.map { it.values.toList() }

    override fun observeBookmark(key: ThreadKey): Flow<Bookmark?> = saved.map { it[key] }

    override suspend fun getBookmark(key: ThreadKey): Bookmark? = saved.value[key]

    override suspend fun addBookmark(bookmark: Bookmark) = saved.update { it + (bookmark.key to bookmark) }

    override suspend fun removeBookmark(key: ThreadKey) = saved.update { it - key }

    override suspend fun setWatched(
        key: ThreadKey,
        watched: Boolean,
    ) = saved.update { all -> all[key]?.let { all + (key to it.copy(isWatched = watched)) } ?: all }

    override suspend fun markRead(key: ThreadKey) = Unit

    override suspend fun watchedBookmarks(): List<Bookmark> = saved.value.values.filter { it.isWatched }

    override suspend fun updateLatest(
        key: ThreadKey,
        latestReplyCount: Int,
        isThreadDead: Boolean,
    ) = Unit
}

/** Reading history kept in memory. */
class FakeHistory : HistoryRepository {
    val entries = MutableStateFlow<Map<ThreadKey, HistoryEntry>>(emptyMap())

    override fun observeHistory(): Flow<List<HistoryEntry>> = entries.map { it.values.toList() }

    override fun observeVisitedKeys(): Flow<Set<ThreadKey>> = entries.map { it.keys }

    override suspend fun getEntry(key: ThreadKey): HistoryEntry? = entries.value[key]

    override suspend fun record(entry: HistoryEntry) = entries.update { it + (entry.key to entry) }

    override suspend fun updateScrollPosition(
        key: ThreadKey,
        postId: PostId,
        offsetPx: Int,
    ) = Unit

    override suspend fun clear() = entries.update { emptyMap() }
}
