package com.orbin.uinext

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderMediaGesturePolicyTest {
    @Test
    fun closeNeedsDeliberateHorizontalMovementInEitherDirection() {
        assertTrue(mediaCloseSwipe(100f, 10f, 96f))
        assertTrue(mediaCloseSwipe(-100f, 10f, 96f))
        assertFalse(mediaCloseSwipe(90f, 0f, 96f))
        assertFalse(mediaCloseSwipe(100f, 100f, 96f))
        assertFalse(mediaCloseSwipe(10f, 200f, 96f))
    }

    @Test
    fun nativePlaybackReceivesOnlySupportedFiniteRates() {
        listOf(1f, 1.25f, 2f).forEach { assertEquals(it, mediaPlaybackSpeed(it), 0f) }
        listOf(0f, -1f, Float.NaN, Float.POSITIVE_INFINITY, 4f).forEach {
            assertEquals(1f, mediaPlaybackSpeed(it), 0f)
        }
    }
}
