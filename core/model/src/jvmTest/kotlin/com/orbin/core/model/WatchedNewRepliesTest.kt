package com.orbin.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class WatchedNewRepliesTest {
    @Test
    fun countsOnlyNewRepliesOnWatchedThreads() {
        val bookmarks =
            listOf(
                bookmark(1, watched = true, seen = 10, latest = 14),
                bookmark(2, watched = true, seen = 5, latest = 6),
                bookmark(3, watched = false, seen = 0, latest = 50),
                bookmark(4, watched = true, seen = 9, latest = 3),
            )
        assertThat(bookmarks.watchedNewReplies()).isEqualTo(5)
    }

    private fun bookmark(
        thread: Long,
        watched: Boolean,
        seen: Int,
        latest: Int,
    ) = Bookmark(
        key = ThreadKey(ProviderId("p"), BoardId("b"), ThreadId(thread)),
        title = "t",
        createdAtMillis = 0,
        isWatched = watched,
        lastSeenReplyCount = seen,
        latestReplyCount = latest,
    )
}
