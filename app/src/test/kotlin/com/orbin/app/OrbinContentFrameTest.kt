package com.orbin.app

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.google.common.truth.Truth.assertThat
import com.orbin.uinext.NextDestination
import com.orbin.uinext.NextTheme
import com.orbin.uinext.OnboardingBoards
import com.orbin.uinext.OnboardingScreen
import com.orbin.uinext.ReaderAppBarTags
import com.orbin.uinext.ReaderDrawerTags
import com.orbin.uinext.ReaderScaffold
import com.orbin.uinext.tokens.NextMotion
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35], qualifiers = "w390dp-h844dp-mdpi")
class OrbinContentFrameTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun systemIconsFollowCoralBrightnessAndRestoreThePaletteAfterBlackMedia() {
        var dark by mutableStateOf(false)
        var amoled by mutableStateOf(false)
        var immersive by mutableStateOf(false)
        var readerBarVisible by mutableStateOf(true)
        composeRule.setContent {
            NextTheme(darkTheme = dark, amoled = amoled) {
                SystemBarAppearance(immersive = immersive, readerBarVisible = readerBarVisible)
            }
        }

        fun assertDarkIcons(expected: Boolean) {
            composeRule.runOnIdle {
                val window = composeRule.activity.window
                val controller = WindowCompat.getInsetsController(window, window.decorView)
                assertThat(controller.isAppearanceLightStatusBars).isEqualTo(expected)
                assertThat(controller.isAppearanceLightNavigationBars).isEqualTo(expected)
            }
        }

