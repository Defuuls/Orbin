package com.orbin.core.reader

import kotlin.test.Test
import kotlin.test.assertEquals

class BoardLabelTest {
    @Test
    fun aBareCodeGetsOnePairOfSlashes() = assertEquals("/p/", boardLabel("p"))

    @Test
    fun anAlreadyWrappedCodeIsNotWrappedAgain() {
        assertEquals("/p/", boardLabel("/p/"))
        assertEquals("/g/", boardLabel("//g//"))
    }

    @Test
    fun aBlankBoardStaysBlank() {
        assertEquals("", boardLabel(""))
        assertEquals("", boardLabel("/"))
    }

    @Test
    fun labellingIsIdempotent() = assertEquals(boardLabel("wg"), boardLabel(boardLabel("wg")))
}
