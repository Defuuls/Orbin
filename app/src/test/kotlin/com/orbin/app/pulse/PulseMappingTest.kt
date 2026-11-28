package com.orbin.app.pulse

import com.google.common.truth.Truth.assertThat
import com.orbin.core.model.Board
import com.orbin.core.model.BoardId
import com.orbin.core.model.Bookmark
import com.orbin.core.model.CatalogThread
import com.orbin.core.model.MediaAttachment
import com.orbin.core.model.MediaType
import com.orbin.core.model.Post
import com.orbin.core.model.PostComment
import com.orbin.core.model.PostId
import com.orbin.core.model.PosterInfo
import com.orbin.core.model.ProviderId
import com.orbin.core.model.Thread
import com.orbin.core.model.ThreadId
import com.orbin.core.model.ThreadKey
import com.orbin.core.model.ThreadStats
import com.orbin.feature.home.SubscribedBoardFeed
import com.orbin.uipulse.PulsePart
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import org.junit.Test

class PulseMappingTest {
    private val now = 10_000_000_000L
    private val hour = 3_600_000L
    private val words = PulseThreadWords(anonymous = "Anon", media = { "$it media" })

    private fun post(
        id: Long,
        board: String = "g",
        comment: String = "",
        subject: String? = null,
        name: String? = null,
        createdAt: Long = now - hour,
        attachments: Int = 0,
        backlinks: Int = 0,
        original: Boolean = false,
    ) = Post(
        id = PostId(id),
        board = BoardId(board),
        threadId = ThreadId(1L),
        isOriginalPost = original,
        subject = subject,
        comment = PostComment(raw = comment, nodes = persistentListOf()),
        poster = PosterInfo(name = name),
        createdAtMillis = createdAt,
        attachments =
            List(attachments) {
                MediaAttachment(
                    id = "m$it",
                    originalFileName = "f$it",
                    extension = "jpg",
                    type = MediaType.IMAGE,
                    sourceUrl = "https://x.example/$it.jpg",
                    thumbnailUrl = "https://x.example/${it}s.jpg",
                )
            }.toImmutableList(),
        backlinks = List(backlinks) { PostId(100L + it) }.toImmutableList(),
    )

    private fun catalog(
        provider: String = "p",
        board: String = "g",
        no: Long,
        subject: String? = null,
        comment: String = "",
        lastModified: Long = now - hour,
        replies: Int = 0,
    ) = CatalogThread(
        key = ThreadKey(ProviderId(provider), BoardId(board), ThreadId(no)),
        originalPost = post(no, board, comment, subject, original = true),
        stats = ThreadStats(replyCount = replies, lastModifiedMillis = lastModified),
    )

    private fun feed(
        board: String,
        title: String,
        vararg threads: CatalogThread,
    ) = SubscribedBoardFeed(
        board = Board(id = BoardId(board), title = title),
        threads = threads.toList().toImmutableList(),
        threadLimitOverride = null,
    )

    @Test
    fun threadsFromEveryBoardAreMergedMostRecentlyActiveFirst() {
        val lines =
            feedLines(
                listOf(
                    feed("g", "Garden", catalog(no = 1, subject = "old", lastModified = now - 5 * hour)),
                    feed("trn", "Trains", catalog(board = "trn", no = 2, subject = "new", lastModified = now - hour)),
                ),
                now,
            )
        assertThat(lines.map { it.title }).containsExactly("new", "old").inOrder()
    }

    @Test
    fun aLineCarriesAgeBoardAndReplyCount() {
        val line =
            feedLines(listOf(feed("g", "Garden", catalog(no = 7, subject = "Seeds", replies = 42))), now).single()
        assertThat(line.id).isEqualTo("t:p|g|7")
        assertThat(line.age).isEqualTo("1h")
        assertThat(line.sub).isEqualTo("/g/ · Garden")
        assertThat(line.trailing).isEqualTo("42")
        assertThat(line.unread).isFalse()
    }

    @Test
    fun theTitleFallsBackFromSubjectToFirstLineToNumber() {
        val lines =
            feedLines(
                listOf(
                    feed(
                        "g",
                        "",
                        catalog(no = 1, subject = "Subject", lastModified = now - 1 * hour),
                        catalog(no = 2, comment = "\nfirst line\nsecond", lastModified = now - 2 * hour),
                        catalog(no = 3, lastModified = now - 3 * hour),
                    ),
                ),
                now,
            )
        assertThat(lines.map { it.title }).containsExactly("Subject", "first line", "No.3").inOrder()
        assertThat(lines.map { it.sub }.toSet()).containsExactly("/g/")
    }

    @Test
    fun aThreadListedTwiceKeepsOneLine() {
        val thread = catalog(no = 5, subject = "same")
        val lines = feedLines(listOf(feed("g", "Garden", thread), feed("g", "Garden", thread)), now)
        assertThat(lines).hasSize(1)
    }

