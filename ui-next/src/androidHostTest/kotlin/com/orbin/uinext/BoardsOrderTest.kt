package com.orbin.uinext

import org.junit.Assert.assertEquals
import org.junit.Test

class BoardsOrderTest {
    private fun tile(
        id: String,
        title: String = id,
    ) = BoardTile(id = id, path = "/$id/", title = title)

    @Test
    fun boardsListAToZWhateverOrderTheSiteGives() {
        val boards = listOf(tile("v"), tile("G"), tile("a"), tile("3"), tile("b"))

        assertEquals(listOf("3", "a", "b", "G", "v"), boards.sortedAlphabetically().map { it.id })
    }

    @Test
    fun titleBreaksTiesBetweenSitesSharingAPath() {
        val boards = listOf(tile("b", "Random · Site B"), tile("b", "Random · Site A"))

        assertEquals(
            listOf("Random · Site A", "Random · Site B"),
            boards.sortedAlphabetically().map { it.title },
        )
    }
}
