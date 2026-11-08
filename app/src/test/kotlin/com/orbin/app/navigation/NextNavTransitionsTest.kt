package com.orbin.app.navigation

import com.google.common.truth.Truth.assertThat
import com.orbin.uinext.tokens.NextMotion
import org.junit.Test

/**
 * Motion tokens gate the Feed↔Boards↔Thread feel under OPTIC-26 mechanical transitions.
 */
class NextNavTransitionsTest {
    @Test
    fun pushUsesCrispMechanicalDuration() {
        assertThat(NextMotion.PUSH_MS).isEqualTo(240)
    }

    @Test
    fun tabSwapIsShorterThanHierarchicalPush() {
        assertThat(NextMotion.TAB_MS).isLessThan(NextMotion.PUSH_MS)
    }

    @Test
    fun siblingTabNudgeStaysAShortLateralOffset() {
        // A lateral tab swap must not read as a hierarchical push: nudge a fraction, not a width.
        assertThat(NextMotion.TAB_NUDGE).isGreaterThan(0f)
        assertThat(NextMotion.TAB_NUDGE).isLessThan(0.2f)
    }

    @Test
    fun easingSettlesWithoutLinearSnap() {
        // CubicBezier(0.32, 0.72, 0, 1) — ease-out biased; value at mid-time is past halfway.
        assertThat(NextMotion.Ease.transform(0.5f)).isGreaterThan(0.5f)
        assertThat(NextMotion.Ease.transform(0f)).isEqualTo(0f)
        assertThat(NextMotion.Ease.transform(1f)).isEqualTo(1f)
        assertThat(NextNavEasing).isEqualTo(NextMotion.Ease)
    }

    @Test
    fun chromeIsSnappierThanTabSwap() {
        assertThat(NextMotion.CHROME_MS).isAtMost(NextMotion.TAB_MS)
    }
}
