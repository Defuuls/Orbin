package com.orbin.uinext

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.LayoutDirection
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * UI-only references of the actual shared standalone media chrome with final bundled Roboto.
 * Local original artwork replaces video pixels; these captures make no decoder/playback claim.
 * Native ownership/readiness and real control callbacks have separate host tests.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "en-rUS-w390dp-h844dp-mdpi")
class ReaderMediaChromeScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun phonePausedAndLoaded() = capture("phone_390_paused")

    @Test
    @Config(qualifiers = "en-rUS-w960dp-h800dp-mdpi")
    fun tabletPlaying() = capture("tablet_960_playing", playing = true)

    @Test
    @Config(qualifiers = "en-rUS-w248dp-h600dp-mdpi")
    fun narrowRightToLeftAtMaximumText() = capture("narrow_248_rtl_font_200", fontScale = 2f, rtl = true)

    private fun capture(
        name: String,
        playing: Boolean = false,
        fontScale: Float = 1f,
        rtl: Boolean = false,
    ) {
        composeRule.setContent {
            NextTheme(darkTheme = true, amoled = true, fontScale = fontScale) {
                CompositionLocalProvider(
                    LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr,
                ) {
                    MediaChromeFixture(playing)
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(ReaderMediaToolbarTags.BAR).assertIsDisplayed()
        composeRule.onNodeWithTag(ReaderMediaToolbarTags.CONTENT).assertIsDisplayed()
        composeRule.onNodeWithTag(ReaderMediaControlsTags.PANEL).assertIsDisplayed()
        composeRule.onNodeWithTag(ReaderMediaControlsTags.BUTTONS).assertIsDisplayed()
        composeRule.onNodeWithTag(ReaderMediaControlsTags.TIME_BAR).assertIsDisplayed()
        composeRule.onRoot().captureRoboImage("src/androidHostTest/screenshots/reader_media_$name.png")
    }
}

@Composable
private fun MediaChromeFixture(playing: Boolean) {
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        MediaChromePoster(Modifier.align(Alignment.Center).fillMaxWidth().aspectRatio(16f / 9f))
        ReaderMediaToolbar("orbin-landscape.mp4", "2/7", "Close", {}) {
            ReaderMediaAction(Icons.AutoMirrored.Filled.VolumeOff, "Unmute", {})
            ReaderMediaAction(Icons.Filled.FileDownload, "Save", {})
            ReaderMediaAction(Icons.Filled.MoreVert, "More options", {}, overflow = true)
        }
        ReaderMediaControls(
            playing = playing,
            positionSeconds = 24.0,
            durationSeconds = 96.0,
            bufferedFraction = 0.62f,
            onPlayPause = {},
            onSeek = {},
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

/** Original deterministic landscape, with no provider requests or copied reference imagery. */
@Composable
private fun MediaChromePoster(modifier: Modifier) {
    Canvas(modifier) {
        drawRect(Color(0xFF4E747B))
        drawCircle(
            Color(0xFFE9C18B),
            radius = size.height * 0.13f,
            center = Offset(size.width * 0.72f, size.height * 0.3f),
        )
        val distantRidge =
            Path().apply {
                moveTo(0f, size.height * 0.78f)
                lineTo(size.width * 0.24f, size.height * 0.32f)
                lineTo(size.width * 0.49f, size.height * 0.74f)
                lineTo(size.width * 0.74f, size.height * 0.43f)
                lineTo(size.width, size.height * 0.76f)
                lineTo(size.width, size.height)
                lineTo(0f, size.height)
                close()
            }
        drawPath(distantRidge, Color(0xFF335852))
        val foreground =
            Path().apply {
                moveTo(0f, size.height * 0.84f)
                lineTo(size.width * 0.38f, size.height * 0.68f)
                lineTo(size.width * 0.66f, size.height * 0.88f)
                lineTo(size.width, size.height * 0.7f)
                lineTo(size.width, size.height)
                lineTo(0f, size.height)
                close()
            }
        drawPath(foreground, Color(0xFF183F3A))
    }
}
