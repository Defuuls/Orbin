package com.orbin.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class CatalogSortTest {
    @Test
    fun `the sort chip steps through every order and wraps round`() {
        val visited = generateSequence(CatalogSort.BUMP_ORDER) { it.next() }.take(CatalogSort.entries.size + 1).toList()

        assertThat(visited.dropLast(1)).containsExactlyElementsIn(CatalogSort.entries).inOrder()
        assertThat(visited.last()).isEqualTo(CatalogSort.BUMP_ORDER)
    }

    @Test
    fun `newest oldest reply and image orders use the correct source fields`() {
        val items =
            listOf(
                thread(1, created = 30, replies = 2, images = 9),
                thread(2, created = 10, replies = 8, images = 1),
                thread(3, created = 20, replies = 5, images = 4),
            )

        fun order(sort: CatalogSort) = items.sortedWith(sort.comparator()).map { it.key.thread.value }
        assertThat(order(CatalogSort.CREATION_DATE)).containsExactly(1L, 3L, 2L).inOrder()
        assertThat(order(CatalogSort.OLDEST)).containsExactly(2L, 3L, 1L).inOrder()
        assertThat(order(CatalogSort.REPLY_COUNT)).containsExactly(2L, 3L, 1L).inOrder()
        assertThat(order(CatalogSort.IMAGE_COUNT)).containsExactly(1L, 3L, 2L).inOrder()
    }

    private fun thread(
        id: Long,
        created: Long,
        replies: Int,
        images: Int,
    ): CatalogThread {
        val key = ThreadKey(ProviderId("test"), BoardId("g"), ThreadId(id))
        return CatalogThread(
            key,
            Post(PostId(id), key.board, key.thread, isOriginalPost = true, createdAtMillis = created),
            ThreadStats(replyCount = replies, imageCount = images),
        )
    }
}
