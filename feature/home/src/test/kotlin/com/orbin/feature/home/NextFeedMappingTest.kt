package com.orbin.feature.home

import com.google.common.truth.Truth.assertThat
import com.orbin.core.model.Board
import com.orbin.core.model.BoardId
import com.orbin.core.model.CatalogThread
import com.orbin.core.model.FeedSort
import com.orbin.core.model.MediaAttachment
import com.orbin.core.model.MediaType
import com.orbin.core.model.Post
import com.orbin.core.model.PostId
import com.orbin.core.model.ProviderId
import com.orbin.core.model.ThreadId
import com.orbin.core.model.ThreadKey
import com.orbin.core.model.ThreadStats
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList
import org.junit.Test

/**
 * The join between the subscribed-feed state and the redesigned feed's rows.
 *
 * Default order is board code A-Z so a quiet board is not buried under a busy one. Activity,
 * replies and the rest are explicit alternatives.
 */
class NextFeedMappingTest {
    private companion object {
        /** An arbitrary "now" far enough from the epoch that a minute before it is still positive. */
        const val NOW = 1_700_000_000_000L
    }

    @Test
    fun `defaults to board code A-Z then activity within that board`() {
        val entries =
            feedEntries(
                feeds =
                    listOf(
                        boardFeed(
                            "z",
                            thread(3, board = "z", bumped = 9_000L),
                        ),
                        boardFeed(
                            "a",
                            thread(1, board = "a", bumped = 1_000L),
                            thread(2, board = "a", bumped = 5_000L),
                        ),
                    ),
                visited = emptySet(),
                nowMillis = 10_000L,
            )

        assertThat(entries.map { it.key.thread.value }).containsExactly(2L, 1L, 3L).inOrder()
        assertThat(entries.map { it.key.board.value }).containsExactly("a", "a", "z").inOrder()
    }

    @Test
    fun `activity sort flattens boards by most recent bump`() {
        val entries =
            feedEntries(
                feeds =
                    listOf(
                        boardFeed(
                            "a",
                            thread(1, board = "a", bumped = 1_000L),
                            thread(2, board = "a", bumped = 9_000L),
                        ),
                        boardFeed("z", thread(3, board = "z", bumped = 5_000L)),
                    ),
                visited = emptySet(),
                nowMillis = 10_000L,
                sort = FeedSort.ACTIVITY,
            )

        assertThat(entries.map { it.key.thread.value }).containsExactly(2L, 3L, 1L).inOrder()
    }

    @Test
    fun `replies sort ranks the busiest thread first`() {
        val entries =
            feedEntries(
                feeds =
                    listOf(
                        boardFeed("a", thread(1, board = "a", replies = 2, bumped = 9_000L)),
                        boardFeed("z", thread(2, board = "z", replies = 40, bumped = 1_000L)),
                    ),
                visited = emptySet(),
                nowMillis = 10_000L,
                sort = FeedSort.REPLIES,
            )

        assertThat(entries.map { it.key.thread.value }).containsExactly(2L, 1L).inOrder()
    }

    @Test
    fun `a thread with no bump time falls back to when its opening post was made`() {
        val entries =
            feedEntries(
                feeds =
                    listOf(
                        boardFeed(
                            "a",
                            thread(1, bumped = 0L, created = 8_000L),
                            thread(2, bumped = 3_000L),
                        ),
                    ),
                visited = emptySet(),
                nowMillis = 10_000L,
                sort = FeedSort.ACTIVITY,
            )

        // Without the fallback the unbumped thread would sort as if it were from 1970.
        assertThat(entries.map { it.key.thread.value }).containsExactly(1L, 2L).inOrder()
    }

    @Test
    fun `rows carry board, counts, relative time and read state`() {
        val visited = setOf(key(1))
        val entries =
            feedEntries(
                feeds = listOf(boardFeed("g", thread(1, bumped = NOW - 60_000L, replies = 7, images = 3))),
                visited = visited,
                nowMillis = NOW,
            )

        val row = entries.single().row
        assertThat(row.board).isEqualTo("/g/")
        assertThat(row.replies).isEqualTo(7)
        assertThat(row.media).isEqualTo(3)
        assertThat(row.read).isTrue()
        assertThat(row.activity).isEqualTo("1m")
    }

