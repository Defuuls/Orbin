package com.orbin.uinext

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.ParameterizedRobolectricTestRunner.Parameters
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Forty-eight real reader captures: sixteen window/layout cases in three themes.
 * Method qualifiers set the actual window; no oversized child is cropped to a phone root.
 * All text and local topic artwork are fixed. These fixtures do not exercise provider requests.
 * Bodies here are plain layout samples; :core:reader's ReaderRichThreadScreenshotTest captures
 * production prepared quote, link and spoiler rendering in all three themes.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "en-rUS-w390dp-h844dp-mdpi")
class ReaderCompletionScreenshotTest(
    private val theme: String,
) {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun phoneCatalogList() = capture("phone_390_catalog_list") { CompletionCatalog() }

    @Test
    fun phoneCatalogGrid() = capture("phone_390_catalog_grid") { CompletionCatalog(mode = CatalogMode.GRID) }

    @Test
    @Config(qualifiers = "en-rUS-w411dp-h891dp-mdpi")
    fun phoneCatalogLargeText() = capture("phone_411_catalog_font_130", fontScale = 1.3f) { CompletionCatalog() }

    @Test
    fun phoneCatalogMaximumText() = capture("phone_390_catalog_font_200", fontScale = 2f) { CompletionCatalog() }

    @Test
    @Config(qualifiers = "en-rUS-w411dp-h891dp-mdpi")
    fun phoneGridMaximumText() =
        capture("phone_411_grid_font_200", fontScale = 2f) { CompletionCatalog(mode = CatalogMode.GRID) }

    @Test
    fun phoneCatalogRightToLeft() =
        capture("phone_390_catalog_rtl_font_130", fontScale = 1.3f, direction = LayoutDirection.Rtl) {
            CompletionCatalog()
        }

    @Test
    @Config(qualifiers = "en-rUS-w411dp-h891dp-mdpi")
    fun phoneThreadPosts() = capture("phone_411_thread_posts") { CompletionThread() }

    @Test
    @Config(qualifiers = "en-rUS-w411dp-h891dp-mdpi")
    fun phoneThreadLargeText() = capture("phone_411_thread_font_130", fontScale = 1.3f) { CompletionThread() }

    @Test
    fun phoneThreadRightToLeftAtMaximumText() =
        capture("phone_390_thread_rtl_font_200", fontScale = 2f, direction = LayoutDirection.Rtl) {
            CompletionThread()
        }

    @Test
    @Config(qualifiers = "en-rUS-w411dp-h891dp-mdpi")
    fun phoneThreadFiles() = capture("phone_411_thread_files") { CompletionThread(layout = ThreadLayout.FILES) }

    @Test
    @Config(qualifiers = "en-rUS-w960dp-h800dp-mdpi")
    fun tabletEmptyDetail() =
        capture("tablet_960_empty_detail") {
            CompletionPanes(catalogWidth = 399.dp, threadOpen = false)
        }

    @Test
    @Config(qualifiers = "en-rUS-w960dp-h800dp-mdpi")
    fun tabletSelectedThread() =
        capture("tablet_960_selected_thread") {
            CompletionPanes(catalogWidth = 399.dp, threadOpen = true)
        }

    @Test
    @Config(qualifiers = "en-rUS-w1280dp-h900dp-mdpi")
    fun tabletGridAndThread() =
        capture("tablet_1280_grid_and_thread") {
            CompletionPanes(catalogWidth = 420.dp, threadOpen = true, mode = CatalogMode.GRID)
        }

    @Test
    fun phoneOpenDrawer() = capture("phone_390_open_drawer", openDrawer = true) { CompletionCatalog() }

    @Test
    @Config(qualifiers = "en-rUS-w960dp-h800dp-mdpi")
    fun tabletOpenDrawer() =
        capture("tablet_960_open_drawer", openDrawer = true) {
            CompletionPanes(catalogWidth = 399.dp, threadOpen = true)
        }

    @Test
    @Config(qualifiers = "en-rUS-w248dp-h600dp-mdpi")
    fun narrowRightToLeftDrawerAtMaximumText() =
        capture(
            "narrow_248_open_drawer_rtl_font_200",
            fontScale = 2f,
            direction = LayoutDirection.Rtl,
            openDrawer = true,
        ) { CompletionCatalog() }

    private fun capture(
        name: String,
        fontScale: Float = 1f,
        direction: LayoutDirection = LayoutDirection.Ltr,
        openDrawer: Boolean = false,
        content: @Composable () -> Unit,
    ) {
        val palette = canonicalNextPalette(theme != "light", theme == "amoled")
        composeRule.setContent {
            NextTheme(
                darkTheme = palette.dark,
                amoled = palette.amoled,
                palette = palette,
                fontScale = fontScale,
            ) {
                CompositionLocalProvider(LocalLayoutDirection provides direction) {
                    Surface(modifier = Modifier.fillMaxSize(), color = reader.background, content = content)
                }
            }
        }
        composeRule.waitForIdle()
        if (openDrawer) {
            composeRule.onNodeWithContentDescription("Menu").performClick()
            composeRule.waitForIdle()
            composeRule.onNodeWithTag(ReaderDrawerTags.DRAWER).assertIsDisplayed()
        }
        composeRule.onRoot().captureRoboImage(
            "src/androidHostTest/screenshots/reader_completion_${theme}_$name.png",
        )
    }

    companion object {
        @JvmStatic
        @Parameters(name = "{0}")
        fun themes(): List<Array<String>> = listOf("light", "dark", "amoled").map { arrayOf(it) }
    }
}

