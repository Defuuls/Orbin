package com.orbin.app.pulse

import com.google.common.truth.Truth.assertThat
import com.orbin.core.model.Board
import com.orbin.core.model.BoardId
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
}
