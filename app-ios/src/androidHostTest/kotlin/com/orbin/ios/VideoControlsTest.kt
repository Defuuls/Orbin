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
    fun audioChoiceSurvivesRepeatedTogglesAndNewPlayers() {
        SessionAudio.muted = true
        try {
            assertEquals(true, SessionAudio.muted)
            var current = SessionAudio.muted
            repeat(5) { index ->
                current = SessionAudio.toggle(current)
                assertEquals(index % 2 != 0, current)
                assertEquals(current, SessionAudio.muted, "next page or reopened player inherits the choice")
            }
            assertEquals(true, SessionAudio.toggle(SessionAudio.muted))
        } finally {
            SessionAudio.muted = true
        }
    }

    @Test
    fun webMScriptAppliesChoiceAndReportsActualMuteState() {
        kotlin.test.assertTrue(webMStateScript(true).contains("v.muted=true;"))
        kotlin.test.assertTrue(webMStateScript(false).contains("v.muted=false;"))
        kotlin.test.assertTrue(webMStateScript(false).contains("(v.muted?'1':'0')"))
    }

    @Test
    fun webMStateParsesThePagesReport() {
        assertEquals(VideoState(playing = true, position = 3.5, duration = 20.0), parseWebMState("1,3.5,20,1"))
        assertEquals(VideoState(playing = false, position = 0.0, duration = 0.0), parseWebMState("0,0,NaN,1"))
        assertEquals(VideoState(muted = false), parseWebMState("0,0,0,0"))
        assertNull(parseWebMState("1,3.5,20"))
        assertNull(parseWebMState("1,3.5,20,unknown"))
        assertNull(parseWebMState("1,NaN,20,1"))
        assertNull(parseWebMState("not ready"))
    }
}
