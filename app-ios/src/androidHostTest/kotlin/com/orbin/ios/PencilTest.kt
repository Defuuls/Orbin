package com.orbin.ios

import kotlin.math.PI
import kotlin.test.Test
import kotlin.test.assertEquals

class PencilTest {
    @Test
    fun rollDeltaIsTheTurnBetweenTwoReadings() {
        assertEquals(0.25f, rollDelta(1f, 1.25f), 1e-5f)
        assertEquals(-0.25f, rollDelta(1.25f, 1f), 1e-5f)
    }

    @Test
    fun rollDeltaTakesTheShortWayAcrossTheSeam() {
        val nearPi = PI.toFloat() - 0.1f
        // From just under +π to just over -π is a small turn forward, not almost a full circle back.
        assertEquals(0.2f, rollDelta(nearPi, -nearPi), 1e-4f)
        assertEquals(-0.2f, rollDelta(-nearPi, nearPi), 1e-4f)
    }
}
