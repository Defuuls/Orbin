package com.orbin.data.database

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.orbin.core.model.BoardId
import com.orbin.core.model.Bookmark
import com.orbin.core.model.HistoryEntry
import com.orbin.core.model.PostId
import com.orbin.core.model.ProviderId
import com.orbin.core.model.ThreadId
import com.orbin.core.model.ThreadKey
import com.orbin.data.repository.BookmarkRepositoryImpl
import com.orbin.data.repository.HistoryRepositoryImpl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The shared database the way the iOS app opens it — Room over the bundled SQLite driver — on the
 * iOS simulator. Android's copy is covered by `data`'s tests over its own open helper; this is the
 * proof that the same schema and repositories work on the other platform.
 */
class SharedDatabaseTest {
    private val database =
        Room
            .inMemoryDatabaseBuilder<OrbinDatabase>()
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
            .build()

    private val key = ThreadKey(ProviderId("p"), BoardId("g"), ThreadId(7))

    @AfterTest
    fun close() = database.close()

    @Test
    fun aBookmarkIsStoredReadBackAndRemoved() =
        runTest {
            val bookmarks = BookmarkRepositoryImpl(database.bookmarkDao())
            val bookmark =
                Bookmark(
                    key = key,
                    title = "Hi",
                    thumbnailUrl = "https://i.example/7s.jpg",
                    createdAtMillis = 5L,
                    lastSeenReplyCount = 3,
                    latestReplyCount = 3,
                )

            bookmarks.addBookmark(bookmark)
            assertEquals(bookmark, bookmarks.getBookmark(key))
            assertEquals(listOf(bookmark), bookmarks.observeBookmarks(key.provider, key.board).first())

            bookmarks.updateLatest(key, latestReplyCount = 8, isThreadDead = false)
            assertEquals(5, bookmarks.getBookmark(key)?.unreadCount)

            bookmarks.removeBookmark(key)
            assertNull(bookmarks.observeBookmark(key).first())
        }

    @Test
    fun aVisitMarksTheThreadReadOnItsBoardOnly() =
        runTest {
            val history = HistoryRepositoryImpl(database.historyDao())

            history.record(
                HistoryEntry(key = key, title = "Hi", lastVisitedMillis = 9L, lastReadPostId = PostId(7)),
            )

            assertEquals(setOf(7L), history.observeVisitedThreadIds(key.provider, key.board).first())
            assertEquals(emptySet(), history.observeVisitedThreadIds(key.provider, BoardId("b")).first())
            assertEquals(PostId(7), history.getEntry(key)?.lastReadPostId)
        }
}
