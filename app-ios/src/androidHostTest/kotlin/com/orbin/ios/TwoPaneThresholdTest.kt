package com.orbin.ios

import com.orbin.core.model.TWO_PANE_MIN_WIDTH_DP
import com.orbin.core.model.WIDE_SCREEN_MIN_DP
import com.orbin.uinext.tokens.ReaderSize
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The two-pane rule lives in core:model, which decides layouts, and as a design token in ui-next,
 * which may not depend on core:model. This module sees both, so it keeps them from drifting.
 */
class TwoPaneThresholdTest {
    @Test
    fun theDesignTokenAndTheLayoutRuleAgreeOnTheTwoPaneWidth() {
        assertEquals(TWO_PANE_MIN_WIDTH_DP.toFloat(), ReaderSize.twoPaneMinWidth.value)
    }

    @Test
    fun theDesignTokenAndTheLayoutRuleAgreeOnTheWideWindowSide() {
        assertEquals(WIDE_SCREEN_MIN_DP.toFloat(), ReaderSize.wideMin.value)
    }
}
