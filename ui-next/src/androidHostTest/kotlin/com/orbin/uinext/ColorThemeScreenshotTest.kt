package com.orbin.uinext

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.ParameterizedRobolectricTestRunner.Parameters
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Real shared screens with deterministic content; not device or persistence coverage. */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xhdpi")
class ColorThemeScreenshotTest(
    private val colorName: String,
    private val mode: String,
) {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun feed() =
        capture("feed") {
            FeedScreen(
                rows =
                    listOf(
                        FeedRow("Home server discussion", "/g/", "4m", 218, 31, id = "first"),
                        FeedRow("What did you cook this week?", "/ck/", "31m", 47, 22, read = true, id = "read"),
                        FeedRow("Film photography and grain", "/p/", "1h", 156, 140, id = "photo"),
                        FeedRow("Reading list", "/lit/", "3h", 28, 4, hasPreview = false, id = "text"),
                    ),
                subtitle = "4 threads across 4 boards",
            )
        }

    @Test
    fun thread() =
        capture("thread") {
            ThreadScreen(
                subject = "Home server discussion",
                board = "/g/",
                watching = true,
                posts =
                    listOf(
                        Post("No.100", "14:02", "A small server with low power usage.", replies = 2),
                        Post("No.101", "14:09", "Which board are you using?", depth = 1),
                        Post("No.102", "14:15", "A board with a proper PCIe lane.", depth = 2),
                        Post("No.103", "14:22", "Spoiler text", spoiler = true),
                    ),
            )
        }

    @Test
    fun settings() =
        capture("settings") {
            SettingsScreen(
                groups =
                    listOf(
                        "" to
                            listOf(
                                SettingItem(
                                    id = "colorTheme",
                                    label = "Color theme",
                                    value = colorName.lowercase().replaceFirstChar(Char::uppercase),
                                    kind = SettingKind.CHOICE,
                                    options = COLOR_LABELS,
                                    selected = NextColorTheme.valueOf(colorName).ordinal,
                                ),
                                SettingItem(
                                    id = "themeMode",
                                    label = "Theme",
                                    value = if (mode == "light") "Light" else "Dark",
                                    kind = SettingKind.CHOICE,
                                    options = listOf("System", "Light", "Dark"),
                                    selected = if (mode == "light") 1 else 2,
                                ),
                                SettingItem("amoled", "True black", if (mode == "amoled") "On" else "Off"),
                            ),
                        "" to
                            listOf(
                                SettingItem(
                                    id = "clearActivity",
                                    label = "Clear local activity",
                                    value = "Delete",
                                    kind = SettingKind.ACTION,
                                    hint = "Deletes browsing history on this device.",
                                    destructive = true,
                                ),
                            ),
                    ),
                expandedId = "colorTheme",
            )
        }

    private fun capture(
        screen: String,
        content: @Composable () -> Unit,
    ) {
        val palette = colorThemePalette(NextColorTheme.valueOf(colorName), mode != "light", mode == "amoled")
        composeRule.setContent {
            NextTheme(darkTheme = palette.dark, amoled = palette.amoled, palette = palette) {
                Surface(modifier = Modifier.size(411.dp, 891.dp), color = palette.background, content = content)
            }
        }
        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage(
            "src/androidHostTest/screenshots/theme_${colorName.lowercase()}_${mode}_$screen.png",
        )
    }

    companion object {
        private val COLOR_LABELS =
            listOf(
                "Orbin (green)",
                "Blue",
                "Red",
                "White",
                "Black",
                "Purple",
                "Orange",
                "Teal",
            )

        @JvmStatic
        @Parameters(name = "{0}-{1}")
        fun themes(): List<Array<String>> =
            NextColorTheme.entries.flatMap { theme ->
                listOf("light", "dark", "amoled").map { mode -> arrayOf(theme.name, mode) }
            }
    }
}
