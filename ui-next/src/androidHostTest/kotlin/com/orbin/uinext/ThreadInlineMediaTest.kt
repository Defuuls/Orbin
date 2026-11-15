package com.orbin.uinext

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ThreadInlineMediaTest {
    @Test
    fun visibilityElectsOnlyOneStableOwnerAndNeverAnOffscreenOrMostlyClippedTile() {
        val candidates =
            listOf(
                ThreadMediaVisibility("offscreen", 0f, 0f),
                ThreadMediaVisibility("clipped", 0.59f, 0f),
                ThreadMediaVisibility("visible-second", 1f, 50f),
                ThreadMediaVisibility("visible-first", 1f, 20f),
            )
        assertEquals("visible-first", selectThreadInlineMedia(candidates))
        assertEquals("visible-first", selectThreadInlineMedia(candidates.reversed()))
        assertNull(selectThreadInlineMedia(candidates.take(2)))
        assertEquals("clipped", selectThreadInlineMedia(candidates, "clipped"))
        assertEquals("visible-first", selectThreadInlineMedia(candidates, "offscreen"))
    }

    @Test
    fun removingOrScrollingAwayAManualOwnerReturnsOwnershipToVisibleMedia() {
        val state = ThreadInlineMediaState()
        state.update(ThreadMediaVisibility("one", 1f, 20f))
        state.update(ThreadMediaVisibility("two", 1f, 40f))
        state.requestPlay("two")
        assertEquals("two", state.activeId)
        state.update(ThreadMediaVisibility("two", 0f, 400f))
        assertEquals("one", state.activeId)
        state.remove("one")
        assertNull(state.activeId)
    }
}