        // Exercise the reader flag that previously forced white icons over Coral's white bar.
        assertDarkIcons(true)
        composeRule.runOnIdle {
            readerBarVisible = false
            immersive = true
        }
        assertDarkIcons(false)
        composeRule.runOnIdle { immersive = false }
        assertDarkIcons(true)
        composeRule.runOnIdle { readerBarVisible = true }
        assertDarkIcons(true)
        composeRule.runOnIdle { dark = true }
        assertDarkIcons(false)
        composeRule.runOnIdle { amoled = true }
        assertDarkIcons(false)
        composeRule.runOnIdle {
            readerBarVisible = false
            immersive = true
        }
        assertDarkIcons(false)
        composeRule.runOnIdle { immersive = false }
        assertDarkIcons(false)
        composeRule.runOnIdle {
            dark = false
            amoled = false
        }
        assertDarkIcons(true)
    }

    @Test
    fun offlineOnboardingHeadingStartsBelowTheBannerWithoutSystemInsets() {
        composeRule.setContent {
            NextTheme {
                OrbinContentFrame(isOnline = false) {
                    OnboardingScreen(
                        boards = OnboardingBoards.Ready(emptyList()),
                        following = emptySet(),
                        sites = listOf("4chan"),
                        selectedSite = 0,
                        onSelectSite = {},
                        onFollow = { _, _ -> },
                        onRetry = {},
                        onStart = {},
                    )
                }
            }
        }
        applySystemBars(0.dp)

        val banner = composeRule.onNodeWithText("You're offline").getUnclippedBoundsInRoot()
        val heading = composeRule.onNodeWithText("Welcome to Orbin").getUnclippedBoundsInRoot()
        assertThat(banner.bottom.value).isAtMost(heading.top.value)
    }

    @Test
    fun connectivityChangesKeepMenuReachableAndRestoreTheOriginalSystemInset() {
        var online by mutableStateOf(true)
        composeRule.setContent {
            NextTheme {
                OrbinContentFrame(isOnline = online) {
                    ReaderScaffold("Home", destination = NextDestination.FEED, onDestination = {}) {}
                }
            }
        }
        val menu = composeRule.onNodeWithContentDescription("Menu")
        val bar = composeRule.onNodeWithTag(ReaderAppBarTags.BAR)
        applySystemBars(0.dp)
        val zeroInsetMenu = menu.getUnclippedBoundsInRoot()
        val zeroInsetBar = bar.getUnclippedBoundsInRoot()
        val toolbarMenuOffset = zeroInsetMenu.top - zeroInsetBar.top
        applySystemBars(52.dp)
        val originalMenu = menu.getUnclippedBoundsInRoot()
        val originalBar = bar.getUnclippedBoundsInRoot()
        assertThat(originalMenu.top.value).isWithin(0.5f).of(52f)

        composeRule.runOnIdle { online = false }

        val banner = composeRule.onNodeWithTag(OFFLINE_BANNER_TAG).getUnclippedBoundsInRoot()
        val offlineBar = bar.getUnclippedBoundsInRoot()
        val offlineMenu = menu.getUnclippedBoundsInRoot()
        assertThat(banner.bottom.value).isAtMost(offlineMenu.top.value)
        assertThat(offlineBar.top.value).isWithin(0.5f).of(banner.bottom.value)
        assertThat((offlineBar.bottom - offlineBar.top).value)
            .isWithin(0.5f)
            .of((zeroInsetBar.bottom - zeroInsetBar.top).value)
        assertThat((offlineMenu.top - offlineBar.top).value).isWithin(0.5f).of(toolbarMenuOffset.value)

        composeRule.runOnIdle { online = true }

        composeRule.onNodeWithText("You're offline").assertDoesNotExist()
        assertThat(menu.getUnclippedBoundsInRoot().top.value).isWithin(0.5f).of(originalMenu.top.value)
        assertThat(bar.getUnclippedBoundsInRoot().bottom.value).isWithin(0.5f).of(originalBar.bottom.value)

        composeRule.runOnIdle { online = false }

        val secondBanner = composeRule.onNodeWithTag(OFFLINE_BANNER_TAG).getUnclippedBoundsInRoot()
        val secondBar = bar.getUnclippedBoundsInRoot()
        assertThat(secondBar.top.value).isWithin(0.5f).of(secondBanner.bottom.value)
        assertThat((menu.getUnclippedBoundsInRoot().top - secondBar.top).value)
            .isWithin(0.5f)
            .of(toolbarMenuOffset.value)
        menu.performTouchInput { click() }
        composeRule.onNodeWithTag(ReaderDrawerTags.DRAWER).assertIsDisplayed()
    }

    @Test
    fun enteringAndLeavingOfflineKeepsMenuBelowTheVisibleBannerAndSystemInset() {
        var online by mutableStateOf(false)
        composeRule.setContent {
            NextTheme {
                OrbinContentFrame(isOnline = online) {
                    ReaderScaffold("Home", destination = NextDestination.FEED, onDestination = {}) {}
                }
            }
        }
        applySystemBars(52.dp)
        val menu = composeRule.onNodeWithContentDescription("Menu")
        val banner = composeRule.onNodeWithTag(OFFLINE_BANNER_TAG)
        val fullBannerHeight = banner.fetchSemanticsNode().boundsInRoot.height
        composeRule.onNodeWithText("You're offline").assertIsDisplayed()
        composeRule.runOnIdle { online = true }
        val baselineTop = menu.fetchSemanticsNode().boundsInRoot.top
        val safeTop = with(composeRule.density) { 52.dp.toPx() }

        fun advanceToMiddleOfTransition() {
            // Compose's clock advances animation frames; Android measure/draw must also finish
            // before the next frame so the visibility animation has its measured target height.
            repeat(2) {
                composeRule.mainClock.advanceTimeByFrame()
                composeRule.waitForIdle()
            }
            composeRule.mainClock.advanceTimeBy(NextMotion.CHROME_MS / 2L)
            composeRule.waitForIdle()
        }

        composeRule.mainClock.autoAdvance = false
        try {
            composeRule.runOnIdle { online = false }
            advanceToMiddleOfTransition()

            // Clipped bounds describe the part actually visible inside the animated banner height.
            val enteringBanner = banner.fetchSemanticsNode().boundsInRoot
            val enteringMenu = menu.fetchSemanticsNode().boundsInRoot
            assertThat(enteringBanner.height).isGreaterThan(0f)
            assertThat(enteringBanner.height).isLessThan(fullBannerHeight)
            assertThat(enteringMenu.top).isAtLeast(safeTop)
            assertThat(enteringBanner.bottom).isAtMost(enteringMenu.top + 0.5f)

            composeRule.mainClock.advanceTimeBy(NextMotion.CHROME_MS.toLong())
            composeRule.waitForIdle()
            composeRule.runOnIdle { online = true }
            advanceToMiddleOfTransition()

            val exitingBanner = banner.fetchSemanticsNode().boundsInRoot
            val exitingMenu = menu.fetchSemanticsNode().boundsInRoot
            assertThat(exitingBanner.height).isGreaterThan(0f)
            assertThat(exitingBanner.height).isLessThan(fullBannerHeight)
            assertThat(exitingMenu.top).isAtLeast(safeTop)
            assertThat(exitingBanner.bottom).isAtMost(exitingMenu.top + 0.5f)

            composeRule.mainClock.advanceTimeBy(NextMotion.CHROME_MS.toLong())
            composeRule.waitForIdle()
            composeRule.onNodeWithText("You're offline").assertDoesNotExist()
            assertThat(menu.fetchSemanticsNode().boundsInRoot.top).isWithin(0.5f).of(baselineTop)
        } finally {
            composeRule.mainClock.autoAdvance = true
        }
    }

    /** Robolectric needs explicit insets to exercise the real safe-drawing padding. */
    private fun applySystemBars(status: Dp) {
        val bars = with(composeRule.density) { Insets.of(0, status.roundToPx(), 0, 0) }
        val insets =
            WindowInsetsCompat
                .Builder()
                .setInsets(WindowInsetsCompat.Type.systemBars(), bars)
                .setInsets(WindowInsetsCompat.Type.displayCutout(), bars)
                .build()
        composeRule.runOnUiThread {
            ViewCompat.dispatchApplyWindowInsets(composeRule.activity.window.decorView, insets)
        }
        composeRule.waitForIdle()
    }
}
