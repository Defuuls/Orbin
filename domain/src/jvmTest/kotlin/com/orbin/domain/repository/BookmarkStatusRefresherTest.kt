package com.orbin.domain.repository

import com.orbin.core.model.BoardId
import com.orbin.core.model.Bookmark
import com.orbin.core.model.Post
import com.orbin.core.model.PostId
import com.orbin.core.model.ProviderId
import com.orbin.core.model.Thread
import com.orbin.core.model.ThreadAvailability
import com.orbin.core.model.ThreadId
import com.orbin.core.model.ThreadKey
import com.orbin.core.model.ThreadMemory
import com.orbin.core.model.ThreadStats
import com.orbin.domain.bookmarks.BookmarkStatusRefresher
import com.orbin.domain.bookmarks.collectionUnread
import com.orbin.provider.api.ProviderException
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class BookmarkStatusRefresherTest {
    private fun key(id: Long) = ThreadKey(ProviderId("fourchan"), BoardId("g"), ThreadId(id))

    private fun thread(
        key: ThreadKey,
        archived: Boolean,
    ) = Thread(
        key,
        Post(PostId(key.thread.value), key.board, key.thread, true),
        stats = ThreadStats(replyCount = 8, isArchived = archived),
    )

    @Test
    fun refreshesUnwatchedAndWatchedAndRetainsLastStatusOnTransientFailures() =
        runTest {
            val repository = mockk<BookmarkRepository>(relaxed = true)
            val keys = (1L..4L).map(::key)
            coEvery { repository.observeBookmarks() } returns
                flowOf(
                    keys.mapIndexed { index, key ->
                        Bookmark(key, "Thread", createdAtMillis = 0, isWatched = index == 0)
                    },
                )
            val calls = mutableListOf<ThreadKey>()
            val refresh =
                BookmarkStatusRefresher(repository, { key ->
                    calls += key
                    when (key.thread.value) {
                        1L -> thread(key, false)
                        2L -> thread(key, true)
                        3L -> throw ProviderException.NotFound("gone")
                        else -> throw ProviderException.Network("offline")
                    }
                }, { 100L })
            assertEquals(3, refresh.refreshAll().checked)
            assertEquals(keys, calls)
            coVerify { repository.updateAvailability(keys[0], ThreadAvailability.LIVE, 100) }
            coVerify { repository.updateAvailability(keys[1], ThreadAvailability.ARCHIVED, 100) }
            coVerify { repository.updateAvailability(keys[2], ThreadAvailability.DELETED, 100) }
            coVerify(exactly = 0) { repository.updateAvailability(keys[3], any(), any()) }
            coVerify(exactly = 0) { repository.markRead(any()) }
            refresh.refreshAll()
            assertEquals(4, calls.size)
        }

    @Test
    fun unreadIncludesLoadedUnseenAndUnloadedRepliesWithoutDoubleCounting() {
        val key = key(1)
        val bookmark = Bookmark(key, "Thread", createdAtMillis = 0, latestReplyCount = 5)
        val memory = ThreadMemory(key, knownPostIds = setOf(1, 2, 3, 4), seenPostIds = setOf(1, 2, 4))
        assertEquals(3, bookmark.collectionUnread(memory))
        assertEquals(2, bookmark.collectionUnread(memory.copy(seenPostIds = memory.knownPostIds)))
        assertEquals(5, bookmark.collectionUnread(null))
        assertEquals(
            0,
            bookmark.copy(lastSeenReplyCount = 5).collectionUnread(memory.copy(seenPostIds = memory.knownPostIds)),
        )
        assertEquals(1, memory.collectionUnread())
        assertEquals(
            0,
            bookmark.collectionUnread(memory.copy(seenPostIds = memory.knownPostIds, acknowledgedReplyCount = 5)),
        )
    }
}
