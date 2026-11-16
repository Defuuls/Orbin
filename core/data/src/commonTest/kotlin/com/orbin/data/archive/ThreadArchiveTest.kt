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
        assertTrue("href=\"https://i.example/a1.jpg\" rel=\"noopener noreferrer\"" in page)
        assertTrue("not saved" in page)
    }

    /** Opening an archive must not tell the site, or anyone watching the network, that it was opened. */
    @Test
    fun openingThePageFetchesNothingFromTheSite() {
        val video =
            image.copy(
                id = "v1",
                type = MediaType.VIDEO,
                extension = ".webm",
                sourceUrl = "https://i.example/v1.webm",
            )
        val audio =
            image.copy(
                id = "s1",
                type = MediaType.AUDIO,
                extension = ".mp3",
                sourceUrl = "https://i.example/s1.mp3",
            )
        val unsaved =
            thread.copy(
                originalPost = thread.originalPost.copy(attachments = persistentListOf(image, video, audio)),
            )

        val page = threadPage(unsaved, emptyMap(), "https://boards.example/g/thread/100", 0L)

        // Every attribute a browser loads without being asked: none of them may leave the archive.
        val autoLoaded = Regex("""(?:src|poster|srcset)="([^"]*)"""").findAll(page).map { it.groupValues[1] }.toList()
        assertEquals(emptyList(), autoLoaded)
        assertFalse("<img" in page || "<video" in page || "<audio" in page)
        // The files are still reachable, by a link the reader chooses to follow.
        listOf("a1.jpg", "v1.webm", "s1.mp3").forEach { assertTrue("href=\"https://i.example/$it\"" in page) }
        assertEquals(3, Regex("not saved").findAll(page).count())
    }

    @Test
    fun aSavedThumbnailIsShownEvenWhenTheFullFileIsNot() {
        val page = threadPage(thread, mapOf("a1" to SavedFiles(null, "thumbs/a1.jpg")), null, 0L)

        val link = "<a href=\"https://i.example/a1.jpg\" rel=\"noopener noreferrer\">"
        assertTrue("$link<img loading=\"lazy\" src=\"thumbs/a1.jpg\"" in page)
        assertTrue("not saved" in page)
    }

    @Test
    fun aSavedVideoPlaysFromTheArchiveAndOnlyShowsAPosterItHas() {
        val video = image.copy(type = MediaType.VIDEO, extension = ".webm")
        val archived = thread.copy(originalPost = thread.originalPost.copy(attachments = persistentListOf(video)))

        val withPoster = threadPage(archived, mapOf("a1" to SavedFiles("media/a1.webm", "thumbs/a1.jpg")), null, 0L)
        val withoutPoster = threadPage(archived, mapOf("a1" to SavedFiles("media/a1.webm", null)), null, 0L)

        assertTrue("<video controls preload=\"metadata\" poster=\"thumbs/a1.jpg\" src=\"media/a1.webm\">" in withPoster)
        assertTrue("<video controls preload=\"metadata\" src=\"media/a1.webm\">" in withoutPoster)
        assertFalse("https://i.example" in withoutPoster)
    }

    @Test
    fun theBrowserIsToldToLoadOnlyTheArchivesOwnFiles() {
        val page = threadPage(thread, emptyMap(), null, 0L)
        val head = page.substringBefore("</head>")

        assertTrue("<meta http-equiv=\"Content-Security-Policy\" content=\"$ARCHIVE_CONTENT_POLICY\">" in head)
        assertTrue("<meta name=\"referrer\" content=\"no-referrer\">" in head)
        // The policy precedes the stylesheet it has to allow.
        assertTrue(head.indexOf("Content-Security-Policy") < head.indexOf("<link rel=\"stylesheet\""))
        assertTrue(ARCHIVE_CONTENT_POLICY.startsWith("default-src 'none';"))
        listOf("script-src", "http:", "https:", "*", "'unsafe-inline'", "'unsafe-eval'").forEach {
            assertFalse(it in ARCHIVE_CONTENT_POLICY, "$it must not be allowed")
        }
        // Inline styles would be blocked, so the page must not rely on any.
        assertFalse(" style=\"" in page || "<style" in page)
    }

    @Test
    fun archiveStylesFollowReaderSurfacesAndRoundedCards() {
        val light = ARCHIVE_CSS.substringBefore("@media")
        val dark = ARCHIVE_CSS.substringAfter("@media (prefers-color-scheme: dark)").substringBefore("* {")

        assertTrue("--bg: #f4f5f7; --raised: #ffffff;" in light)
        assertTrue("--ink: #14161f; --muted: #696f83;" in light)
        assertTrue("--accent: #c2410c;" in light)
        assertTrue("--bg: #0b0d11; --raised: #141520;" in dark)
        assertTrue("--ink: #e8eaf0; --muted: #8a8fa8;" in dark)
        assertTrue("--accent: #fb923c;" in dark)
        assertTrue("max-width: 720px;" in ARCHIVE_CSS)
        assertTrue("border-radius: 22px;" in ARCHIVE_CSS)
        assertFalse("#0b1021" in ARCHIVE_CSS || "#141b33" in ARCHIVE_CSS)
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