    @Test
    fun aThreadThatCannotBeAddressedHasNoLine() {
        val lines =
            feedLines(
                listOf(
                    feed("g", "Garden", catalog(provider = "a|b", no = 1), catalog(no = 2, subject = "ok")),
                ),
                now,
            )
        assertThat(lines.map { it.title }).containsExactly("ok")
    }

    @Test
    fun anEmptyFeedHasNoLines() {
        assertThat(feedLines(emptyList(), now)).isEmpty()
        assertThat(feedLines(listOf(feed("g", "Garden")), now)).isEmpty()
    }

    @Test
    fun hostileTextStaysTextAndIsBounded() {
        val hostile = "<img src=x onerror=alert(1)>" + "y".repeat(500)
        val line = feedLines(listOf(feed("g", "<b>Garden</b>", catalog(no = 1, subject = hostile))), now).single()
        assertThat(line.title.length).isAtMost(140)
        assertThat(line.title).startsWith("<img")
        assertThat(line.sub).isEqualTo("/g/ · <b>Garden</b>")
    }

    private fun thread(vararg replies: Post) =
        Thread(
            key = ThreadKey(ProviderId("p"), BoardId("g"), ThreadId(1L)),
            originalPost = post(1, comment = "opening", original = true, name = "OP", attachments = 2),
            replies = replies.toList().toImmutableList(),
        )

    @Test
    fun aThreadReadsOpeningPostFirstThenRepliesInOrder() {
        val lines = threadLines(thread(post(2, comment = "one"), post(3, comment = "two")), now, words)
        assertThat(lines.map { it.id }).containsExactly("p:1", "p:2", "p:3").inOrder()
        assertThat(lines.map { it.title }).containsExactly("opening", "one", "two").inOrder()
    }

    @Test
    fun aPostLineShowsNumberPosterMediaAndBacklinks() {
        val lines = threadLines(thread(post(2, comment = "reply", backlinks = 3)), now, words)
        assertThat(lines[0].sub).isEqualTo("No.1 · OP · 2 media")
        assertThat(lines[0].trailing).isEmpty()
        assertThat(lines[1].sub).isEqualTo("No.2 · Anon")
        assertThat(lines[1].trailing).isEqualTo("+3")
        assertThat(lines[1].age).isEqualTo("1h")
    }

    @Test
    fun aBlankPosterNameReadsAsAnonymous() {
        val lines = threadLines(thread(post(2, comment = "x", name = "  ")), now, words)
        assertThat(lines[1].sub).isEqualTo("No.2 · Anon")
    }

    @Test
    fun aPostWithoutTextShowsItsSubjectThenItsNumber() {
        val lines = threadLines(thread(post(2, subject = "just a subject"), post(3)), now, words)
        assertThat(lines[1].title).isEqualTo("just a subject")
        assertThat(lines[2].title).isEqualTo("No.3")
    }

    @Test
    fun aPostNumberSeenTwiceKeepsItsFirstLine() {
        val lines = threadLines(thread(post(2, comment = "first"), post(2, comment = "again")), now, words)
        assertThat(lines.map { it.id }).containsExactly("p:1", "p:2").inOrder()
        assertThat(lines[1].title).isEqualTo("first")
    }

    @Test
    fun aLongPostIsClippedToOneBoundedLine() {
        val line = threadLines(thread(post(2, comment = "a\n\n" + "word ".repeat(400))), now, words)[1]
        assertThat(line.title.length).isAtMost(600)
        assertThat(line.title).doesNotContain("\n")
    }

    private fun watched(
        no: Long,
        board: String = "g",
        seen: Int = 0,
        latest: Int = 0,
        title: String = "t$no",
        isWatched: Boolean = true,
    ) = Bookmark(
        key = ThreadKey(ProviderId("p"), BoardId(board), ThreadId(no)),
        title = title,
        createdAtMillis = 1L,
        isWatched = isWatched,
        lastSeenReplyCount = seen,
        latestReplyCount = latest,
    )

    @Test
    fun aWatchedThreadWithNewRepliesComesFirstMarkedUnreadWithItsCount() {
        val boards =
            listOf(
                feed(
                    "g",
                    "Garden",
                    catalog(no = 1, subject = "newest", lastModified = now - hour),
                    catalog(no = 2, subject = "older", lastModified = now - 5 * hour),
                ),
            )
        val lines = feedLines(boards, now, listOf(watched(no = 2, seen = 3, latest = 10)))
        assertThat(lines.map { it.title }).containsExactly("older", "newest").inOrder()
        assertThat(lines[0].unread).isTrue()
        assertThat(lines[0].trailing).isEqualTo("+7")
        assertThat(lines[1].unread).isFalse()
    }

