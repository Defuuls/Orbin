package com.orbin.uinext

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** The public directory snapshot and future-board fallback must both remain fully illustrated. */
class BoardSymbolsTest {
    @Test
    fun everyAdvertisedBoardHasAnExplicitTopicAndDistinctVisualIdentity() {
        val boards = directoryFixture()
        assertEquals(77, boards.count { it.site == "fourchan" })
        assertEquals(14, boards.count { it.site == "bbwchan" })
        assertEquals(registeredBoardIconIds, boards.map { "${it.site}/${it.board}" }.toSet())
        val icons = boards.map { boardIconDefinition(it.site, it.board, it.title) }
        assertTrue(icons.all { it.registered })
        assertEquals(boards.size, icons.map { it.visualIdentity }.toSet().size)
        boards.forEach { assertNotNull(it.toString(), boardSymbol(it.site, it.board)) }
    }

    @Test
    fun sourceAliasesCaseAndSlashNotationPreserveTheSameIdentity() {
        val canonical = boardIconDefinition("fourchan", "g")
        val display = boardIconDefinition("4chan", "/G/")
        assertEquals(canonical.identity, display.identity)
        assertEquals(canonical.visualIdentity, display.visualIdentity)
        assertSame(canonical.symbol, display.symbol)
        assertEquals("bbwchan/gen", boardIconDefinition("BBW Chan", "/GEN/").identity)
    }

    @Test
    fun identicalBoardCodesOnDifferentSitesNeverShareAnIdentityOrProviderMark() {
        val fourchan = boardIconDefinition("fourchan", "g")
        val bbwchan = boardIconDefinition("bbwchan", "g")
        assertNotEquals(fourchan.identity, bbwchan.identity)
        assertNotEquals(fourchan.fingerprint, bbwchan.fingerprint)
        assertNotEquals(fourchan.providerMark, bbwchan.providerMark)
        assertNotEquals(fourchan.visualIdentity, bbwchan.visualIdentity)
    }

    @Test
    fun futureBoardsHaveStableDistinctIconsWithoutARegistryUpdate() {
        val first = boardIconDefinition("fourchan", "future-board")
        val again = boardIconDefinition("4chan", "/FUTURE-BOARD/")
        val other = boardIconDefinition("fourchan", "another-future-board")
        assertFalse(first.registered)
        assertEquals(first.identity, again.identity)
        assertEquals(first.fingerprint, again.fingerprint)
        assertEquals(first.visualIdentity, again.visualIdentity)
        assertNotEquals(first.visualIdentity, other.visualIdentity)
        assertNotNull(boardSymbol("new-provider", "new-board"))
    }

    @Test
    fun unknownBoardMetadataCanSupplyAnAppropriateTopicWithoutLosingItsIdentity() {
        val withoutTitle = boardIconDefinition("future-provider", "creative")
        val withTitle = boardIconDefinition("future-provider", "creative", "Drawing and illustration")
        assertEquals(withoutTitle.identity, withTitle.identity)
        assertEquals(withoutTitle.fingerprint, withTitle.fingerprint)
        assertEquals(BoardTopic.ART, withTitle.topic)
    }

    private fun directoryFixture(): List<DirectoryBoard> =
        requireNotNull(javaClass.classLoader?.getResourceAsStream("board-icon-directory.tsv"))
            .bufferedReader()
            .useLines { lines ->
                lines
                    .filter { it.isNotBlank() && !it.startsWith("#") }
                    .map { line -> line.split('\t', limit = 3).let { DirectoryBoard(it[0], it[1], it[2]) } }
                    .toList()
            }

    private data class DirectoryBoard(
        val site: String,
        val board: String,
        val title: String,
    )
}
