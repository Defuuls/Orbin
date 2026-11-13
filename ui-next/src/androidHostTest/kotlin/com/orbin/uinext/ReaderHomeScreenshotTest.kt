package com.orbin.uinext

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
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

/**
 * The production Home screen and its real reader app bar, in every canonical theme.
 * Nine window/type/direction cases replace the retained full-screen Home references.
 * Local artwork and thread metadata keep these 27 captures independent of live feeds.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "en-rUS-w390dp-h844dp-mdpi")
class ReaderHomeScreenshotTest(
    private val theme: String,
) {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun phoneList() = capture("phone_390_list")

    @Test
    fun phoneGrid() = capture("phone_390_grid", mode = CatalogMode.GRID)

    @Test
    fun phoneCard() = capture("phone_390_card", mode = CatalogMode.CARD)

    @Test
    @Config(qualifiers = "en-rUS-w960dp-h800dp-mdpi")
    fun tabletList() = capture("tablet_960_list", wideWindow = true)

    @Test
    @Config(qualifiers = "en-rUS-w960dp-h800dp-mdpi")
    fun tabletGrid() = capture("tablet_960_grid", mode = CatalogMode.GRID, wideWindow = true)

    @Test
    fun phoneListAtLargeText() = capture("phone_390_list_font_130", fontScale = 1.3f)

    @Test
    fun phoneGridRightToLeftAtLargeText() =
        capture("phone_390_grid_rtl_font_130", mode = CatalogMode.GRID, fontScale = 1.3f, rtl = true)

    @Test
    fun phoneListRightToLeftAtMaximumText() = capture("phone_390_list_rtl_font_200", fontScale = 2f, rtl = true)

    @Test
    @Config(qualifiers = "en-rUS-w248dp-h600dp-mdpi")
    fun narrowGridAtMaximumText() = capture("narrow_248_grid_font_200", mode = CatalogMode.GRID, fontScale = 2f)

    private fun capture(
        name: String,
        mode: CatalogMode = CatalogMode.LIST,
        wideWindow: Boolean = false,
        fontScale: Float = 1f,
        rtl: Boolean = false,
    ) {
        val palette = canonicalNextPalette(theme != "light", theme == "amoled")
        val direction = if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr
        composeRule.setContent {
            NextTheme(
                darkTheme = palette.dark,
                amoled = palette.amoled,
                palette = palette,
                fontScale = fontScale,
                platform = NextPlatform.IOS,
            ) {
                CompositionLocalProvider(LocalLayoutDirection provides direction) {
                    Surface(Modifier.fillMaxSize(), color = reader.background) {
                        HomeReaderFixture(mode, wideWindow)
                    }
                }
            }
        }
        composeRule.waitForIdle()
        // A themed frame around the old pager must never satisfy the Home visual contract.
        composeRule.onNodeWithTag(ReaderFeedTags.GRID).assertIsDisplayed()
        composeRule.onNodeWithTag(TikTokFeedTags.PAGER).assertDoesNotExist()
        composeRule.onNodeWithTag(TikTokFeedTags.ACTIONS).assertDoesNotExist()
        composeRule.onNodeWithTag(ReaderFeedTags.FOLLOWING).assertDoesNotExist()
        composeRule.onNodeWithTag(ReaderFeedTags.FOR_YOU).assertDoesNotExist()
        composeRule.onRoot().captureRoboImage("src/androidHostTest/screenshots/reader_home_${theme}_$name.png")
    }

    companion object {
        @JvmStatic
        @Parameters(name = "{0}")
        fun themes(): List<Array<String>> = listOf("light", "dark", "amoled").map { arrayOf(it) }
    }
}

@Composable
private fun HomeReaderFixture(
    mode: CatalogMode,
    wideWindow: Boolean,
) {
    FeedScreen(
        rows = homeReaderRows,
        mode = mode,
        onModeChange = {},
        wideWindow = wideWindow,
        sourceLabels = listOf("All sites", "4chan", "BBWChan"),
        onSelectSource = {},
        thumbnail = { row, modifier -> HomeReaderArtwork(row.threadNumber.lastOrNull() == '1', modifier) },
        onOpenRow = {},
        onOpenMedia = {},
        onOpenReplies = {},
        repliesOpenComments = false,
        onOpenBoard = {},
        onOpenBoards = {},
        onOpenDownloads = {},
        downloadBadge = 2,
        onOpenSearch = {},
        onOpenSaved = {},
        onOpenWatched = {},
        onSettings = {},
        onToggleWatch = {},
        onToggleSaved = {},
        onShareRow = {},
    )
}

/** Square local poster artwork, so the preview never calls a provider or a video player. */
@Composable
private fun HomeReaderArtwork(
    warm: Boolean,
    modifier: Modifier,
) {
    Canvas(modifier) {
        drawRect(if (warm) Color(0xFF42647A) else Color(0xFF50746D))
        drawCircle(
            Color(0xFFE3B58D),
            radius = size.minDimension * 0.18f,
            center = Offset(size.width * 0.68f, size.height * 0.28f),
        )
        val ridge =
            Path().apply {
                moveTo(0f, size.height * 0.72f)
                lineTo(size.width * 0.28f, size.height * 0.38f)
                lineTo(size.width * 0.56f, size.height * 0.68f)
                lineTo(size.width * 0.8f, size.height * 0.46f)
                lineTo(size.width, size.height * 0.7f)
                lineTo(size.width, size.height)
                lineTo(0f, size.height)
                close()
            }
        drawPath(ridge, Color(0xFF21444A))
    }
}

private val homeReaderRows =
    listOf(
        FeedRow(
            subject = "A quiet place to pause",
            board = "/p/",
            boardTitle = "Photography",
            activity = "8m",
            replies = 128,
            media = 42,
            id = "fourchan/p/482911",
            site = "4chan",
            mediaLabel = "WEBM",
            excerpt = "> Original local artwork\nShare a view that made your day.",
            watched = true,
            bookmarked = true,
            unread = 5,
        ),
        FeedRow(
            subject = "Home server discussion",
            board = "/g/",
            boardTitle = "Technology",
            activity = "12m",
            replies = 218,
            media = 31,
            id = "fourchan/g/482922",
            site = "4chan",
            mediaLabel = "MP4",
            excerpt = "Small builds, quiet fans and useful home projects.",
            read = true,
        ),
        FeedRow(
            subject = "Weekly discussion",
            board = "/gen/",
            boardTitle = "General Discussion",
            activity = "25m",
            replies = 24,
            media = 6,
            id = "bbwchan/gen/230133",
            site = "BBWChan",
            mediaLabel = "WEBM",
            excerpt = "A fictional discussion for shared layout testing.",
            imageSpoiler = true,
        ),
        FeedRow(
            subject = "Reading list for a quiet weekend",
            board = "/lit/",
            boardTitle = "Literature",
            activity = "1h",
            replies = 47,
            media = 0,
            hasPreview = false,
            id = "fourchan/lit/482944",
            site = "4chan",
            excerpt = "Recommendations, short reviews and current reading lists.",
        ),
    )