    @Test
    fun aWatchedThreadTheFeedDoesNotListStillGetsALine() {
        val boards = listOf(feed("g", "Garden", catalog(no = 1, subject = "listed")))
        val lines = feedLines(boards, now, listOf(watched(no = 9, board = "trn", seen = 1, latest = 2, title = "Away")))
        assertThat(lines.map { it.title }).containsExactly("Away", "listed").inOrder()
        assertThat(lines[0].id).isEqualTo("t:p|trn|9")
        assertThat(lines[0].sub).isEqualTo("/trn/")
        assertThat(lines[0].unread).isTrue()
        assertThat(lines[0].age).isEmpty()
    }

    @Test
    fun threadsThatAreReadOrNotWatchedAreNotMarked() {
        val boards = listOf(feed("g", "Garden", catalog(no = 1, subject = "a"), catalog(no = 2, subject = "b")))
        val lines =
            feedLines(
                boards,
                now,
                listOf(
                    watched(no = 1, seen = 5, latest = 5),
                    watched(no = 2, seen = 0, latest = 4, isWatched = false),
                ),
            )
        assertThat(lines.none { it.unread }).isTrue()
        assertThat(lines).hasSize(2)
    }

    @Test
    fun theOrderAmongUnreadAndAmongReadThreadsIsKept() {
        val boards =
            listOf(
                feed(
                    "g",
                    "Garden",
                    catalog(no = 1, subject = "one", lastModified = now - 1 * hour),
                    catalog(no = 2, subject = "two", lastModified = now - 2 * hour),
                    catalog(no = 3, subject = "three", lastModified = now - 3 * hour),
                    catalog(no = 4, subject = "four", lastModified = now - 4 * hour),
                ),
            )
        val lines = feedLines(boards, now, listOf(watched(no = 4, latest = 1), watched(no = 2, latest = 1)))
        assertThat(lines.map { it.title }).containsExactly("two", "four", "one", "three").inOrder()
    }

    private fun body(
        text: String,
        present: Set<Long> = emptySet(),
    ) = bodyOf(text, present)

    @Test
    fun aBodyKeepsLinesGreentextAndPresentReferences() {
        val lines = body("hello >>12 there\n>be me\n\nlast", present = setOf(12L))
        assertThat(lines).hasSize(4)
        assertThat(lines[0].parts)
            .containsExactly(PulsePart.Text("hello "), PulsePart.Ref(">>12", 12L), PulsePart.Text(" there"))
            .inOrder()
        assertThat(lines[1].quote).isTrue()
        assertThat(lines[1].parts).containsExactly(PulsePart.Text("be me"))
        assertThat(lines[2].parts).isEmpty()
        assertThat(lines[3].quote).isFalse()
    }

    @Test
    fun aReferenceToAPostThatIsNotThereStaysPlainText() {
        val lines = body(">>99 gone", present = setOf(1L))
        assertThat(lines.single().parts).containsExactly(PulsePart.Text(">>99"), PulsePart.Text(" gone")).inOrder()
    }

    @Test
    fun aLinkIsShownAsPlainTextNeverALink() {
        val lines = body("see https://example.com/a. ok")
        val parts = lines.single().parts
        assertThat(parts.none { it is PulsePart.Ref }).isTrue()
        val shown = parts.filterIsInstance<PulsePart.Text>().map { it.value }
        assertThat(shown.joinToString("")).isEqualTo("see https://example.com/a. ok")
    }

    @Test
    fun anEmptyOrBlankBodyHasNoLines() {
        assertThat(body("")).isEmpty()
        assertThat(body("   ")).isEmpty()
    }

    @Test
    fun aHugePostIsBoundedAndMarked() {
        val huge = List(500) { "line $it with some words in it" }.joinToString("\n")
        val lines = body(huge)
        assertThat(lines.size).isAtMost(41)
        val last = lines.last().parts.single() as PulsePart.Text
        assertThat(last.value).isEqualTo("…")
    }

    @Test
    fun hostileBodyTextStaysPlainText() {
        val lines = body("<img src=x onerror=alert(1)> [spoiler]x[/spoiler]")
        assertThat(lines.single().parts.all { it is PulsePart.Text }).isTrue()
    }

    @Test
    fun aPostLineCarriesItsNumberAndBody() {
        val thread =
            Thread(
                key = ThreadKey(ProviderId("p"), BoardId("g"), ThreadId(1L)),
                originalPost = post(1, comment = "opening", original = true),
                replies = listOf(post(2, comment = ">>1 yes\n>quote")).toImmutableList(),
            )
        val lines = threadLines(thread, now, words)
        assertThat(lines.map { it.number }).containsExactly(1L, 2L).inOrder()
        assertThat(lines[1].body[0].parts[0]).isEqualTo(PulsePart.Ref(">>1", 1L))
        assertThat(lines[1].body[1].quote).isTrue()
    }
}
