package com.orbin.ios

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import com.orbin.core.model.Board
import com.orbin.core.model.BoardId
import com.orbin.core.model.CatalogThread
import com.orbin.core.model.InlineStyle
import com.orbin.core.model.MediaAttachment
import com.orbin.core.model.MediaType
import com.orbin.core.model.Post
import com.orbin.core.model.PostComment
import com.orbin.core.model.PostId
import com.orbin.core.model.PostNode
import com.orbin.core.model.ProviderId
import com.orbin.core.model.Thread
import com.orbin.core.model.ThreadId
import com.orbin.core.model.ThreadKey
import com.orbin.core.model.ThreadStats
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RowsTest {
    @Test
    fun commentsReadAsPlainTextWithQuotesAndLineBreaks() {
        val comment =
            PostComment(
                raw = "",
                nodes =
                    persistentListOf(
                        PostNode.QuoteLink(PostId(12)),
                        PostNode.LineBreak,
                        PostNode.Styled(InlineStyle.GREENTEXT, persistentListOf(PostNode.Text(">be me"))),
                        PostNode.LineBreak,
                        PostNode.QuoteLink(PostId(34), board = BoardId("g")),
                        PostNode.Text(" see "),
                        PostNode.Link("https://example.com", persistentListOf()),
                    ),
            )

        assertEquals(">>12\n>be me\n>>>/g/34 see https://example.com", comment.plainText())
    }

    @Test
    fun theSameBoardOnTwoSitesGetsTwoTiles() {
        val random = Board(BoardId("b"), "Random")
        val one = SiteBoard(ProviderId("one"), "One", random).toTile()
        val two = SiteBoard(ProviderId("two"), "Two", random).toTile()

        assertNotEquals(one.id, two.id)
        assertEquals("/b/", one.path)
        assertEquals("Random · One", one.title)
    }

    @Test
    fun filesAreIndexedAcrossTheThreadInReadingOrder() {
        val thread =
            Thread(
                key = ThreadKey(ProviderId("p"), BoardId("g"), ThreadId(1)),
                originalPost = post(1, files = 2),
                replies = persistentListOf(post(2, files = 0), post(3, files = 1)),
            )

        assertEquals(listOf("1-0", "1-1", "3-0"), thread.files.map { it.id })
        assertEquals(0, thread.firstFileIndex("1"))
        assertEquals(2, thread.firstFileIndex("3"))
        assertNull(thread.firstFileIndex("2"), "a post without files")
        assertNull(thread.firstFileIndex("9"), "a post not in the thread")
    }

    @Test
    fun onlyHttpsLinksWithAHostMayBeOpened() {
        assertEquals("https://example.com/a", safeExternalLink(" https://example.com/a "))
        assertEquals("HTTPS://example.com", safeExternalLink("HTTPS://example.com"))
        listOf(
            "http://example.com",
            "https://",
            "https:///path",
            "javascript:alert(1)",
            "data:text/html,hi",
            "file:///etc/passwd",
            "intent://x#Intent;end",
            "example.com",
            "",
        ).forEach { assertNull(safeExternalLink(it), it) }
    }

    @Test
    fun aZoomedImageCannotBeDraggedPastItsEdges() {
        val size = IntSize(100, 200)

        assertEquals(Offset.Zero, zoomedOffset(Offset(40f, 40f), 1f, size), "not zoomed: centred")
        assertEquals(Offset(50f, -100f), zoomedOffset(Offset(80f, -300f), 2f, size), "clamped to the overflow")
        assertEquals(Offset(10f, 20f), zoomedOffset(Offset(10f, 20f), 2f, size), "within bounds: kept")
    }

    @Test
    fun bookmarksAndVisitsStoreWhatAndroidStores() {
        val thread =
            Thread(
                key = ThreadKey(ProviderId("p"), BoardId("g"), ThreadId(1)),
                originalPost = post(1, files = 1),
                stats = ThreadStats(replyCount = 12),
            )

        val bookmark = thread.toBookmark(nowMillis = 5L)
        assertEquals("/g/", bookmark.title, "no subject: the board")
        assertEquals("https://i.example/1-0s.jpg", bookmark.thumbnailUrl)
        assertEquals(12, bookmark.lastSeenReplyCount)
        assertEquals(0, bookmark.unreadCount, "nothing unread at the moment it is bookmarked")
        assertEquals(5L, bookmark.createdAtMillis)
        assertTrue(bookmark.isWatched, "watched, so the board and watch list show it as on Android")

        val visit = thread.toHistoryEntry(nowMillis = 7L)
        assertEquals(PostId(1), visit.lastReadPostId)
        assertEquals(7L, visit.lastVisitedMillis)
    }

    @Test
    fun feedRowsNameTheBoardLikeAndroidAndKeepSitesApart() {
        val thread =
            CatalogThread(
                key = ThreadKey(ProviderId("site"), BoardId("g"), ThreadId(42)),
                originalPost = post(42, files = 0),
                stats = ThreadStats(),
            )

        val row = FeedThread(ProviderId("site"), thread).toFeedRow(nowMillis = 0L, read = true)

        assertEquals("/g/", row.board)
        assertEquals("site/g/42", row.id, "another site's /g/42 is a different row")
        assertEquals("42", row.threadNumber)
        assertTrue(row.read)
    }

    private fun post(
        id: Long,
        files: Int,
    ): Post =
        Post(
            id = PostId(id),
            board = BoardId("g"),
            threadId = ThreadId(1),
            isOriginalPost = id == 1L,
            attachments =
                List(files) { index ->
                    MediaAttachment(
                        id = "$id-$index",
                        originalFileName = "f",
                        extension = "jpg",
                        type = MediaType.IMAGE,
                        sourceUrl = "https://i.example/$id-$index.jpg",
                        thumbnailUrl = "https://i.example/$id-${index}s.jpg",
                    )
                }.toPersistentList(),
        )
}
