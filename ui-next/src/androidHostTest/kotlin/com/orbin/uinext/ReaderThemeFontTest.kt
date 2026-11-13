package com.orbin.uinext

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontSynthesis
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.sp
import com.orbin.uinext.tokens.LocalNextFontFamily
import com.orbin.uinext.tokens.OrbinType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.ParameterizedRobolectricTestRunner.Parameters
import org.robolectric.annotation.Config

/** The installed theme and real production text layouts must use the packaged reader family. */
@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "en-rUS-w411dp-h891dp-mdpi")
class ReaderThemeFontTest(
    private val platform: NextPlatform,
) {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun actualThemeBindsMaterialTextAndPreservesNestedFontScaling() {
        var installed: FontFamily? = null
        var screenText: FontFamily? = null
        var materialText: FontFamily? = null
        var systemScale = 0f
        var scaled = 0f
        var inheritedScale = 0f
        var brightnessOverrideScale = 0f
        var explicitScale = 0f
        var platformResolver: FontFamily.Resolver? = null
        var themedResolver: FontFamily.Resolver? = null
        compose.setContent {
            val originalScale = LocalDensity.current.fontScale
            val originalResolver = LocalFontFamilyResolver.current
            NextTheme(platform = platform, fontScale = 1.3f) {
                val family = LocalNextFontFamily.current
                val body = OrbinType.body.fontFamily
                val material = MaterialTheme.typography.bodyLarge.fontFamily
                val fontScale = LocalDensity.current.fontScale
                val resolver = LocalFontFamilyResolver.current
                SideEffect {
                    installed = family
                    screenText = body
                    materialText = material
                    systemScale = originalScale
                    scaled = fontScale
                    platformResolver = originalResolver
                    themedResolver = resolver
                }
                NextTheme {
                    val value = LocalDensity.current.fontScale
                    SideEffect { inheritedScale = value }
                }
                NextTheme(darkTheme = true) {
                    val value = LocalDensity.current.fontScale
                    SideEffect { brightnessOverrideScale = value }
                }
                NextTheme(fontScale = 2f) {
                    val value = LocalDensity.current.fontScale
                    SideEffect { explicitScale = value }
                }
            }
        }
        compose.runOnIdle {
            assertNotNull(installed)
            assertNotEquals(FontFamily.Default, installed)
            assertEquals(installed, screenText)
            assertEquals(installed, materialText)
            assertSame("The platform font resolver must remain intact", platformResolver, themedResolver)
            assertEquals(systemScale * 1.3f, scaled, 0.001f)
            assertEquals(scaled, inheritedScale, 0.001f)
            assertEquals(scaled, brightnessOverrideScale, 0.001f)
            assertEquals(systemScale * 2f, explicitScale, 0.001f)
        }
    }

    @Test
    fun readerFontBindingPreservesMetricsStyleAndExplicitOtherFamilies() {
        val requested = listOf(null, FontFamily.Default, FontFamily.SansSerif, FontFamily.Serif, FontFamily.Monospace)
        var installed: FontFamily? = null
        var bindings = emptyList<Pair<TextStyle, TextStyle>>()
        compose.setContent {
            NextTheme(platform = platform) {
                val family = LocalNextFontFamily.current
                val styles =
                    requested.map { font ->
                        val original =
                            TextStyle(
                                fontFamily = font,
                                fontSize = 15.sp,
                                lineHeight = 22.sp,
                                letterSpacing = 0.2.sp,
                                fontWeight = FontWeight.Medium,
                                fontStyle = FontStyle.Italic,
                                fontSynthesis = FontSynthesis.None,
                                color = Color(0xFF135724),
                                background = Color(0xFFDDEEFF),
                                textAlign = TextAlign.End,
                                textDirection = TextDirection.ContentOrRtl,
                                textDecoration = TextDecoration.Underline,
                            )
                        original to original.withReaderFont()
                    }
                SideEffect {
                    installed = family
                    bindings = styles
                }
            }
        }
        compose.runOnIdle {
            assertNotNull(installed)
            assertEquals(requested.size, bindings.size)
            bindings.forEachIndexed { index, (original, bound) ->
                val expected = if (index < 3) original.copy(fontFamily = installed) else original
                assertEquals("Only the default family may change for request ${requested[index]}", expected, bound)
            }
        }
    }

    @Test
    fun homeCatalogSettingsAndEditableFieldRenderTheInstalledFamily() {
        var screen by mutableIntStateOf(0)
        var installed: FontFamily? = null
        compose.setContent {
            NextTheme(platform = platform) {
                val family = LocalNextFontFamily.current
                SideEffect { installed = family }
                when (screen) {
                    0 ->
                        FeedScreen(
                            rows = listOf(fontRow("Home typeface fixture")),
                            onOpenBoards = {},
                            onOpenSearch = {},
                            onSettings = {},
                        )
                    1 ->
                        BoardScreen(
                            board = "/g/",
                            description = "Typeface catalog",
                            itemCount = 1,
                            rowAt = { fontRow("Catalog typeface fixture") },
                        )
                    2 ->
                        SettingsScreen(
                            groups =
                                listOf(
                                    "Playback" to
                                        listOf(
                                            SettingItem(
                                                "startMuted",
                                                "Settings typeface fixture",
                                                "On",
                                                checked = true,
                                            ),
                                        ),
                                ),
                            expandedId = "startMuted",
                            onBack = {},
                        )
                    else -> ReaderCatalogSearch(query = "Typeface query", onQueryChange = {}, onClose = {})
                }
            }
        }
        listOf("Home typeface fixture", "Catalog typeface fixture", "Settings typeface fixture").forEachIndexed {
            index,
            text,
            ->
            compose.runOnIdle { screen = index }
            val layout = compose.onNodeWithText(text, useUnmergedTree = true).textLayout()
            assertNotNull(installed)
            assertEquals(
                "The production $text must bind the bundled family",
                installed,
                layout.layoutInput.style.fontFamily,
            )
        }
        compose.runOnIdle { screen = 3 }
        val field = compose.onNodeWithTag("reader-catalog-query", useUnmergedTree = true).textLayout()
        assertEquals(
            "The actual editable search text must bind the bundled family",
            installed,
            field.layoutInput.style.fontFamily,
        )
    }

    private fun SemanticsNodeInteraction.textLayout(): TextLayoutResult {
        val layouts = mutableListOf<TextLayoutResult>()
        performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertEquals("The production text node must expose one measured layout", 1, layouts.size)
        return layouts.single()
    }

    private fun fontRow(subject: String) =
        FeedRow(
            subject = subject,
            board = "/g/",
            activity = "1m",
            replies = 12,
            media = 0,
            hasPreview = false,
            id = "fourchan/g/7",
            site = "4chan",
        )

    companion object {
        @JvmStatic
        @Parameters(name = "{0}")
        fun platforms(): List<Array<NextPlatform>> = NextPlatform.entries.map { arrayOf(it) }
    }
}
