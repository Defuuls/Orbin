package com.orbin.uinext

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.unit.dp
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.common.truth.Truth.assertThat
import com.orbin.uinext.components.FeedRow
import com.orbin.uinext.components.NextDestination
import com.orbin.uinext.components.NextTheme
import com.orbin.uinext.components.ReaderAction
import com.orbin.uinext.components.ReaderAppBarTags
import com.orbin.uinext.components.ReaderIcons
import com.orbin.uinext.components.ReaderScaffold
import com.orbin.uinext.screens.CatalogMode
import com.orbin.uinext.screens.FeedScreen
import com.orbin.uinext.screens.FeedScreenActions
import com.orbin.uinext.screens.FeedScreenState
import com.orbin.uinext.screens.ReaderFeedTags
import com.orbin.uinext.screens.SettingItem
import com.orbin.uinext.screens.SettingsScreen
import com.orbin.uinext.tokens.ReaderSize
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Production Home rows, chrome and drawer must respect real system safe areas. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xhdpi")
class WindowInsetsTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    /** Replaces `immersive media is edge to edge while captions and navigation clear system bars`. */
    @Test
    fun `Home rows and bounded thumbnails clear the app bar and bottom system inset`() {
        composeRule.setContent {
            NextTheme {
                FeedScreen(
                    state =
                        FeedScreenState(
                            rows = listOf(FeedRow("A thread that has to stay readable", "/g/", "4m", 12, 3)),
                            subtitle = "1 thread",
                        ),
                    actions =
                        FeedScreenActions(
                            onOpenBoards = {},
                            onOpenWatched = {},
                            onOpenSearch = {},
                            onOpenSaved = {},
                        ),
                    thumbnail = { _, modifier ->
                        Box(modifier.testTag("feed-media").background(Color.Green))
                    },
                )
            }
        }
        applySystemBars()

        val root = composeRule.onRoot().getUnclippedBoundsInRoot()
        val media = composeRule.onNodeWithTag("feed-media", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val bar = composeRule.onNodeWithTag(ReaderAppBarTags.BAR).getUnclippedBoundsInRoot()
        val grid = composeRule.onNodeWithTag(ReaderFeedTags.GRID).getUnclippedBoundsInRoot()
        assertThat(grid.top.value).isAtLeast(bar.bottom.value)
        assertThat((bar.bottom - bar.top).value).isWithin(0.5f).of((STATUS_BAR + ReaderSize.appBar).value)
        assertThat(media.top.value).isAtLeast(grid.top.value)
        assertThat((media.right - media.left).value).isWithin(0.5f).of(ReaderSize.thumbnail.value)
        assertThat((media.bottom - media.top).value).isWithin(0.5f).of(ReaderSize.thumbnail.value)
        assertThat(media.left.value).isAtLeast(root.left.value)
        assertThat(media.right.value).isAtMost(root.right.value)
        assertThat(media.bottom.value).isAtMost((root.bottom - NAVIGATION_BAR).value)
    }

    @Test
    fun `the final Home row stops above the bottom system inset`() {
        val rows = List(30) { FeedRow("Thread $it", "/g/", "now", it, 0, hasPreview = false, id = "row-$it") }
        composeRule.setContent {
            NextTheme {
                FeedScreen(
                    state =
                        FeedScreenState(
                            rows = rows,
                        ),
                    actions =
                        FeedScreenActions(
                            onOpenBoards = {},
                        ),
                )
            }
        }
        applySystemBars()
        composeRule.onNodeWithTag(ReaderFeedTags.GRID).performScrollToIndex(rows.lastIndex)
        val root = composeRule.onRoot().getUnclippedBoundsInRoot()
        val last = composeRule.onNodeWithTag(ReaderFeedTags.row(rows.last().id)).getUnclippedBoundsInRoot()
        assertThat(last.bottom.value).isAtMost((root.bottom - NAVIGATION_BAR).value + 0.5f)
    }

    @Test
    fun `empty Home grid keeps its Boards action above the bottom system inset without scrolling`() {
        composeRule.setContent {
            NextTheme {
                FeedScreen(
                    state =
                        FeedScreenState(
                            rows = emptyList(),
                            mode = CatalogMode.GRID,
                        ),
                    actions =
                        FeedScreenActions(
                            onOpenBoards = {},
                            onRetry = {},
                        ),
                )
            }
        }
        applySystemBars()
        val root = composeRule.onRoot().getUnclippedBoundsInRoot()
        val boards =
            composeRule
                .onNode(
                    hasText("Boards") and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button),
                ).getUnclippedBoundsInRoot()
        assertThat(boards.bottom.value).isAtMost((root.bottom - NAVIGATION_BAR).value + 0.5f)
        assertThat(boards.top.value).isAtLeast(STATUS_BAR.value + ReaderSize.appBar.value)
    }

    /** Replaces `navigation uses horizontal safe areas without adding capsule margins`. */
    @Test
    fun `the app bar fills the width while its controls respect horizontal safe areas`() {
        composeRule.setContent {
            NextTheme {
                Box {
                    ReaderScaffold(
                        "Feed",
                        destination = NextDestination.FEED,
                        onDestination = {},
                        actions = listOf(ReaderAction(ReaderIcons.Search, "Search") {}),
                    ) {}
                }
            }
        }
        applySystemBars(horizontal = 24.dp)
        val root = composeRule.onRoot().getUnclippedBoundsInRoot()
        val bar = composeRule.onNodeWithTag(ReaderAppBarTags.BAR).getUnclippedBoundsInRoot()
        val menu = composeRule.onNodeWithContentDescription("Search").getUnclippedBoundsInRoot()
        assertThat(bar.left.value).isWithin(0.5f).of(root.left.value)
        assertThat(bar.right.value).isWithin(0.5f).of(root.right.value)
        assertThat(menu.left.value).isAtLeast((root.left + 24.dp).value)
        assertThat(menu.right.value).isAtMost((root.right - 24.dp).value)
    }

    @Test
    fun `settings content clears the status bar`() {
        composeRule.setContent {
            NextTheme {
                // The app bar title and the first preference category can share a label.
                SettingsScreen(
                    groups = listOf("Content" to listOf(SettingItem("t", "Threads per board", "12"))),
                    showRail = false,
                )
            }
        }
        applySystemBars()

        val title = composeRule.onNodeWithTag(ReaderAppBarTags.TITLE).getUnclippedBoundsInRoot()
        assertThat(title.top.value).isAtLeast(STATUS_BAR.value)
    }

    /**
     * Robolectric's window reports no insets, so they are dispatched by hand. This is the whole
     * point of the test: without it the assertions pass on a broken layout, which is exactly how
     * the goldens missed this.
     */
    private fun applySystemBars(horizontal: androidx.compose.ui.unit.Dp = 0.dp) {
        val decor = composeRule.activity.window.decorView
        val density = composeRule.density
        val bars =
            with(density) {
                Insets.of(
                    horizontal.roundToPx(),
                    STATUS_BAR.roundToPx(),
                    horizontal.roundToPx(),
                    NAVIGATION_BAR.roundToPx(),
                )
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
