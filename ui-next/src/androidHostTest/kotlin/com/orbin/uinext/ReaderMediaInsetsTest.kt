package com.orbin.uinext

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Full-screen media chrome must stay in the safe drawing area on edge-to-edge Android, including
 * tall 20:9 phones such as the Pixel 10 Pro XL (1344×2992 at 486 dpi, gesture navigation, punch-hole
 * camera, rounded corners).
 *
 * Robolectric reports no window insets on its own, so they are dispatched by hand. Without that the
 * assertions would pass on a layout that still draws under the navigation bar.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w442dp-h985dp-xxhdpi")
class ReaderMediaInsetsTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun `gallery transport and time bar sit above the navigation bar`() {
        composeRule.setContent {
            NextTheme(darkTheme = true) {
                Box(Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxSize().background(Color.Black).testTag(VIDEO_SURFACE))
                    ReaderMediaControls(
                        playing = true,
                        positionSeconds = 24.0,
                        durationSeconds = 96.0,
                        onPlayPause = {},
                        onSeek = {},
                        modifier = Modifier.align(Alignment.BottomCenter),
                    )
                }
            }
        }
        applySystemBars()

        val root = composeRule.onRoot().getUnclippedBoundsInRoot()
        val video = composeRule.onNodeWithTag(VIDEO_SURFACE).getUnclippedBoundsInRoot()
        val panel = composeRule.onNodeWithTag(ReaderMediaControlsTags.PANEL).getUnclippedBoundsInRoot()
        val buttons = composeRule.onNodeWithTag(ReaderMediaControlsTags.BUTTONS).getUnclippedBoundsInRoot()
        val timeBar = composeRule.onNodeWithTag(ReaderMediaControlsTags.TIME_BAR).getUnclippedBoundsInRoot()
        val safeBottom = (root.bottom - NAVIGATION_BAR).value
        val safeLeft = (root.left + HORIZONTAL_CUTOUT).value
        val safeRight = (root.right - HORIZONTAL_CUTOUT).value

        // The video may draw edge-to-edge behind the bars; only the controls must clear them.
        assertThat(video.top.value).isWithin(0.5f).of(root.top.value)
        assertThat(video.bottom.value).isWithin(0.5f).of(root.bottom.value)
        assertThat(timeBar.bottom.value).isWithin(0.5f).of(safeBottom)
        assertThat(buttons.bottom.value).isAtMost(safeBottom + 0.5f)
        assertThat(timeBar.top.value).isAtLeast(buttons.bottom.value)
        assertThat(timeBar.left.value).isAtLeast(safeLeft - 0.5f)
        assertThat(timeBar.right.value).isAtMost(safeRight + 0.5f)
        assertThat(buttons.left.value).isAtLeast(safeLeft - 0.5f)
        assertThat(buttons.right.value).isAtMost(safeRight + 0.5f)
        assertThat(panel.left.value).isAtLeast(safeLeft - 0.5f)
        assertThat(panel.right.value).isAtMost(safeRight + 0.5f)
    }

    @Test
    @Config(qualifiers = "w985dp-h442dp-land-xxhdpi")
    fun `gallery controls clear a landscape display cutout and the navigation bar`() {
        composeRule.setContent {
            NextTheme(darkTheme = true) {
                Box(Modifier.fillMaxSize()) {
                    ReaderMediaControls(
                        playing = false,
                        positionSeconds = 10.0,
                        durationSeconds = 60.0,
                        onPlayPause = {},
                        onSeek = {},
                        modifier = Modifier.align(Alignment.BottomCenter),
                    )
                }
            }
        }
        applySystemBars(navigation = LANDSCAPE_NAVIGATION_BAR, horizontal = HORIZONTAL_CUTOUT)

        val root = composeRule.onRoot().getUnclippedBoundsInRoot()
        val panel = composeRule.onNodeWithTag(ReaderMediaControlsTags.PANEL).getUnclippedBoundsInRoot()
        val timeBar = composeRule.onNodeWithTag(ReaderMediaControlsTags.TIME_BAR).getUnclippedBoundsInRoot()
        assertThat(panel.bottom.value).isAtMost((root.bottom - LANDSCAPE_NAVIGATION_BAR).value + 0.5f)
        assertThat(timeBar.bottom.value).isAtMost((root.bottom - LANDSCAPE_NAVIGATION_BAR).value + 0.5f)
        assertThat(panel.left.value).isAtLeast((root.left + HORIZONTAL_CUTOUT).value - 0.5f)
        assertThat(panel.right.value).isAtMost((root.right - HORIZONTAL_CUTOUT).value + 0.5f)
    }

    @Test
    fun `feed progress sits above the navigation bar`() {
        composeRule.setContent {
            NextTheme(darkTheme = true) {
                FeedPlaybackControls(
                    playing = true,
                    muted = false,
                    onPlayPause = {},
                    onMuteToggle = {},
                    progress = 0.4f,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        applySystemBars()

        val root = composeRule.onRoot().getUnclippedBoundsInRoot()
        val progress = composeRule.onNodeWithTag(FeedPlaybackTags.PROGRESS).getUnclippedBoundsInRoot()
        val mute = composeRule.onNodeWithTag(FeedPlaybackTags.MUTE).getUnclippedBoundsInRoot()
        assertThat(progress.bottom.value).isAtMost((root.bottom - NAVIGATION_BAR).value + 0.5f)
        assertThat(progress.left.value).isAtLeast((root.left + HORIZONTAL_CUTOUT).value - 0.5f)
        assertThat(progress.right.value).isAtMost((root.right - HORIZONTAL_CUTOUT).value + 0.5f)
        // The mute control already cleared the status bar; bottom padding must not lift it out of the
        // top safe area or push it into the navigation bar.
        assertThat(mute.top.value).isAtLeast(STATUS_BAR.value)
        assertThat(mute.bottom.value).isAtMost((root.bottom - NAVIGATION_BAR).value + 0.5f)
    }

    /**
     * Robolectric's window reports no insets, so they are dispatched by hand. This is the whole
     * point of the test: without it the assertions pass on a broken layout.
     */
    private fun applySystemBars(
        navigation: Dp = NAVIGATION_BAR,
        horizontal: Dp = HORIZONTAL_CUTOUT,
    ) {
        val decor = composeRule.activity.window.decorView
        val density = composeRule.density
        val bars =
            with(density) {
                Insets.of(
                    horizontal.roundToPx(),
                    STATUS_BAR.roundToPx(),
                    horizontal.roundToPx(),
                    navigation.roundToPx(),
                )
            }
        val cutout =
            with(density) {
                Insets.of(horizontal.roundToPx(), STATUS_BAR.roundToPx(), horizontal.roundToPx(), 0)
            }
        val insets =
            WindowInsetsCompat
                .Builder()
                .setInsets(WindowInsetsCompat.Type.systemBars(), bars)
                .setInsets(WindowInsetsCompat.Type.displayCutout(), cutout)
                .build()
        composeRule.runOnUiThread { ViewCompat.dispatchApplyWindowInsets(decor, insets) }
        composeRule.waitForIdle()
    }

    private companion object {
        const val VIDEO_SURFACE = "reader-media-video-surface"
        val STATUS_BAR = 52.dp
        val NAVIGATION_BAR = 48.dp
        val LANDSCAPE_NAVIGATION_BAR = 24.dp
        val HORIZONTAL_CUTOUT = 24.dp
    }
}
