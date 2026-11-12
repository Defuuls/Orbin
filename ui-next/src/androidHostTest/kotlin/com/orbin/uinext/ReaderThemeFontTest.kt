package com.orbin.uinext

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.text.font.FontFamily
import com.orbin.uinext.tokens.LocalNextFontFamily
import com.orbin.uinext.tokens.NextType
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.ParameterizedRobolectricTestRunner.Parameters
import org.robolectric.annotation.Config

/** Check the installed theme, including screen styles outside the catalog components. */
@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(sdk = [35])
class ReaderThemeFontTest(
    private val platform: NextPlatform,
) {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun actualThemeUsesThePlatformFontForReaderAndMaterialText() {
        var installed: FontFamily? = null
        var screenText: FontFamily? = null
        var materialText: FontFamily? = null
        compose.setContent {
            NextTheme(platform = platform) {
                val family = LocalNextFontFamily.current
                val body = NextType.body.fontFamily
                val material = MaterialTheme.typography.bodyLarge.fontFamily
                SideEffect {
                    installed = family
                    screenText = body
                    materialText = material
                }
            }
        }
        compose.runOnIdle {
            assertEquals(FontFamily.Default, installed)
            assertEquals(FontFamily.Default, screenText)
            assertEquals(FontFamily.Default, materialText)
        }
    }

    companion object {
        @JvmStatic
        @Parameters(name = "{0}")
        fun platforms(): List<Array<NextPlatform>> = NextPlatform.entries.map { arrayOf(it) }
    }
}
