package com.orbin.core.reader

import com.orbin.core.model.BoardId
import com.orbin.core.model.CatalogThread
import com.orbin.core.model.Post
import com.orbin.core.model.PostComment
import com.orbin.core.model.PostId
import com.orbin.core.model.PostNode
import com.orbin.core.model.ProviderId
import com.orbin.core.model.ThreadId
import com.orbin.core.model.ThreadKey
import com.orbin.core.model.ThreadStats
import kotlinx.collections.immutable.persistentListOf
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FeedRowsTest {
    @Test
    fun feedRowNamesItsSourceWrapsTheBoardOnceAndKeysBySource() {
        val thread = thread(board = "/g/")
        val row = thread.feedRow(provider = "fourchan", nowMillis = 121_000, read = true)

        assertEquals("fourchan/g/42", row.id)
        assertEquals("4chan", row.site)
        assertEquals("/g/", row.board)
        assertEquals("42", row.threadNumber)
        assertTrue(row.read)
        assertEquals(0, row.unread)
        // Everything else is the shared catalog row.
        val catalog = thread.catalogRow(121_000)
        assertEquals(catalog.subject, row.subject)
        assertEquals(catalog.activity, row.activity)
        assertEquals(catalog.replies, row.replies)
    }

    @Test
    fun theSameThreadFromTwoSourcesGetsTwoKeys() {
        val thread = thread(board = "g")
        assertEquals("site/g/42", thread.feedRow("site", 0).id)
        assertEquals("other/g/42", thread.feedRow("other", 0).id)
        assertFalse(thread.feedRow("site", 0).read)
    }

    @Test
    fun providerLabelsNameKnownSourcesAndPassOthersThrough() {
        assertEquals("4chan", providerLabel("fourchan"))
        assertEquals("BBWChan", providerLabel("bbwchan"))
        assertEquals("example", providerLabel("example"))
        assertEquals("example/b/7", threadRowId("example", "b", 7))
    }

    private fun thread(board: String): CatalogThread {
        val key = ThreadKey(ProviderId("site"), BoardId(board.trim('/')), ThreadId(42))
        return CatalogThread(
            key,
            Post(
                PostId(42),
                key.board,
                key.thread,
                isOriginalPost = true,
                createdAtMillis = 1_000,
                comment = PostComment("", persistentListOf(PostNode.Text("Hello"))),
            ),
            ThreadStats(replyCount = 3),
        )
    }
}
