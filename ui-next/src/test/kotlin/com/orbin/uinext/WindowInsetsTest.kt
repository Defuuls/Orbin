package com.orbin.uinext

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
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
 * The interface draws edge to edge with no top bar and no bottom navigation bar, so nothing but
 * these screens is keeping content out of the status bar and the gesture handle. That went wrong
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
    fun `feed content clears the status bar and the rail clears the navigation bar`() {
        composeRule.setContent {
            NextTheme {
                FeedScreen(
                    rows = listOf(FeedRow("A thread that has to stay readable", "/g/", "4m", 12, 3)),
                    subtitle = "1 thread",
                )
            }
        }
        applySystemBars()

        val root = composeRule.onRoot().getUnclippedBoundsInRoot()
        // The first row is below the status bar rather than under the clock.
        val firstRow = composeRule.onNodeWithText("A thread that has to stay readable").getUnclippedBoundsInRoot()
        assertThat(firstRow.top.value).isAtLeast(STATUS_BAR.value)
        // The rail's affordance is above the gesture handle rather than behind it.
        val search = composeRule.onNodeWithText("Search").getUnclippedBoundsInRoot()
        assertThat(search.bottom.value).isAtMost((root.bottom - NAVIGATION_BAR).value)
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

        val title = composeRule.onNodeWithText("Settings").getUnclippedBoundsInRoot()
        assertThat(title.top.value).isAtLeast(STATUS_BAR.value)
    }

    /**
     * Robolectric's window reports no insets, so they are dispatched by hand. This is the whole
     * point of the test: without it the assertions pass on a broken layout, which is exactly how
     * the goldens missed this.
     */
    private fun applySystemBars() {
        val decor = composeRule.activity.window.decorView
        val density = composeRule.density
        val bars =
            with(density) {
                Insets.of(0, STATUS_BAR.roundToPx(), 0, NAVIGATION_BAR.roundToPx())
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
