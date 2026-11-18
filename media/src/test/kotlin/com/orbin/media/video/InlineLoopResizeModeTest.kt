package com.orbin.media.video

import androidx.annotation.OptIn
import androidx.compose.ui.layout.ContentScale
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import com.google.common.truth.Truth.assertThat
import org.junit.Test

@OptIn(UnstableApi::class)
class InlineLoopResizeModeTest {
    /** A cropped thumbnail under a fitted video left the still image showing around the video. */
    @Test
    fun aCroppedThumbnailGetsAVideoThatFillsTheTileTheSameWay() {
        assertThat(inlineLoopResizeMode(ContentScale.Crop)).isEqualTo(AspectRatioFrameLayout.RESIZE_MODE_ZOOM)
    }

    @Test
    fun everyScaleThatKeepsTheWholeImageKeepsTheWholeVideo() {
        listOf(
            ContentScale.Fit,
            ContentScale.Inside,
            ContentScale.FillWidth,
            ContentScale.FillHeight,
            ContentScale.None,
        ).forEach { scale ->
            assertThat(inlineLoopResizeMode(scale)).isEqualTo(AspectRatioFrameLayout.RESIZE_MODE_FIT)
        }
    }

    @Test
    fun anInlineLoopDefaultsToShowingTheWholeFrame() {
        // The default is what InlineLoop uses when a caller does not say how its thumbnail is drawn.
        assertThat(inlineLoopResizeMode(ContentScale.Fit)).isEqualTo(AspectRatioFrameLayout.RESIZE_MODE_FIT)
    }
}
