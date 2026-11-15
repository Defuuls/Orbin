package com.orbin.ios

import com.orbin.core.model.BoardId
import com.orbin.core.model.Bookmark
import com.orbin.core.model.Post
import com.orbin.core.model.PostId
import com.orbin.core.model.ProviderId
import com.orbin.core.model.Thread
import com.orbin.core.model.ThreadId
import com.orbin.core.model.ThreadKey
import com.orbin.core.model.ThreadStats
import com.orbin.domain.notification.ThreadNotifier
import com.orbin.domain.repository.InMemoryThreadMemoryRepository
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WatchedThreadsTest {
    private val key = ThreadKey(ProviderId("site"), BoardId("g"), ThreadId(7))

    private class Posted : ThreadNotifier {
        val posts = mutableListOf<Triple<ThreadKey, String, Int>>()

        override suspend fun notifyThreadUpdate(
            key: ThreadKey,
            title: String,
            newReplyCount: Int,
        ) {
            posts += Triple(key, title, newReplyCount)
        }
    }

    private fun thread(replies: Int) =
        Thread(
            key = key,
            originalPost = Post(id = PostId(7), board = key.board, threadId = key.thread, isOriginalPost = true),
            stats = ThreadStats(replyCount = replies),
        )

    @Test
    fun loadingAThreadKeepsUnreadUntilTheReaderActuallySeesItsPosts() =
        runTest {
            val bookmarks = FakeBookmarks()
            bookmarks.addBookmark(
                Bookmark(
                    key,
                    "Weekly",
                    createdAtMillis = 0,
                    isWatched = true,
                    lastSeenReplyCount = 2,
                    latestReplyCount = 2,
                ),
            )
            val loaded =
                thread(5).copy(
                    replies =
                        (101L..105L)
                            .map {
                                Post(id = PostId(it), board = key.board, threadId = key.thread, isOriginalPost = false)
                            }.toImmutableList(),
                )
            val watched = WatchedThreads(bookmarks, backgroundScope, { 0 }) { loaded }

            assertEquals("103", watched.read(loaded))
            assertEquals(5, bookmarks.getBookmark(key)?.latestReplyCount)
            assertEquals(2, bookmarks.getBookmark(key)?.lastSeenReplyCount)
        }

    @Test
    fun catalogUnreadUsesQualifiedVisibleReadMemoryWithoutChangingStoredBookmarkCount() =
        runTest {
            val bookmarks = FakeBookmarks()
            bookmarks.addBookmark(Bookmark(key, "Weekly", createdAtMillis = 0, isWatched = true, latestReplyCount = 3))
            val memories = InMemoryThreadMemoryRepository()
            memories.update(key) { it.copy(knownPostIds = setOf(7, 8, 9, 10), seenPostIds = setOf(7, 8)) }
            val otherSite = key.copy(provider = ProviderId("other"))
            bookmarks.addBookmark(
                Bookmark(otherSite, "Other site", createdAtMillis = 0, isWatched = true, latestReplyCount = 3),
            )
            memories.update(otherSite) { it.copy(knownPostIds = setOf(7, 8, 9, 10), seenPostIds = setOf(7, 8, 9, 10)) }
            val watched = WatchedThreads(bookmarks, backgroundScope, { 0 }, threadMemory = memories) { thread(3) }
            val board =
                SiteBoard(
                    key.provider,
                    "Site",
                    com.orbin.core.model
                        .Board(key.board, "Technology"),
                )

            assertEquals(mapOf(7L to 2), watched.unread(board).first())
            assertEquals(emptyMap(), watched.unread(board.copy(provider = otherSite.provider)).first())
            assertEquals(0, bookmarks.getBookmark(key)?.lastSeenReplyCount)
            assertEquals(0, bookmarks.getBookmark(otherSite)?.lastSeenReplyCount)
        }

    @Test
    fun aRefreshStoresNewRepliesAndPostsTheOnesNotYetSeen() =
        runTest {
            val bookmarks = FakeBookmarks()
            bookmarks.addBookmark(
                Bookmark(
                    key,
                    "Weekly",
                    createdAtMillis = 0,
                    isWatched = true,
                    lastSeenReplyCount = 2,
                    latestReplyCount = 2,
                ),
            )
            val notifier = Posted()
            val watched = WatchedThreads(bookmarks, backgroundScope, { 0 }, notifier) { thread(replies = 5) }

            watched.refreshNow()

            assertEquals(5, bookmarks.getBookmark(key)?.latestReplyCount)
            assertEquals(listOf(Triple(key, "Weekly", 3)), notifier.posts)
        }

    @Test
    fun nothingNewPostsNothing() =
        runTest {
            val bookmarks = FakeBookmarks()
            bookmarks.addBookmark(
                Bookmark(
                    key,
                    "Weekly",
                    createdAtMillis = 0,
                    isWatched = true,
                    lastSeenReplyCount = 5,
                    latestReplyCount = 5,
                ),
            )
            val notifier = Posted()
            WatchedThreads(bookmarks, backgroundScope, { 0 }, notifier) { thread(replies = 5) }.refreshNow()

            assertTrue(notifier.posts.isEmpty())
        }

    @Test
    fun failedCountWriteDoesNotPostAnAlertThatWillRepeat() =
        runTest {
            val bookmarks =
                object : FakeBookmarks() {
                    override suspend fun updateLatest(
                        key: ThreadKey,
                        latestReplyCount: Int,
                        isThreadDead: Boolean,
                    ) {
                        error("database write failed")
                    }
                }
            bookmarks.addBookmark(
                Bookmark(
                    key,
                    "Weekly",
                    createdAtMillis = 0,
                    isWatched = true,
                    lastSeenReplyCount = 2,
                    latestReplyCount = 2,
                ),
            )
            val notifier = Posted()
            WatchedThreads(bookmarks, backgroundScope, { 0 }, notifier) { thread(5) }.refreshNow()

            assertEquals(2, bookmarks.getBookmark(key)?.latestReplyCount)
            assertTrue(notifier.posts.isEmpty())
        }

    @Test
    fun savedOnlyThreadsDoNotExposeWatchedUnreadState() =
        runTest {
            val bookmarks = FakeBookmarks()
            bookmarks.addBookmark(Bookmark(key, "Saved", createdAtMillis = 0, isWatched = false))
            val watched = WatchedThreads(bookmarks, backgroundScope, { 0 }) { thread(0) }
            assertNull(watched.read(thread(3)))
            assertEquals(0, bookmarks.getBookmark(key)?.latestReplyCount)
        }

    @Test
    fun savedOnlyThreadsCanBeWatchedAndUnwatchedWithoutDeletingTheBookmark() =
        runTest {
            val bookmarks = FakeBookmarks()
            bookmarks.addBookmark(Bookmark(key, "Saved", createdAtMillis = 0, isWatched = false))
            val watched = WatchedThreads(bookmarks, backgroundScope, { 0 }) { thread(0) }
            assertEquals(false, watched.watching(key).first())
            watched.toggle(thread(0))
            assertTrue(watched.watching(key).first { it })
            watched.toggle(thread(0))
            assertEquals(false, watched.watching(key).first { !it })
            assertEquals("Saved", bookmarks.getBookmark(key)?.title)
        }

    @Test
    fun watchingAThreadIsWhenNotificationsAreAskedFor() =
        runTest {
            var asked = 0
            val bookmarks = FakeBookmarks()
            val watched = WatchedThreads(bookmarks, backgroundScope, { 0 }, onWatch = { asked++ }) { thread(0) }

            watched.toggle(thread(0))
            bookmarks.observeBookmark(key).first { it != null }

            assertEquals(1, asked)
        }
}
