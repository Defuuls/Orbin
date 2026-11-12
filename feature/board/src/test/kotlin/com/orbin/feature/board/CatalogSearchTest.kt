package com.orbin.feature.board

import com.google.common.truth.Truth.assertThat
import com.orbin.uinext.FeedRow
import org.junit.Test

class CatalogSearchTest {
    @Test
    fun blankQueryPreservesLoadedPositionsAndPagingPlaceholders() {
        assertThat(catalogMatchingIndices(listOf(row("A", ""), null, row("B", "")), " \t "))
            .containsExactly(0, 1, 2)
            .inOrder()
    }

    @Test
    fun queryMatchesAllTermsAcrossTitleAndBodyIgnoringCase() {
        val rows = listOf(row("Quiet landscapes", "GREEN hills"), row("Quiet sea", "Blue water"), null)
        assertThat(catalogMatchingIndices(rows, "green QUIET")).containsExactly(0)
    }

    @Test
    fun unmatchedQueryAndEmptyCatalogProduceNoRows() {
        assertThat(catalogMatchingIndices(listOf(row("A", "B"), null), "missing")).isEmpty()
        assertThat(catalogMatchingIndices(emptyList(), "")).isEmpty()
    }

    private fun row(
        title: String,
        body: String,
    ) = FeedRow(title, "/g/", "", 0, 0, excerpt = body)
}
