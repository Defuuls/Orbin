package com.orbin.domain.feed

import com.google.common.truth.Truth.assertThat
import com.orbin.core.model.Board
import com.orbin.core.model.BoardId
import com.orbin.core.model.CatalogThread
import com.orbin.core.model.FeedThreadLimit
import com.orbin.core.model.Post
import com.orbin.core.model.PostComment
import com.orbin.core.model.PostId
import com.orbin.core.model.ProviderId
import com.orbin.core.model.ThreadId
import com.orbin.core.model.ThreadKey
import com.orbin.core.model.ThreadStats
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class SubscribedFeedLoaderTest {
    private val loader = SubscribedFeedLoader(maxConcurrentBoards = 2)

    private fun board(
        id: String,
        nsfw: Boolean = false,
    ) = Board(BoardId(id), title = id, isNsfw = nsfw)

    private fun thread(
        board: String,
        id: Long,
    ): CatalogThread {
        val key = ThreadKey(ProviderId("site"), BoardId(board), ThreadId(id))
        return CatalogThread(
            key = key,
            originalPost =
                Post(
                    id = PostId(id),
                    board = BoardId(board),
                    threadId = ThreadId(id),
                    isOriginalPost = true,
                    comment = PostComment(raw = "thread $id", nodes = persistentListOf()),
                ),
            stats = ThreadStats(),
        )
    }

    @Test
    fun `covers only followed boards, hides nsfw when asked, in id order`() {
        val boards = listOf(board("v"), board("a"), board("b", nsfw = true), board("g"))
        val followed = setOf(BoardId("v"), BoardId("a"), BoardId("b"))

        assertThat(loader.feedBoards(boards, followed, hideNsfw = true).map { it.id.value })
            .containsExactly("a", "v")
            .inOrder()
        assertThat(loader.feedBoards(boards, followed, hideNsfw = false).map { it.id.value })
            .containsExactly("a", "b", "v")
            .inOrder()
    }

    @Test
    fun `cuts each board to its limit and keeps input order`() =
        runTest {
            val boards = listOf(board("a"), board("b"))
            val result =
                loader.load(boards, mapOf(BoardId("a") to FeedThreadLimit.SIX)) { id ->
                    (1L..10L).map { thread(id.value, it) }
                }

            assertThat(result.map { it.board.id.value }).containsExactly("a", "b").inOrder()
            assertThat(result[0].threads).hasSize(6)
            assertThat(result[0].limitOverride).isEqualTo(FeedThreadLimit.SIX)
            assertThat(result[1].threads).hasSize(10)
        }

    @Test
    fun `a failing board is kept apart and reported`() =
        runTest {
            val failures = mutableListOf<BoardId>()
            val result =
                loader.load(listOf(board("a"), board("b")), emptyMap(), { id, _ -> failures += id }) { id ->
                    if (id.value == "a") error("offline") else listOf(thread("b", 1))
                }

            assertThat(result[0].failed).isTrue()
            assertThat(result[0].threads).isEmpty()
            assertThat(result[1].failed).isFalse()
            assertThat(failures).containsExactly(BoardId("a"))
        }

    @Test
    fun `never loads more boards at once than allowed`() =
        runTest {
            val running = AtomicInteger()
            var peak = 0
            loader.load((1..6).map { board("b$it") }, emptyMap()) {
                peak = maxOf(peak, running.incrementAndGet())
                delay(10)
                running.decrementAndGet()
                emptyList()
            }

            assertThat(peak).isAtMost(2)
        }

    @Test
    fun `a failed board keeps what it showed last time`() {
        val before = listOf(BoardFeed(board("a"), listOf(thread("a", 1))), BoardFeed(board("b"), emptyList()))
        val now =
            listOf(BoardFeed(board("a"), emptyList(), failed = true), BoardFeed(board("b"), listOf(thread("b", 2))))

        val merged = SubscribedFeedLoader.withCachedFailures(now, before)

        assertThat(merged[0].threads.map { it.key.thread.value }).containsExactly(1L)
        assertThat(merged[0].failed).isTrue()
        assertThat(merged[1]).isEqualTo(now[1])
        assertThat(SubscribedFeedLoader.withCachedFailures(now, null)).isEqualTo(now)
    }
}
