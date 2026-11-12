package com.orbin.uinext

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.common.truth.Truth.assertThat
import com.orbin.uinext.tokens.ReaderAmoledColors
import com.orbin.uinext.tokens.ReaderLightColors
import com.orbin.uinext.tokens.ReaderSize
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Media fills the edge-to-edge viewport while overlay controls and navigation respect safe areas.
 * These screens keep actions and captions out of the status bar and gesture handle. That went wrong
 * once already — every screen was cut off at both ends on a device with real insets — and the
 * screenshot goldens could not see it, because Robolectric renders with no system bars at all
 * unless a test puts them there. This is the test that puts them there.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xhdpi")
class WindowInsetsTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    /** Replaces `immersive media is edge to edge while captions and navigation clear system bars`. */
    @Test
    fun `Home media starts below the app bar and captions clear the bottom system inset`() {
        composeRule.setContent {
            NextTheme {
                FeedScreen(
                    rows = listOf(FeedRow("A thread that has to stay readable", "/g/", "4m", 12, 3)),
                    subtitle = "1 thread",
                    onOpenBoards = {},
                    onOpenWatched = {},
                    onOpenSearch = {},
                    onOpenSaved = {},
                    thumbnail = { _, modifier ->
                        Box(modifier.testTag("feed-media").background(Color.Green))
                    },
                )
            }
        }
        applySystemBars()

        val root = composeRule.onRoot().getUnclippedBoundsInRoot()
        val media = composeRule.onNodeWithTag("feed-media").getUnclippedBoundsInRoot()
        val bar = composeRule.onNodeWithTag(ReaderAppBarTags.BAR).getUnclippedBoundsInRoot()
        assertThat(media.top.value).isWithin(0.5f).of(bar.bottom.value)
        assertThat((bar.bottom - bar.top).value).isWithin(0.5f).of((STATUS_BAR + ReaderSize.appBar).value)
        assertThat(media.left.value).isWithin(0.5f).of(root.left.value)
        assertThat(media.right.value).isWithin(0.5f).of(root.right.value)
        // Media can extend behind the status bar; its caption must remain readable.
        val firstRow = composeRule.onNodeWithText("A thread that has to stay readable").getUnclippedBoundsInRoot()
        assertThat(firstRow.top.value).isAtLeast(STATUS_BAR.value)
        assertThat(media.bottom.value).isWithin(0.5f).of((root.bottom - NAVIGATION_BAR).value)
        assertThat(firstRow.bottom.value).isAtMost((root.bottom - NAVIGATION_BAR).value)
    }

    /** Replaces `navigation uses horizontal safe areas without adding capsule margins`. */
    @Test
    fun `the app bar fills the width while its controls respect horizontal safe areas`() {
        composeRule.setContent {
            NextTheme {
                Box {
                    ReaderScaffold("Home", destination = NextDestination.FEED, onDestination = {}) {}
                }
            }
        }
        applySystemBars(horizontal = 24.dp)
        val root = composeRule.onRoot().getUnclippedBoundsInRoot()
        val bar = composeRule.onNodeWithTag(ReaderAppBarTags.BAR).getUnclippedBoundsInRoot()
        val menu = composeRule.onNodeWithContentDescription("Menu").getUnclippedBoundsInRoot()
        assertThat(bar.left.value).isWithin(0.5f).of(root.left.value)
        assertThat(bar.right.value).isWithin(0.5f).of(root.right.value)
        assertThat(menu.left.value).isAtLeast((root.left + 24.dp).value)
        assertThat(menu.right.value).isAtMost((root.right - 24.dp).value)
    }

    @Test
    fun `settings content clears the status bar`() {
        composeRule.setContent {
            NextTheme {
                // Without the rail, "Settings" names only the title, so the assertion can be made
                // against the title itself rather than against something further down the screen.
                SettingsScreen(
                    groups = listOf("Content" to listOf(SettingItem("t", "Threads per board", "12"))),
                    showRail = false,
                )
            }
        }
        applySystemBars()

        val title = composeRule.onNodeWithText("Settings and privacy").getUnclippedBoundsInRoot()
        assertThat(title.top.value).isAtLeast(STATUS_BAR.value)
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun `an open drawer keeps the reader app bar ground behind light status icons`() {
        composeRule.setContent {
            NextTheme(darkTheme = false) {
                ReaderScaffold("Boards", destination = NextDestination.BOARDS, onDestination = {}) {}
            }
        }
        applySystemBars()
        composeRule.onNodeWithContentDescription("Menu").performClick()
        val drawer = composeRule.onNodeWithTag(ReaderDrawerTags.DRAWER)
        drawer.assertWidthIsEqualTo(ReaderSize.drawerMax)
        val pixels = drawer.captureToImage().toPixelMap()
        val topInsetPx = with(composeRule.density) { STATUS_BAR.roundToPx() }
        assertThat(pixels[0, 0].toArgb()).isEqualTo(ReaderLightColors.appBar.toArgb())
        assertThat(pixels[pixels.width - 1, topInsetPx - 1].toArgb()).isEqualTo(ReaderLightColors.appBar.toArgb())
        // Top is consumed by the sheet; the first entry starts immediately below it.
        assertThat(pixels[0, topInsetPx + 1].toArgb()).isEqualTo(ReaderLightColors.background.toArgb())
        assertThat(pixels[0, pixels.height - 1].toArgb()).isEqualTo(ReaderLightColors.background.toArgb())
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun `the Home drawer keeps a black bottom system inset behind light navigation icons`() {
        composeRule.setContent {
            NextTheme(darkTheme = false) {
                ReaderScaffold("Home", destination = NextDestination.FEED, onDestination = {}, immersive = true) {}
            }
        }
        applySystemBars()
        composeRule.onNodeWithContentDescription("Menu").performClick()
        val pixels = composeRule.onNodeWithTag(ReaderDrawerTags.DRAWER).captureToImage().toPixelMap()
        val bottomInsetPx = with(composeRule.density) { NAVIGATION_BAR.roundToPx() }
        assertThat(pixels[0, pixels.height - 1].toArgb()).isEqualTo(ReaderAmoledColors.background.toArgb())
        assertThat(
            pixels[0, pixels.height - bottomInsetPx - 1].toArgb(),
        ).isEqualTo(ReaderLightColors.background.toArgb())
    }

    /**
     * Robolectric's window reports no insets, so they are dispatched by hand. This is the whole
     * point of the test: without it the assertions pass on a broken layout, which is exactly how
     * the goldens missed this.
     */
    private fun applySystemBars(horizontal: androidx.compose.ui.unit.Dp = 0.dp) {
        val decor = composeRule.activity.window.decorView
        val density = composeRule.density
        val bars =
            with(density) {
                Insets.of(
                    horizontal.roundToPx(),
                    STATUS_BAR.roundToPx(),
                    horizontal.roundToPx(),
                    NAVIGATION_BAR.roundToPx(),
                )
            }
        val insets =
            WindowInsetsCompat.Builder().setInsets(WindowInsetsCompat.Type.systemBars(), bars).build()
        composeRule.runOnUiThread { ViewCompat.dispatchApplyWindowInsets(decor, insets) }
        composeRule.waitForIdle()
    }

    private companion object {
        val STATUS_BAR = 48.dp
        val NAVIGATION_BAR = 32.dp
    }
}
