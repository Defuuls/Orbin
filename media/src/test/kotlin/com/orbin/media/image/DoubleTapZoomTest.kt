package com.orbin.media.image

import androidx.compose.ui.geometry.Offset
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DoubleTapZoomTest {
    @Test
    fun `tapping the centre zooms without moving`() {
        assertThat(doubleTapOffset(Offset(500f, 1000f), width = 1000f, height = 2000f)).isEqualTo(Offset.Zero)
    }

    @Test
    fun `tapping a corner pulls that corner towards the middle`() {
        val offset = doubleTapOffset(Offset(0f, 0f), width = 1000f, height = 2000f)

        assertThat(offset.x).isGreaterThan(0f)
        assertThat(offset.y).isGreaterThan(0f)
    }
}
