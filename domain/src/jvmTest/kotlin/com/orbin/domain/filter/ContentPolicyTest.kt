package com.orbin.domain.filter

import com.google.common.truth.Truth.assertThat
import com.orbin.core.model.Board
import com.orbin.core.model.BoardId
import com.orbin.core.model.CatalogThread
import com.orbin.core.model.Post
import com.orbin.core.model.PostComment
import com.orbin.core.model.PostId
import com.orbin.core.model.ProviderId
import com.orbin.core.model.Thread
import com.orbin.core.model.ThreadId
import com.orbin.core.model.ThreadKey
import com.orbin.core.model.ThreadStats
import kotlinx.collections.immutable.persistentListOf
import org.junit.Test

class ContentPolicyTest {
    private val board = BoardId("g")
    private val key = ThreadKey(ProviderId("site"), board, ThreadId(1))

    private fun post(
        id: Long,
        text: String,
        op: Boolean = false,
    ) = Post(
        id = PostId(id),
        board = board,
        threadId = ThreadId(1),
        isOriginalPost = op,
        comment = PostComment(raw = text, nodes = persistentListOf()),
    )

    @Test
    fun `filtered boards are left out`() {
        val boards = listOf(Board(BoardId("g"), "Technology"), Board(BoardId("x"), "Gore"))
        assertThat(ContentPolicy.visibleBoards(boards).map { it.id.value }).containsExactly("g")
    }

    @Test
    fun `filtered catalog threads are left out`() {
        val threads =
            listOf(
                CatalogThread(key, post(1, "hello", op = true), ThreadStats()),
                CatalogThread(key.copy(thread = ThreadId(2)), post(2, "gore video", op = true), ThreadStats()),
            )
        assertThat(ContentPolicy.visibleCatalog(threads).map { it.originalPost.id.value }).containsExactly(1L)
    }

    @Test
    fun `a filtered thread is not shown and filtered replies are hidden`() {
        val thread = Thread(key, post(1, "hello", op = true), persistentListOf(post(2, "gore"), post(3, "fine")))
        assertThat(ContentPolicy.visibleThread(thread)?.replies?.map { it.id.value }).containsExactly(3L)

        val caught = Thread(key, post(1, "gore", op = true), persistentListOf())
        assertThat(ContentPolicy.visibleThread(caught)).isNull()
    }
}
