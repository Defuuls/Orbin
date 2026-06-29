package com.orbin.feature.gallery

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class GalleryProgressFormatterTest {
    @Test
    fun formatsProgressMessageWithCurrentAndTotal() {
        assertThat(buildProgressMessage(2, 5, "video.mp4")).isEqualTo("2/5 · video.mp4")
    }
}