@Composable
private fun CompletionCatalog(
    mode: CatalogMode = CatalogMode.LIST,
    wideWindow: Boolean = false,
    selected: Boolean = false,
) {
    ReaderScaffold(
        title = "Technology",
        destination = NextDestination.BOARDS,
        onDestination = {},
        drawAppBar = false,
    ) {
        CompletionCatalogContent(mode, wideWindow, selected)
    }
}

@Composable
private fun CompletionCatalogContent(
    mode: CatalogMode,
    wideWindow: Boolean,
    selected: Boolean,
) {
    BoardScreen(
        board = "/g/",
        description = "Technology",
        itemCount = completionRows.size,
        rowAt = { completionRows.getOrNull(it) },
        mode = mode,
        onModeChange = {},
        sortLabel = "Bump order",
        onSort = {},
        onOpenMedia = {},
        onOpenRow = {},
        thumbnail = { _, modifier -> CompletionThumbnail(modifier) },
        navigation = ReaderNavigation.MENU,
        onNavigation = LocalReaderOpenDrawer.current ?: {},
        onPickBoard = {},
        onReload = {},
        onSearch = {},
        wideWindow = wideWindow,
        selectedRowId = completionRows.first().id.takeIf { selected },
        onOpenBrowser = {},
    )
}

@Composable
private fun CompletionPanes(
    catalogWidth: Dp,
    threadOpen: Boolean,
    mode: CatalogMode = CatalogMode.LIST,
) {
    ReaderScaffold(
        title = "Technology",
        destination = NextDestination.BOARDS,
        onDestination = {},
        drawAppBar = false,
    ) {
        ReaderTwoPane(
            catalogWidth = catalogWidth,
            threadOpen = threadOpen,
            listContent = {
                CompletionCatalogContent(mode, wideWindow = true, selected = threadOpen)
            },
            detailContent = { CompletionThread(wideWindow = true) },
        )
    }
}

