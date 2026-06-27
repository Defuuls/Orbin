package com.orbin.data.database

import com.google.common.truth.Truth.assertThat
import com.orbin.core.model.BoardId
import com.orbin.core.model.Bookmark
import com.orbin.core.model.HistoryEntry
import com.orbin.core.model.PostId
import com.orbin.core.model.ProviderId
import com.orbin.core.model.ThreadId
import com.orbin.core.model.ThreadKey
import org.junit.Test

class EntityMappersTest {
    private val key = ThreadKey(ProviderId("fourchan"), BoardId("g"), ThreadId(123))

    @Test
    fun `bookmark round-trips through entity`() {
        val bookmark =
            Bookmark(
                key = key,
                title = "Tech thread",
                thumbnailUrl = "https://example/t.jpg",
                createdAtMillis = 1000,
                isWatched = true,
                lastSeenReplyCount = 5,
                latestReplyCount = 9,
                isThreadDead = false,
            )
        assertThat(bookmark.toEntity().toDomain()).isEqualTo(bookmark)
    }

    @Test
    fun `bookmark unread count derives from counts`() {
        val entity =
            Bookmark(
                key = key,
                title = "t",
                createdAtMillis = 0,
                lastSeenReplyCount = 3,
                latestReplyCount = 10,
            ).toEntity()
        val restored = entity.toDomain()
        assertThat(restored.unreadCount).isEqualTo(7)
        assertThat(restored.hasUnread).isTrue()
    }

    @Test
    fun `history round-trips through entity including last read`() {
        val entry =
            HistoryEntry(
                key = key,
                title = "Tech thread",
                thumbnailUrl = null,
                lastVisitedMillis = 42,
                lastReadPostId = PostId(456),
            )
        assertThat(entry.toEntity().toDomain()).isEqualTo(entry)
    }
}
