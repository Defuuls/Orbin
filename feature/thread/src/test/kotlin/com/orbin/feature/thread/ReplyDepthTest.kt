package com.orbin.feature.thread

import com.google.common.truth.Truth.assertThat
import com.orbin.core.model.BoardId
import com.orbin.core.model.Post
import com.orbin.core.model.PostId
import com.orbin.core.model.ThreadId
import kotlinx.collections.immutable.toPersistentList
import org.junit.Test

/**
 * The depth the new reader indents by. The thread model is flat and carries no nesting, so this is
 * derived entirely from quote links — which makes its edge cases worth pinning down.
 */
class ReplyDepthTest {
    @Test
    fun `a thread with no quotes is entirely flat`() {
        val posts = listOf(post(1), post(2), post(3))

        assertThat(replyDepths(posts).values).containsExactly(0, 0, 0)
    }

    @Test
    fun `depth grows one step per link in the chain`() {
        val posts =
            listOf(post(1), post(2, quotes = listOf(1)), post(3, quotes = listOf(2)), post(4, quotes = listOf(3)))

        assertThat(replyDepths(posts).values.toList()).containsExactly(0, 0, 1, 2).inOrder()
    }

    @Test
    fun `quoting only the opening post is not nesting`() {
        // Quoting the OP is how people address the thread itself. Counting it would indent most of
        // a thread by one and say nothing.
        val posts = listOf(post(1), post(2, quotes = listOf(1)), post(3, quotes = listOf(1)))

        assertThat(replyDepths(posts).values).containsExactly(0, 0, 0)
    }

    @Test
    fun `a post rejoining a shallower conversation follows the shallower one`() {
        val posts =
            listOf(
                post(1),
                post(2, quotes = listOf(1)),
                post(3, quotes = listOf(2)),
                post(4, quotes = listOf(3)),
                // Answers both the deep sub-thread and a top-level post: it belongs to the
                // conversation it rejoins, not the one it left.
                post(5, quotes = listOf(4, 2)),
            )

        assertThat(replyDepths(posts).values.toList()).containsExactly(0, 0, 1, 2, 1).inOrder()
    }

    @Test
    fun `a quote pointing forward is ignored rather than trusted`() {
        // Depth has to stay well-defined, and a forward reference cannot contribute one.
        val posts = listOf(post(1), post(2, quotes = listOf(9)), post(3, quotes = listOf(2)))

        assertThat(replyDepths(posts).values.toList()).containsExactly(0, 0, 1).inOrder()
    }

    @Test
    fun `a quote to a post that is not in the thread is ignored`() {
        val posts = listOf(post(1), post(2, quotes = listOf(4471028)))

        assertThat(replyDepths(posts).values).containsExactly(0, 0)
    }

    @Test
    fun `every post gets a depth, including one quoting itself`() {
        val posts = listOf(post(1), post(2, quotes = listOf(2)))

        assertThat(replyDepths(posts)).hasSize(2)
        assertThat(replyDepths(posts).values).containsExactly(0, 0)
    }

    @Test
    fun `an empty thread yields no depths rather than failing`() {
        assertThat(replyDepths(emptyList())).isEmpty()
    }

    @Test
    fun `reply counts come from quote links, so a saved copy still shows them`() {
        val posts =
            listOf(
                post(1),
                post(2, quotes = listOf(1)),
                post(3, quotes = listOf(1)),
                post(4, quotes = listOf(2)),
            )

        val counts = replyCounts(posts)
        assertThat(counts[PostId(1)]).isEqualTo(2)
        assertThat(counts[PostId(2)]).isEqualTo(1)
        assertThat(counts[PostId(3)]).isNull()
    }

    @Test
    fun `quoting the same post twice in one comment counts once`() {
        val posts = listOf(post(1), post(2, quotes = listOf(1, 1)))

        assertThat(replyCounts(posts)[PostId(1)]).isEqualTo(1)
    }

    private fun post(
        id: Long,
        quotes: List<Long> = emptyList(),
    ) = Post(
        id = PostId(id),
        board = BoardId("g"),
        threadId = ThreadId(1),
        isOriginalPost = id == 1L,
        repliesTo = quotes.map { PostId(it) }.toPersistentList(),
    )
}
