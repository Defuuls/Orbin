package com.orbin.uinext

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Forum
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class BoardSymbolsTest {
    @Test
    fun everyBbwChanBoardHasASymbol() {
        // bbwchan's boards as of September 2026.
        val boards = "bbw bbwdraw ssbbw bbwalt inf gen bbfurries booty bhm tits bbwai ee elite preg".split(" ")

        boards.forEach { assertNotNull(it, boardSymbol("bbwchan", it)) }
        assertEquals(boards.size, boards.map { boardSymbol("bbwchan", it) }.toSet().size)
    }

    @Test
    fun theSymbolIsPerSiteAndIgnoresCase() {
        assertEquals(Icons.Outlined.Forum, boardSymbol("bbwchan", "GEN"))
        assertNull(boardSymbol("fourchan", "gen"))
        assertNull(boardSymbol("bbwchan", "new"))
    }
}
