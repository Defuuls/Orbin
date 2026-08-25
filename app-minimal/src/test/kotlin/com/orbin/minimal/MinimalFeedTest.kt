package com.orbin.minimal

import com.google.common.truth.Truth.assertThat
import com.orbin.core.model.Board
import com.orbin.core.model.BoardId
import com.orbin.core.model.CatalogThread
import com.orbin.core.model.Post
import com.orbin.core.model.PostComment
import com.orbin.core.model.PostId
import com.orbin.core.model.ProviderId
import com.orbin.core.model.ThreadId
import com.orbin.core.model.ThreadKey
import com.orbin.core.model.ThreadStats
import com.orbin.feature.home.SubscribedBoardFeed
import kotlinx.collections.immutable.toPersistentList
import org.junit.Test

private const val PROVIDER = "fourchan"

/**
 * The flat feed is the one thing this app does that the full client does not, so it is the one
 * thing worth testing here: everything else is the shared layers, tested where they live.
 */
class MinimalFeedTest {
    private val tech = Board(BoardId("g"), "Technology")
    private val anime = Board(BoardId("a"), "Anime")

    @Test
    fun `merges every board into one stream, newest activity first`() {
        val feeds =
            listOf(
                SubscribedBoardFeed(tech, listOf(thread(tech, 1L, bumpedAt = 100)).toPersistentList(), null),
                SubscribedBoardFeed(anime, listOf(thread(anime, 2L, bumpedAt = 300)).toPersistentList(), null),
            )

        val flat = feeds.flattenToFeed()

        // A quiet board's freshly-bumped thread outranks a busy board's stale one — the merge is
        // by activity, not by which board the thread happens to belong to.
        assertThat(flat.map { it.id }).containsExactly("a/2", "g/1").inOrder()
    }

    @Test
    fun `threads bumped in the same millisecond keep a stable order`() {
        val feeds =
            listOf(
                SubscribedBoardFeed(
                    tech,
                    listOf(
                        thread(tech, 1L, bumpedAt = 100),
                        thread(tech, 2L, bumpedAt = 100),
                    ).toPersistentList(),
                    null,
                ),
            )

        // Without a tiebreak, sort stability is the only thing keeping these apart and a redraw
        // could swap two rows under a reader's finger.
        assertThat(feeds.flattenToFeed().map { it.id }).containsExactly("g/2", "g/1").inOrder()
    }

    @Test
    fun `a row falls back to the opening post when the thread has no subject`() {
        val untitled = thread(tech, 1L, bumpedAt = 0, subject = null, comment = "just some text")
        val feeds = listOf(SubscribedBoardFeed(tech, listOf(untitled).toPersistentList(), null))

        assertThat(feeds.flattenToFeed().single().title).isEqualTo("just some text")
    }

    @Test
    fun `a row with neither subject nor comment has no title of its own`() {
        val blank = thread(tech, 1L, bumpedAt = 0, subject = null, comment = "")
        val feeds = listOf(SubscribedBoardFeed(tech, listOf(blank).toPersistentList(), null))

        // Empty rather than a hard-coded "No subject": the stand-in is a string resource, so the
        // screen supplies it and it can be translated.
        assertThat(feeds.flattenToFeed().single().title).isEmpty()
    }

    @Test
    fun `an empty subscription list produces an empty feed rather than failing`() {
        assertThat(emptyList<SubscribedBoardFeed>().flattenToFeed()).isEmpty()
    }

    private fun thread(
        board: Board,
        id: Long,
        bumpedAt: Long,
        subject: String? = "Subject $id",
        comment: String = "",
    ) = CatalogThread(
        key = ThreadKey(ProviderId(PROVIDER), board.id, ThreadId(id)),
        originalPost =
            Post(
                id = PostId(id),
                board = board.id,
                threadId = ThreadId(id),
                isOriginalPost = true,
                subject = subject,
                comment = PostComment(raw = comment, nodes = kotlinx.collections.immutable.persistentListOf()),
            ),
        stats = ThreadStats(replyCount = 3, lastModifiedMillis = bumpedAt),
    )
}
