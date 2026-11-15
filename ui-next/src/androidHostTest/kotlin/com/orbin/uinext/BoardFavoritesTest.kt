package com.orbin.uinext

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class BoardFavoritesTest {
    @Test
    fun favoritesAndFollowsRemainSeparateAndSourceQualifiedWithoutDuplicateRows() {
        val starred = BoardTile("one/g", "/g/", "Technology", site = "one", favorite = true)
        val followed = BoardTile("two/g", "/g/", "Technology", site = "two", followed = true)
        val both = BoardTile("one/a", "/a/", "Anime", site = "one", favorite = true, followed = true)
        val directory = BoardTile("two/a", "/a/", "Anime", site = "two")
        val sections = boardDirectorySections(listOf(starred, followed, both, directory))
        assertEquals(listOf(starred, both), sections.favorites)
        assertEquals(listOf(followed), sections.followed)
        assertEquals(listOf(directory), sections.directory)
        assertFalse(starred.followed)
        assertFalse(followed.favorite)
        assertEquals(4, (sections.favorites + sections.followed + sections.directory).map { it.id }.toSet().size)
    }
}
