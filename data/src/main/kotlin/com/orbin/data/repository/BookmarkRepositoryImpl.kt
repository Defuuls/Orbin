package com.orbin.data.repository

import com.orbin.core.model.Bookmark
import com.orbin.core.model.ThreadKey
import com.orbin.data.database.dao.BookmarkDao
import com.orbin.data.database.toDomain
import com.orbin.data.database.toEntity
import com.orbin.domain.repository.BookmarkRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** Room-backed [BookmarkRepository]. Bookmarks persist across launches and drive thread watching. */
@Singleton
class BookmarkRepositoryImpl
    @Inject
    constructor(
        private val dao: BookmarkDao,
    ) : BookmarkRepository {
        override fun observeBookmarks(): Flow<List<Bookmark>> =
            dao.observeAll().map { list ->
                list.map { it.toDomain() }
            }

        override fun observeBookmark(key: ThreadKey): Flow<Bookmark?> =
            dao
                .observeOne(key.provider.value, key.board.value, key.thread.value)
                .map { it?.toDomain() }

        override suspend fun addBookmark(bookmark: Bookmark) = dao.upsert(bookmark.toEntity())

        override suspend fun removeBookmark(key: ThreadKey) =
            dao.deleteByKey(key.provider.value, key.board.value, key.thread.value)

        override suspend fun setWatched(
            key: ThreadKey,
            watched: Boolean,
        ) = dao.setWatched(key.provider.value, key.board.value, key.thread.value, watched)

        override suspend fun markRead(key: ThreadKey) =
            dao.markRead(key.provider.value, key.board.value, key.thread.value)

        override suspend fun watchedBookmarks(): List<Bookmark> = dao.watchedBookmarks().map { it.toDomain() }

        override suspend fun updateLatest(
            key: ThreadKey,
            latestReplyCount: Int,
            isThreadDead: Boolean,
        ) = dao.updateLatest(
            key.provider.value,
            key.board.value,
            key.thread.value,
            latestReplyCount,
            isThreadDead,
        )
    }
