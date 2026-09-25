package com.orbin.media.video

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DoubleTapSeekTest {
    @Test
    fun `a forward double tap skips five seconds ahead`() {
        assertThat(seekTargetMs(currentMs = 12_000, durationMs = 60_000, forward = true)).isEqualTo(17_000)
    }

    @Test
    fun `a back double tap skips five seconds back`() {
        assertThat(seekTargetMs(currentMs = 12_000, durationMs = 60_000, forward = false)).isEqualTo(7_000)
    }

    @Test
    fun `skipping back near the start stops at the start`() {
        assertThat(seekTargetMs(currentMs = 3_000, durationMs = 60_000, forward = false)).isEqualTo(0)
    }

    @Test
    fun `skipping forward near the end stops at the end`() {
        assertThat(seekTargetMs(currentMs = 58_000, durationMs = 60_000, forward = true)).isEqualTo(60_000)
    }

    @Test
    fun `an unknown duration only clamps at the start`() {
        // ExoPlayer reports C.TIME_UNSET, a large negative, until the duration is known.
        assertThat(seekTargetMs(currentMs = 1_000, durationMs = Long.MIN_VALUE + 1, forward = true)).isEqualTo(6_000)
        assertThat(seekTargetMs(currentMs = 1_000, durationMs = Long.MIN_VALUE + 1, forward = false)).isEqualTo(0)
    }

    @Test
    fun `the skip is five seconds`() {
        assertThat(SKIP_SECONDS).isEqualTo(5)
    }
}
