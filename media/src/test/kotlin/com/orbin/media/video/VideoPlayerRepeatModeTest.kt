package com.orbin.media.video

import androidx.media3.common.Player
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class VideoPlayerRepeatModeTest {
    @Test
    fun `loop enabled repeats current media`() {
        assertThat(repeatModeFor(true)).isEqualTo(Player.REPEAT_MODE_ONE)
    }

    @Test
    fun `loop disabled plays current media once`() {
        assertThat(repeatModeFor(false)).isEqualTo(Player.REPEAT_MODE_OFF)
    }
}
