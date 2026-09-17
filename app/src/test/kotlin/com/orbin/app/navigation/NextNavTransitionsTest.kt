package com.orbin.app.navigation

import com.google.common.truth.Truth.assertThat
import com.orbin.uinext.tokens.NextMotion
import org.junit.Test

/**
 * Motion tokens gate the Feed↔Boards↔Thread feel. If PUSH collapses back to a 300ms linear
 * Material default, the rest of the Apple Next chrome reads as Android again.
 */
class NextNavTransitionsTest {
    @Test
    fun pushIsSofterAndLongerThanMaterialDefault() {
        assertThat(NextMotion.PUSH_MS).isAtLeast(350)
        assertThat(NextMotion.PUSH_PARALLAX).isWithin(0.001f).of(0.3f)
        assertThat(NextMotion.PUSH_PARALLAX).isLessThan(1f)
    }

    @Test
    fun tabSwapIsShorterThanHierarchicalPush() {
        assertThat(NextMotion.TAB_MS).isLessThan(NextMotion.PUSH_MS)
        assertThat(NextMotion.TAB_NUDGE).isLessThan(NextMotion.PUSH_PARALLAX)
    }

    @Test
    fun easingSettlesWithoutLinearSnap() {
        // CubicBezier(0.32, 0.72, 0, 1) — ease-out biased; value at mid-time is past halfway.
        assertThat(NextNavEasing.transform(0.5f)).isGreaterThan(0.5f)
        assertThat(NextNavEasing.transform(0f)).isEqualTo(0f)
        assertThat(NextNavEasing.transform(1f)).isEqualTo(1f)
    }
}
