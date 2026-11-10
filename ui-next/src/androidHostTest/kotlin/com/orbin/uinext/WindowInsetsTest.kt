package com.orbin.uinext

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
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

    @Test
    fun `immersive media is edge to edge while captions and navigation clear system bars`() {
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
        assertThat(media.top.value).isWithin(0.5f).of(root.top.value)
        assertThat(media.left.value).isWithin(0.5f).of(root.left.value)
        assertThat(media.right.value).isWithin(0.5f).of(root.right.value)
        // Media can extend behind the status bar; its caption must remain readable.
        val firstRow = composeRule.onNodeWithText("A thread that has to stay readable").getUnclippedBoundsInRoot()
        assertThat(firstRow.top.value).isAtLeast(STATUS_BAR.value)
        val home = composeRule.onNodeWithContentDescription("Home").getUnclippedBoundsInRoot()
        val saved = composeRule.onNodeWithContentDescription("Saved").getUnclippedBoundsInRoot()
        assertThat(home.left.value).isWithin(0.5f).of(root.left.value)
        assertThat(saved.right.value).isWithin(0.5f).of(root.right.value)
        assertThat(saved.bottom.value).isWithin(0.5f).of((root.bottom - NAVIGATION_BAR).value)
        assertThat(firstRow.bottom.value).isAtMost(saved.top.value)
    }

    @Test
    fun `navigation uses horizontal safe areas without adding capsule margins`() {
        composeRule.setContent {
            NextTheme {
                Box {
                    DestinationPill(NextDestination.FEED, {})
                }
            }
        }
        applySystemBars(horizontal = 24.dp)
        val root = composeRule.onRoot().getUnclippedBoundsInRoot()
        val home = composeRule.onNodeWithContentDescription("Home").getUnclippedBoundsInRoot()
        val saved = composeRule.onNodeWithContentDescription("Saved").getUnclippedBoundsInRoot()
        assertThat(home.left.value).isWithin(0.5f).of((root.left + 24.dp).value)
        assertThat(saved.right.value).isWithin(0.5f).of((root.right - 24.dp).value)
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
