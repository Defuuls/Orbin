package com.orbin.media.video

import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Test

class SimpleVideoRulesTest {
    @Test
    fun `explicit inline fullscreen handoff preserves even a short looping clip`() {
        val url = "https://x/inline.webm"
        val capture = { 4_000L to 10_000L }
        PlaybackPositions.register(url, capture)
        try {
            prepareVideoPlaybackHandoff(url)
            assertThat(PlaybackPositions.resumeAt(url)).isEqualTo(4_000L)
            PlaybackPositions.remember(url, positionMs = 4_100L, durationMs = 10_000L)
            assertThat(PlaybackPositions.resumeAt(url)).isEqualTo(4_100L)
            PlaybackPositions.remember(url, positionMs = 5_000L, durationMs = 10_000L)
            assertThat(PlaybackPositions.resumeAt(url)).isEqualTo(0L)
        } finally {
            PlaybackPositions.unregister(url, capture)
        }
    }

    @After
    fun reset() = PlaybackPositions.clear()

    @Test
    fun `short clips loop and long videos play once`() {
        assertThat(shouldLoop(8_000L)).isTrue()
        assertThat(shouldLoop(30_000L)).isTrue()
        assertThat(shouldLoop(30_001L)).isFalse()
    }

    @Test
    fun `a video of unknown length loops until its length is known`() {
        assertThat(shouldLoop(0L)).isTrue()
    }

    @Test
    fun `a long video resumes where it was left`() {
        PlaybackPositions.remember("https://x/a.webm", positionMs = 42_000L, durationMs = 120_000L)

        assertThat(PlaybackPositions.resumeAt("https://x/a.webm")).isEqualTo(42_000L)
    }

    @Test
    fun `a finished or short video starts from the top`() {
        PlaybackPositions.remember("https://x/end.webm", positionMs = 119_000L, durationMs = 120_000L)
        PlaybackPositions.remember("https://x/clip.webm", positionMs = 4_000L, durationMs = 10_000L)

        assertThat(PlaybackPositions.resumeAt("https://x/end.webm")).isEqualTo(0L)
        assertThat(PlaybackPositions.resumeAt("https://x/clip.webm")).isEqualTo(0L)
    }
}
