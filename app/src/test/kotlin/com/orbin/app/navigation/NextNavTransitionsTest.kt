package com.orbin.app.navigation

import com.google.common.truth.Truth.assertThat
import com.orbin.uinext.tokens.NextMotion
import org.junit.Test

/**
 * Motion tokens gate the Feed↔Boards↔Thread feel. If PUSH collapses back to a 300ms linear
 * Material default, hierarchical navigation stops reading as a push at all.
 *
 * The M3 Expressive redesign dropped the iOS parallax drift on the outgoing screen, so there is
 * no PUSH_PARALLAX to assert here — a push now slides a full container width.
 */
class NextNavTransitionsTest {
    @Test
    fun pushIsSofterAndLongerThanMaterialDefault() {
        assertThat(NextMotion.PUSH_MS).isAtLeast(350)
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
