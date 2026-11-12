package com.orbin.uinext

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import com.orbin.uinext.tokens.ReaderLightColors
import com.orbin.uinext.tokens.ReaderSize
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The fixed app bar uses reader tokens, and Home's themed content starts below it. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xhdpi")
class NavigationAppearanceTest {
    @get:Rule
    val composeRule = createComposeRule()

    /** Replaces the immersive bottom-bar/offset-center-colors test with the approved Home frame. */
    @Test
    fun `Home has a full width reader app bar above themed content`() {
        installNavigation(immersive = false)
        val bar = composeRule.onNodeWithTag(ReaderAppBarTags.BAR)
        bar.assertWidthIsEqualTo(390.dp).assertHeightIsEqualTo(ReaderSize.appBar)
        val pixels = bar.captureToImage().toPixelMap()
        assertThat(pixels[0, 0].toArgb()).isEqualTo(ReaderLightColors.appBar.toArgb())
        assertThat(pixels[pixels.width - 1, 0].toArgb()).isEqualTo(ReaderLightColors.appBar.toArgb())
        assertThat(pixels[0, pixels.height - 1].toArgb()).isEqualTo(ReaderLightColors.appBar.toArgb())
        val content = composeRule.onNodeWithTag("navigation-content")
        val barBounds = bar.getUnclippedBoundsInRoot()
        assertThat(content.getUnclippedBoundsInRoot().top.value).isWithin(0.5f).of(barBounds.bottom.value)
        val contentPixels = content.captureToImage().toPixelMap()
        assertThat(contentPixels[0, 0].toArgb()).isEqualTo(ReaderLightColors.background.toArgb())
    }

    /** Replaces the white secondary bottom-bar test with a token-based app bar and content ground. */
    @Test
    fun `secondary reader frame has a full width app bar and token background`() {
        installNavigation(immersive = false, child = true)
        val bar = composeRule.onNodeWithTag(ReaderAppBarTags.BAR)
        bar.assertWidthIsEqualTo(390.dp).assertHeightIsEqualTo(ReaderSize.appBar)
        val pixels = bar.captureToImage().toPixelMap()
        assertThat(pixels[0, 0].toArgb()).isEqualTo(ReaderLightColors.appBar.toArgb())
        assertThat(pixels[pixels.width - 1, pixels.height - 1].toArgb()).isEqualTo(ReaderLightColors.appBar.toArgb())
        val contentPixels = composeRule.onNodeWithTag("navigation-content").captureToImage().toPixelMap()
        assertThat(contentPixels[0, 0].toArgb()).isEqualTo(ReaderLightColors.background.toArgb())
        composeRule.onNodeWithContentDescription("Back").assertExists()
        composeRule.onNodeWithContentDescription("Menu").assertDoesNotExist()
    }

    @Test
    fun `toolbar retains Clover navigation action and overflow hit geometry`() {
        composeRule.setContent {
            NextTheme(darkTheme = false) {
                ReaderAppBar(
                    title = "Technology",
                    navigation = ReaderNavigation.BACK,
                    onNavigation = {},
                    actions =
                        listOf(
                            ReaderAction(ReaderIcons.Refresh, "Reload", {}),
                            ReaderAction(ReaderIcons.Search, "Search", {}),
                        ),
                    overflow = listOf(ReaderMenuItem("View as grid", onClick = {})),
                )
            }
        }
        composeRule.onNodeWithContentDescription("Back").assertWidthIsEqualTo(56.dp).assertHeightIsEqualTo(56.dp)
        composeRule.onNodeWithContentDescription("Reload").assertWidthIsEqualTo(50.dp).assertHeightIsEqualTo(56.dp)
        composeRule.onNodeWithContentDescription("Search").assertWidthIsEqualTo(50.dp).assertHeightIsEqualTo(56.dp)
        composeRule
            .onNodeWithContentDescription(
                "More options",
            ).assertWidthIsEqualTo(44.dp)
            .assertHeightIsEqualTo(56.dp)
        assertThat(
            composeRule
                .onNodeWithTag(ReaderAppBarTags.TITLE)
                .getUnclippedBoundsInRoot()
                .left.value,
        ).isWithin(0.5f)
            .of(72f)
    }

    @Test
    fun `drawer is capped at the maximum token width`() {
        installNavigation(immersive = false, width = 411.dp)
        composeRule.onNodeWithContentDescription("Menu").performClick()
        composeRule.onNodeWithTag(ReaderDrawerTags.DRAWER).assertWidthIsEqualTo(336.dp)
    }

    @Test
    fun `a phone drawer leaves the Clover 56dp strip when that is narrower than the cap`() {
        installNavigation(immersive = false, width = 390.dp)
        composeRule.onNodeWithContentDescription("Menu").performClick()
        composeRule.onNodeWithTag(ReaderDrawerTags.DRAWER).assertWidthIsEqualTo(334.dp)
    }

    @Test
    fun `drawer content starts with Clover eight dp vertical padding`() {
        composeRule.setContent { NextTheme { ReaderDrawer(NextDestination.FEED, {}) } }
        val home =
            composeRule
                .onNodeWithTag(
                    ReaderDrawerTags.ENTRY + NextDestination.FEED.name,
                ).getUnclippedBoundsInRoot()
        assertThat(home.top.value).isWithin(0.5f).of(8f)
    }

    @Test
    fun `narrow drawer leaves one app bar width visible`() {
        installNavigation(immersive = false, width = 320.dp)
        composeRule.onNodeWithContentDescription("Menu").performClick()
        composeRule.onNodeWithTag(ReaderDrawerTags.DRAWER).assertWidthIsEqualTo(320.dp - ReaderSize.appBar)
    }

    private fun installNavigation(
        immersive: Boolean,
        child: Boolean = false,
        width: Dp = 390.dp,
    ) {
        composeRule.setContent {
            NextTheme(darkTheme = false) {
                Box(Modifier.size(width, 600.dp)) {
                    ReaderScaffold(
                        title = if (child) "Downloads" else "Home",
                        destination = if (child) null else NextDestination.FEED,
                        onDestination = if (child) null else { _ -> },
                        onBack = if (child) ({}) else null,
                        immersive = immersive,
                    ) {
                        Box(
                            Modifier
                                .testTag("navigation-content")
                                .fillMaxSize()
                                .background(if (immersive) Color.Black else ReaderLightColors.background),
                        )
                    }
                }
            }
        }
    }
}
