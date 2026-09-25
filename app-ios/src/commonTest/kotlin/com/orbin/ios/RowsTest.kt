package com.orbin.ios

import com.orbin.core.model.Board
import com.orbin.core.model.BoardId
import com.orbin.core.model.InlineStyle
import com.orbin.core.model.PostComment
import com.orbin.core.model.PostId
import com.orbin.core.model.PostNode
import com.orbin.core.model.ProviderId
import kotlinx.collections.immutable.persistentListOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

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
    fun relativeTimesMatchAndroidsShortForms() {
        val now = 1_000_000_000_000L
        assertEquals("just now", relativeTime(now - 30_000, now))
        assertEquals("5m", relativeTime(now - 5 * 60_000, now))
        assertEquals("3h", relativeTime(now - 3 * 3_600_000, now))
        assertEquals("2d", relativeTime(now - 2 * 86_400_000L, now))
        assertEquals("4w", relativeTime(now - 28 * 86_400_000L, now))
        assertEquals("1y", relativeTime(now - 400 * 86_400_000L, now))
        assertEquals("", relativeTime(0, now), "unknown time")
        assertEquals("", relativeTime(now + 60_000, now), "clock skew")
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
}
