package com.orbin.core.model.stream

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SwipeDecisionTest {
    @Test
    fun aQuickMostlyHorizontalSwipeMovesOneView() {
        assertThat(SwipeDecision.horizontal(-120.0, 10.0, 200)).isEqualTo(1)
        assertThat(SwipeDecision.horizontal(120.0, -10.0, 200)).isEqualTo(-1)
    }

    @Test
    fun shortDiagonalSlowVerticalAndNonFiniteGesturesAreNotSwipes() {
        assertThat(SwipeDecision.horizontal(-50.0, 0.0, 100)).isEqualTo(0)
        assertThat(SwipeDecision.horizontal(-120.0, 80.0, 200)).isEqualTo(0)
        assertThat(SwipeDecision.horizontal(-120.0, 0.0, 900)).isEqualTo(0)
        assertThat(SwipeDecision.horizontal(0.0, 300.0, 100)).isEqualTo(0)
        assertThat(SwipeDecision.horizontal(Double.NaN, 0.0, 100)).isEqualTo(0)
        assertThat(SwipeDecision.horizontal(-120.0, Double.POSITIVE_INFINITY, 100)).isEqualTo(0)
    }

    @Test
    fun thresholdsAreInclusiveAtTheEdge() {
        assertThat(SwipeDecision.horizontal(-72.0, 0.0, 600)).isEqualTo(1)
        assertThat(SwipeDecision.horizontal(-71.9, 0.0, 600)).isEqualTo(0)
        assertThat(SwipeDecision.horizontal(-100.0, 40.0, 100)).isEqualTo(1)
        assertThat(SwipeDecision.horizontal(-100.0, 40.1, 100)).isEqualTo(0)
    }

    @Test
    fun onlyAQuickMostlyVerticalDownwardDragDismisses() {
        assertThat(SwipeDecision.dismiss(5.0, 140.0, 200)).isTrue()
        assertThat(SwipeDecision.dismiss(0.0, 96.0, 200)).isTrue()
        assertThat(SwipeDecision.dismiss(0.0, 95.0, 200)).isFalse()
        assertThat(SwipeDecision.dismiss(0.0, -200.0, 200)).isFalse()
        assertThat(SwipeDecision.dismiss(100.0, 140.0, 200)).isFalse()
        assertThat(SwipeDecision.dismiss(0.0, 200.0, 900)).isFalse()
        assertThat(SwipeDecision.dismiss(Double.NaN, 200.0, 100)).isFalse()
    }

    @Test
    fun clampIndexStaysInsideTheListAndToleratesAnEmptyOne() {
        assertThat(SwipeDecision.clampIndex(-1, 4)).isEqualTo(0)
        assertThat(SwipeDecision.clampIndex(4, 4)).isEqualTo(3)
        assertThat(SwipeDecision.clampIndex(2, 4)).isEqualTo(2)
        assertThat(SwipeDecision.clampIndex(3, 0)).isEqualTo(0)
    }
}
