package com.orbin.uinext

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.LayoutDirection
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.ParameterizedRobolectricTestRunner.Parameters
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Saved renders the supplied local records, without invented profile statistics or cover art. */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "en-rUS-w390dp-h844dp-mdpi")
class ReaderSavedScreenshotTest(
    private val theme: String,
) {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun phoneRecords() = capture("phone_390")

    @Test
    @Config(qualifiers = "en-rUS-w960dp-h800dp-mdpi")
    fun tabletRecords() = capture("tablet_960")

    @Test
    fun maximumTextRightToLeft() = capture("phone_390_rtl_font_200", fontScale = 2f, rtl = true)

    @Test
    fun emptySaved() = capture("phone_390_empty", empty = true)

    private fun capture(
        name: String,
        fontScale: Float = 1f,
        rtl: Boolean = false,
        empty: Boolean = false,
    ) {
        composeRule.setContent {
            NextTheme(
                darkTheme = theme != "light",
                amoled = theme == "amoled",
                fontScale = fontScale,
                platform = NextPlatform.IOS,
            ) {
                CompositionLocalProvider(
                    LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr,
                ) {
                    Surface(Modifier.fillMaxSize(), color = reader.background) {
                        ThreadCollectionScreen(
                            title = "Saved",
                            rows = if (empty) emptyList() else savedReaderRecords,
                            onOpen = {},
                            onDestination = {},
                            onSettings = {},
                        )
                    }
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(ReaderAppBarTags.BAR).assertIsDisplayed()
        if (empty) {
            composeRule.onNodeWithTag(ReaderStateTags.EMPTY).assertIsDisplayed()
        } else {
            composeRule.onNodeWithTag(ReaderSavedTags.LIST).assertIsDisplayed()
            composeRule.onNodeWithTag(ReaderSavedTags.row(savedReaderRecords[0].id)).assertIsDisplayed()
        }
        composeRule.onRoot().captureRoboImage("src/androidHostTest/screenshots/reader_saved_${theme}_$name.png")
    }

    companion object {
        @JvmStatic
        @Parameters(name = "{0}")
        fun themes(): List<Array<String>> = listOf("light", "dark", "amoled").map { arrayOf(it) }
    }
}

private val savedReaderRecords =
    listOf(
        ThreadCollectionRow(
            id = "fourchan/g/7",
            title = "A small home server and the software that runs it",
            source = "4chan · /g/",
            detail = "Saved for later reading",
            time = "12 Nov 2026, 10:45",
            threadNumber = "7",
            unread = 3,
        ),
        ThreadCollectionRow(
            id = "bbwchan/gen/7",
            title = "Weekend discussion",
            source = "BBWChan · /gen/",
            time = "12 Nov 2026, 10:30",
            threadNumber = "7",
            unread = 1,
        ),
        ThreadCollectionRow(
            id = "fourchan/3/21",
            title = "A study of shape and lighting",
            source = "4chan · /3/",
            detail = "Read through the last reply",
            time = "11 Nov 2026, 20:10",
            threadNumber = "21",
        ),
    )