@Composable
private fun CompletionThread(
    layout: ThreadLayout = ThreadLayout.POSTS,
    wideWindow: Boolean = false,
) {
    ThreadScreen(
        subject = "Weekend workshop notebook",
        board = "/g/",
        posts = completionPosts,
        watching = true,
        layout = layout,
        onLayoutChange = {},
        files = completionFiles,
        onOpenFile = {},
        fileTile = { _, modifier -> CompletionThumbnail(modifier) },
        collapsed = setOf("101"),
        onToggleCollapse = {},
        onWatch = {},
        onDownloadAll = {},
        onSavePage = {},
        onShare = {},
        firstUnreadPostId = "102",
        media = { _, modifier -> CompletionThumbnail(modifier) },
        siteName = "fourchan",
        onBack = {},
        saved = true,
        onToggleSaved = {},
        onReload = {},
        onPostReference = {},
        wideWindow = wideWindow,
        onOpenBrowser = {},
        statusText = "3 replies · 4 files · Updated 11 Nov 2026, 14:20",
    )
}

/** Production topic artwork supplies local deterministic media without a network/image-loader claim. */
@Composable
private fun CompletionThumbnail(modifier: Modifier) {
    BoardIcon(siteId = "fourchan", boardId = "g", modifier = modifier)
}

private val completionRows =
    listOf(
        FeedRow(
            subject = "Weekend workshop notebook",
            board = "/g/",
            activity = "2m",
            replies = 1234,
            media = 4,
            id = "fourchan/g/100",
            excerpt = ">Small projects for a quiet afternoon\nShare what you learned this week.",
            sticky = true,
        ),
        FeedRow(
            subject = "A watched thread with new replies",
            board = "/g/",
            activity = "8m",
            replies = 86,
            media = 3,
            unread = 7,
            id = "fourchan/g/200",
            excerpt = "The unread count keeps its accent and readable label.",
        ),
        FeedRow(
            subject = "A long title about organizing a shared reading list for the coming winter",
            board = "/g/",
            activity = "12m",
            replies = 48,
            media = 0,
            hasPreview = false,
            read = true,
            id = "fourchan/g/300",
            excerpt = "Text without an image takes the available width.",
            closed = true,
        ),
        FeedRow(
            subject = "Muted fixture",
            board = "/g/",
            activity = "20m",
            replies = 5,
            media = 2,
            muted = true,
            id = "fourchan/g/400",
            excerpt = "This preview is hidden by the real muted-row rendering.",
        ),
        FeedRow(
            subject = "Local photo archive",
            board = "/g/",
            activity = "1h",
            replies = 19,
            media = 6,
            id = "fourchan/g/500",
            excerpt = "A second row of cards gives the grid a visible gap and edge.",
        ),
        FeedRow(
            subject = "A short question",
            board = "/g/",
            activity = "2h",
            replies = 0,
            media = 0,
            hasPreview = false,
            id = "fourchan/g/600",
            excerpt = "No replies yet.",
        ),
    )

private val completionPosts =
    listOf(
        Post(
            number = "100",
            time = "11 Nov 2026, 14:02",
            body = "Small projects for a quiet afternoon\nShare a useful idea from your notebook.",
            hasMedia = true,
            replies = 2,
            id = "100",
            author = "Anonymous",
            fileLabel = "workshop-notebook.jpg · 128 KB",
            mediaCount = 3,
            replyNumbers = listOf("101", "102"),
        ),
        Post(
            number = "101",
            time = "11 Nov 2026, 14:08",
            body = "This reply is collapsed in the fixture.",
            depth = 1,
            replies = 2,
            id = "101",
            author = "Reader",
        ),
        Post(
            number = "102",
            time = "11 Nov 2026, 14:15",
            body = "A covered image stays private until its first tap.",
            depth = 1,
            hasMedia = true,
            id = "102",
            author = "Anonymous",
            fileLabel = "project-detail.png · 64 KB",
            imageSpoiler = true,
        ),
        Post(
            number = "103",
            time = "11 Nov 2026, 14:20",
            body = "A text-only reply keeps its reading width and uses the shared gutter.",
            depth = 3,
            id = "103",
            author = "Reader",
        ),
    )

private val completionFiles =
    List(4) { index ->
        MediaCell(id = "fourchan/g/100/file-$index", board = "/g/", spoiler = index == 1)
    }
