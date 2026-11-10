package com.orbin.uinext

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Actual painted chrome must cover the full width, including its square corners. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xhdpi")
class NavigationAppearanceTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `immersive navigation has a full bleed black ground and offset center colors`() {
        installNavigation(immersive = true)
        val bar = composeRule.onNodeWithTag("navigation")
        bar.assertWidthIsEqualTo(390.dp).assertHeightIsEqualTo(52.dp)
        val pixels = bar.captureToImage().toPixelMap()
        assertThat(pixels[0, 0].toArgb()).isEqualTo(Color.Black.toArgb())
        assertThat(pixels[pixels.width - 1, 0].toArgb()).isEqualTo(Color.Black.toArgb())
        assertThat(pixels[0, pixels.height - 1].toArgb()).isEqualTo(Color.Black.toArgb())
        assertThat(pixels[pixels.width - 1, pixels.height - 1].toArgb()).isEqualTo(Color.Black.toArgb())
        val colors = mutableSetOf<Int>()
        for (y in 0 until pixels.height) {
            for (x in 0 until pixels.width) colors += pixels[x, y].toArgb()
        }
        assertThat(colors).containsAtLeast(0xFF25F4EE.toInt(), 0xFFFE2C55.toInt(), Color.White.toArgb())
    }

    @Test
    fun `light secondary navigation keeps a full bleed white ground`() {
        installNavigation(immersive = false)
        val bar = composeRule.onNodeWithTag("navigation")
        bar.assertWidthIsEqualTo(390.dp).assertHeightIsEqualTo(52.dp)
        val pixels = bar.captureToImage().toPixelMap()
        assertThat(pixels[0, 0].toArgb()).isEqualTo(Color.White.toArgb())
        assertThat(pixels[pixels.width - 1, 0].toArgb()).isEqualTo(Color.White.toArgb())
        assertThat(pixels[0, pixels.height - 1].toArgb()).isEqualTo(Color.White.toArgb())
        assertThat(pixels[pixels.width - 1, pixels.height - 1].toArgb()).isEqualTo(Color.White.toArgb())
    }

    private fun installNavigation(immersive: Boolean) {
        composeRule.setContent {
            NextTheme(darkTheme = false) {
                Box(
                    Modifier.size(390.dp, 160.dp).background(Color.Magenta),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    DestinationPill(
                        selected = NextDestination.FEED,
                        onSelect = {},
                        modifier = Modifier.testTag("navigation"),
                        immersive = immersive,
                    )
                }
            }
        }
    }
}
