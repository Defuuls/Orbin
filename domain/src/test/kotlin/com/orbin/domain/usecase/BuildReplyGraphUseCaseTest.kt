package com.orbin.domain.usecase

import com.google.common.truth.Truth.assertThat
import com.orbin.core.model.BoardId
import com.orbin.core.model.Post
import com.orbin.core.model.PostComment
import com.orbin.core.model.PostId
import com.orbin.core.model.PostNode
import com.orbin.core.model.ProviderId
import com.orbin.core.model.Thread
import com.orbin.core.model.ThreadId
import com.orbin.core.model.ThreadKey
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import org.junit.Test

class BuildReplyGraphUseCaseTest {
    private val useCase = BuildReplyGraphUseCase()
    private val board = BoardId("g")
    private val threadId = ThreadId(1000)
    private val key = ThreadKey(ProviderId("test"), board, threadId)

    private fun post(
        id: Long,
        quotes: List<Long> = emptyList(),
        op: Boolean = false,
    ): Post {
        val nodes = quotes.map { PostNode.QuoteLink(target = PostId(it)) }.toImmutableList()
        return Post(
            id = PostId(id),
            board = board,
            threadId = threadId,
            isOriginalPost = op,
            comment = PostComment(raw = "", nodes = nodes),
        )
    }

    @Test
    fun `backlinks are inverted from forward quotes`() {
        val op = post(1000, op = true)
        val r1 = post(1001, quotes = listOf(1000))
        val r2 = post(1002, quotes = listOf(1000, 1001))
        val thread =
            Thread(
                key = key,
                originalPost = op,
                replies = persistentListOf(r1, r2),
            )

        val result = useCase(thread)

        // OP is quoted by 1001 and 1002.
        assertThat(result.originalPost.backlinks.map { it.value }).containsExactly(1001L, 1002L)
        // 1001 is quoted by 1002.
        assertThat(
            result.replies
                .first { it.id.value == 1001L }
                .backlinks
                .map { it.value },
        ).containsExactly(1002L)
        // 1002 is quoted by nobody.
        assertThat(result.replies.first { it.id.value == 1002L }.backlinks).isEmpty()
    }

    @Test
    fun `thread with no quotes yields no backlinks`() {
        val thread =
            Thread(
                key = key,
                originalPost = post(1000, op = true),
                replies = persistentListOf(post(1001), post(1002)),
            )

        val result = useCase(thread)

        assertThat(result.allPosts.flatMap { it.backlinks }).isEmpty()
    }
}
