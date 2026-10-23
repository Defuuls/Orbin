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
}
