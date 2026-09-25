package com.orbin.uinext

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaSizeRangeTest {
    @Test
    fun feedSizeRangeSpansMildToWild() {
        assertEquals(MEDIA_SIZE_MIN_DP, FEED_SIZE_MIN_DP)
        assertEquals(MEDIA_SIZE_MAX_DP, FEED_SIZE_MAX_DP)
        assertEquals(MEDIA_SIZE_STEPS, FEED_SIZE_STEPS)
        // Mild must be dense enough for multi-column phones; wild near full-bleed.
        assertTrue(FEED_SIZE_MIN_DP <= 100f)
        assertTrue(FEED_SIZE_MAX_DP >= 360f)
        assertTrue(FEED_SIZE_MAX_DP - FEED_SIZE_MIN_DP >= 250f)
        assertTrue(FEED_SIZE_STEPS >= 8)
    }
}