    @Test
    fun `a thread with no subject falls back to its number, as the current feed does`() {
        val entries = feedEntries(listOf(boardFeed("g", thread(4471028))), emptySet(), 1L)

        assertThat(entries.single().row.subject).isEqualTo("No.4471028")
        assertThat(entries.single().title).isEqualTo("No.4471028")
    }

    @Test
    fun `a thread with no attachment asks for no preview tile`() {
        val entries =
            feedEntries(
                feeds = listOf(boardFeed("g", thread(1), thread(2, attachments = arrayOf(attachment())))),
                visited = emptySet(),
                nowMillis = 1L,
            )

        val byId = entries.associateBy { it.key.thread.value }
        assertThat(byId.getValue(1L).row.hasPreview).isFalse()
        assertThat(byId.getValue(1L).attachment).isNull()
        assertThat(byId.getValue(2L).row.hasPreview).isTrue()
    }

    @Test
    fun `row ids are unique across boards that reuse thread numbers`() {
        val entries =
            feedEntries(
                feeds =
                    listOf(
                        boardFeed("a", thread(1, board = "a")),
                        boardFeed("b", thread(1, board = "b")),
                    ),
                visited = emptySet(),
                nowMillis = 1L,
            )

        assertThat(entries.map { it.row.id }.toSet()).hasSize(2)
    }

    @Test
    fun `the feed filter matches board, subject, comment, poster and filename`() {
        val feeds = listOf(boardFeed("g", thread(1, board = "g", subject = "Home server on ARM")))

        // The previous feed's haystack, unchanged — narrowing it would quietly lose matches.
        assertThat(feedEntries(feeds, emptySet(), NOW, "arm")).hasSize(1)
        assertThat(feedEntries(feeds, emptySet(), NOW, "/g/".trim('/'))).hasSize(1)
        assertThat(feedEntries(feeds, emptySet(), NOW, "nothing here")).isEmpty()
    }

    @Test
    fun `the filter ignores case and surrounding space, and an empty filter keeps everything`() {
        val feeds = listOf(boardFeed("g", thread(1, board = "g", subject = "Weekly desktop thread")))

        assertThat(feedEntries(feeds, emptySet(), NOW, "  DESKTOP ")).hasSize(1)
        assertThat(feedEntries(feeds, emptySet(), NOW, "   ")).hasSize(1)
        assertThat(feedEntries(feeds, emptySet(), NOW, "")).hasSize(1)
    }

    @Test
    fun `filtering narrows the merged list without disturbing its order`() {
        val feeds =
            listOf(
                boardFeed(
                    "g",
                    thread(1, board = "g", subject = "keep me", bumped = 1_000L),
                    thread(2, board = "g", subject = "drop me", bumped = 5_000L),
                    thread(3, board = "g", subject = "keep me too", bumped = 9_000L),
                ),
            )

        val kept = feedEntries(feeds, emptySet(), NOW, "keep", FeedSort.ACTIVITY)
        assertThat(kept.map { it.key.thread.value }).containsExactly(3L, 1L).inOrder()
    }

    private fun boardFeed(
        board: String,
        vararg threads: CatalogThread,
    ) = SubscribedBoardFeed(
        board = Board(id = BoardId(board), title = board),
        threads = threads.toList().toPersistentList(),
        threadLimitOverride = null,
    )

    private fun key(
        id: Long,
        board: String = "g",
    ) = ThreadKey(ProviderId("fourchan"), BoardId(board), ThreadId(id))

    private fun thread(
        id: Long,
        board: String = "g",
        subject: String? = null,
        bumped: Long = 0L,
        created: Long = 0L,
        replies: Int = 0,
        images: Int = 0,
        attachments: Array<MediaAttachment> = emptyArray(),
    ) = CatalogThread(
        key = key(id, board),
        originalPost =
            Post(
                id = PostId(id),
                board = BoardId(board),
                threadId = ThreadId(id),
                isOriginalPost = true,
                subject = subject,
                createdAtMillis = created,
                attachments = attachments.toList().toPersistentList(),
            ),
        stats =
            ThreadStats(
                replyCount = replies,
                imageCount = images,
                lastModifiedMillis = bumped,
            ),
        previewReplies = persistentListOf(),
    )

    private fun attachment() =
        MediaAttachment(
            id = "a",
            originalFileName = "a.png",
            extension = "png",
            type = MediaType.IMAGE,
            sourceUrl = "https://example.org/a",
            thumbnailUrl = "https://example.org/a/thumb",
        )
}
