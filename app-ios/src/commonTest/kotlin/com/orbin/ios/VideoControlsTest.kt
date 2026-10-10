package com.orbin.ios

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class VideoControlsTest {
    @Test
    fun clockShowsMinutesAndPaddedSeconds() {
        assertEquals("0:00", clock(0.0))
        assertEquals("1:05", clock(65.4))
        assertEquals("12:00", clock(720.0))
    }

    @Test
    fun seekStaysWithinTheVideo() {
        assertEquals(0.0, clampedSeek(-4.0, 30.0))
        assertEquals(30.0, clampedSeek(42.0, 30.0))
        assertEquals(42.0, clampedSeek(42.0, 0.0), "unknown length: only the floor applies")
    }

    @Test
    fun webMStateParsesThePagesReport() {
        assertEquals(VideoState(playing = true, position = 3.5, duration = 20.0), parseWebMState("1,3.5,20"))
        assertEquals(VideoState(playing = false, position = 0.0, duration = 0.0), parseWebMState("0,0,NaN"))
        assertNull(parseWebMState("not ready"))
    }
}
