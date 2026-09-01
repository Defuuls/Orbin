package com.orbin.feature.home

import com.google.common.truth.Truth.assertThat
import com.orbin.core.model.Board
import com.orbin.core.model.BoardId
import com.orbin.core.model.CatalogThread
import com.orbin.core.model.Post
import com.orbin.core.model.PostId
import com.orbin.core.model.ProviderId
import com.orbin.core.model.ThreadId
import com.orbin.core.model.ThreadKey
import com.orbin.core.model.ThreadStats
import kotlinx.collections.immutable.persistentListOf
import org.junit.Test

class MutedFeedMappingTest {
    @Test
    fun `muted tag keeps a thread but marks its row collapsed`() {
        val board = Board(id = BoardId("g"), title = "Technology")
        val thread = thread(subject = "Weekly desktop discussion")
        val feeds = listOf(SubscribedBoardFeed(board, persistentListOf(thread), threadLimitOverride = null))

        val entries = feedEntries(feeds, emptySet(), nowMillis = 1L, mutedTokens = setOf("desktop"))

        assertThat(entries).hasSize(1)
        assertThat(entries.single().row.muted).isTrue()
    }

    @Test
    fun `unmatched muted tag leaves the row expanded`() {
        val board = Board(id = BoardId("g"), title = "Technology")
        val thread = thread(subject = "Weekly desktop discussion")
        val feeds = listOf(SubscribedBoardFeed(board, persistentListOf(thread), threadLimitOverride = null))

        val entries = feedEntries(feeds, emptySet(), nowMillis = 1L, mutedTokens = setOf("cooking"))

        assertThat(entries.single().row.muted).isFalse()
    }

    private fun thread(subject: String): CatalogThread {
        val board = BoardId("g")
        val thread = ThreadId(1)
        return CatalogThread(
            key = ThreadKey(ProviderId("fourchan"), board, thread),
            originalPost =
                Post(
                    id = PostId(1),
                    board = board,
                    threadId = thread,
                    isOriginalPost = true,
                    subject = subject,
                ),
            stats = ThreadStats(),
            previewReplies = persistentListOf(),
        )
    }
}
