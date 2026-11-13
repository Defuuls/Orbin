package com.orbin.data.archive

import com.orbin.core.model.BoardId
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
import kotlinx.collections.immutable.persistentListOf
import okio.Buffer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ThreadArchiveTest {
    private val key = ThreadKey(ProviderId("4chan"), BoardId("g"), ThreadId(100))
    private val image =
        MediaAttachment(
            id = "a1",
            originalFileName = "cat",
            extension = ".jpg",
            type = MediaType.IMAGE,
            sourceUrl = "https://i.example/a1.jpg",
            thumbnailUrl = "https://i.example/a1s.jpg",
        )
    private val thread =
        Thread(
            key = key,
            originalPost =
                Post(
                    id = PostId(100),
                    board = key.board,
                    threadId = key.thread,
                    isOriginalPost = true,
                    subject = "<Cats> & dogs",
                    comment =
                        PostComment(
                            "",
                            persistentListOf(
                                PostNode.Styled(InlineStyle.GREENTEXT, persistentListOf(PostNode.Text(">be me"))),
                                PostNode.LineBreak,
                                PostNode.Link("javascript:alert(1)", persistentListOf(PostNode.Text("click"))),
                            ),
                        ),
                    attachments = persistentListOf(image),
                    backlinks = persistentListOf(PostId(101)),
                ),
            replies =
                persistentListOf(
                    Post(
                        id = PostId(101),
                        board = key.board,
                        threadId = key.thread,
                        isOriginalPost = false,
                        comment = PostComment("", persistentListOf(PostNode.QuoteLink(PostId(100), isOp = true))),
                    ),
                ),
        )

    @Test
    fun thePageEscapesTextAndLinksQuotesAndSavedFiles() {
        val page = threadPage(thread, mapOf("a1" to SavedFiles("media/a1.jpg", "thumbs/a1.jpg")), null, 0L)
        assertTrue("&lt;Cats&gt; &amp; dogs" in page)
        assertTrue("<span class=\"green\">&gt;be me</span>" in page)
        assertTrue("<a class=\"quote\" href=\"#p100\">&gt;&gt;100 (OP)</a>" in page)
        assertTrue("href=\"media/a1.jpg\"" in page && "src=\"thumbs/a1.jpg\"" in page)
        assertTrue("<a href=\"#p101\">&gt;&gt;101</a>" in page)
        assertTrue("<link rel=\"stylesheet\" href=\"style.css\">" in page)
        assertFalse("javascript:" in page)
    }

    @Test
    fun aFileThatWasntSavedLinksToTheSite() {
        val page = threadPage(thread, emptyMap(), null, 0L)
        assertTrue("href=\"https://i.example/a1.jpg\"" in page)
        assertTrue("not saved" in page)
    }

    @Test
    fun spoilersStartClosedAndKeepStyledTextAndQuoteLinksInsideTheirDisclosure() {
        val spoiler =
            PostNode.Styled(
                InlineStyle.SPOILER,
                persistentListOf(
                    PostNode.Styled(InlineStyle.GREENTEXT, persistentListOf(PostNode.Text(">hidden & styled"))),
                    PostNode.QuoteLink(PostId(101)),
                    PostNode.Link("https://example.test/?a=1&b=2", persistentListOf(PostNode.Text("hidden link"))),
                ),
            )
        val archived =
            thread.copy(
                originalPost = thread.originalPost.copy(comment = PostComment("", persistentListOf(spoiler))),
            )
        val page = threadPage(archived, emptyMap(), null, 0L)
        val disclosure = page.substringAfter("<details class=\"spoiler\">").substringBefore("</details>")

        // Native details starts closed and gives its summary keyboard and touch activation without script.
        assertTrue("<details class=\"spoiler\">" in page)
        assertFalse("<details class=\"spoiler\" open" in page)
        assertTrue(disclosure.startsWith("<summary>Spoiler</summary>"))
        assertTrue("<span class=\"green\">&gt;hidden &amp; styled</span>" in disclosure)
        assertTrue("<a class=\"quote\" href=\"#p101\">&gt;&gt;101</a>" in disclosure)
        assertTrue("href=\"https://example.test/?a=1&amp;b=2\"" in disclosure)
        assertTrue("hidden link</a>" in disclosure)
        assertFalse("<script" in page)
    }

    @Test
    fun nestedSpoilersHaveIndependentClosedDisclosures() {
        val nested =
            PostNode.Styled(
                InlineStyle.SPOILER,
                persistentListOf(
                    PostNode.Text("outer "),
                    PostNode.Styled(
                        InlineStyle.SPOILER,
                        persistentListOf(
                            PostNode.Styled(InlineStyle.QUOTE_TEXT, persistentListOf(PostNode.Text("inner"))),
                        ),
                    ),
                ),
            )
        val archived =
            thread.copy(
                originalPost = thread.originalPost.copy(comment = PostComment("", persistentListOf(nested))),
            )
        val page = threadPage(archived, emptyMap(), null, 0L)

        assertEquals(2, Regex("<details class=\"spoiler\">").findAll(page).count())
        assertEquals(2, Regex("<summary>Spoiler</summary>").findAll(page).count())
        assertTrue("<span class=\"pink\">inner</span></span></details></span></details>" in page)
        assertFalse(" open" in page.substringAfter("<blockquote>").substringBefore("</blockquote>"))
    }

    @Test
    fun namesCannotLeaveTheirFolder() {
        assertEquals("____etc", safeName("../.etc"))
        assertEquals(".webm", extensionOf(".webm", ""))
        assertEquals(".png", extensionOf("", "https://x/y/z.PNG?x=1"))
        assertEquals("", extensionOf("", "https://x/y/z"))
        assertEquals("orbin-g-100", archiveRoot(thread))
    }

    @Test
    fun activeContentIsSavedInertAndOfferedAsADownload() {
        assertEquals(".bin", extensionOf("svg", ""))
        assertEquals(".bin", extensionOf(".html", ""))
        assertEquals(".bin", extensionOf("", "https://x/a.xhtml"))
        assertEquals(".bin", extensionOf("", "https://x/a.SVG?x=1"))
        assertEquals(".jpeg", extensionOf("JPEG", ""))
        val page = threadPage(thread, mapOf("a1" to SavedFiles("media/a1.bin", null)), null, 0L)
        assertTrue("<a href=\"media/a1.bin\" download><img" in page)
        val normal = threadPage(thread, mapOf("a1" to SavedFiles("media/a1.jpg", null)), null, 0L)
        assertFalse(" download>" in normal)
    }

    @Test
    fun theZipHasEveryEntryAndAValidEnd() {
        assertEquals(0xCBF43926.toInt(), crc32("123456789".encodeToByteArray()))
        val out = Buffer()
        ZipWriter(out).apply {
            add("a/index.html", "hi".encodeToByteArray())
            add("a/style.css", "x".encodeToByteArray())
            finish()
        }
        val bytes = out.readByteArray()
        val end = bytes.size - END_RECORD_SIZE
        assertEquals(0x50, bytes[0].toInt())
        assertEquals(0x4b, bytes[1].toInt())
        assertEquals(0x06054b50, Buffer().write(bytes, end, 4).readIntLe())
        assertEquals(2, Buffer().write(bytes, end + 10, 2).readShortLe().toInt())
    }

    private companion object {
        const val END_RECORD_SIZE = 22
    }
}
