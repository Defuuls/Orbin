package com.orbin.uinext

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.orbin.uinext.tokens.ReaderSize
import com.orbin.uinext.tokens.ReaderSpace
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h640dp-xhdpi")
class ReaderStatesTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun `empty state is centered below the app bar and reload has a full touch target`() {
        var reloads = 0
        composeRule.setContent {
            NextTheme {
                Column(Modifier.fillMaxSize()) {
                    ReaderAppBar(title = "Catalog")
                    ReaderEmptyState("No threads", "Reload to check for new threads.", onReload = { reloads += 1 })
                }
            }
        }

        val state = composeRule.onNodeWithTag(ReaderStateTags.EMPTY).getUnclippedBoundsInRoot()
        val content = composeRule.onNodeWithTag(ReaderStateTags.CONTENT).getUnclippedBoundsInRoot()
        assertEquals((state.top + state.bottom).value / 2, (content.top + content.bottom).value / 2, 0.5f)
        composeRule
            .onNodeWithTag(ReaderStateTags.ACTION)
            .assertHeightIsAtLeast(ReaderSize.touchTarget)
            .assertWidthIsAtLeast(ReaderSize.touchTarget)
            .performClick()
        assertEquals(1, reloads)
    }

    @Test
    fun `empty and error actions are absent when callbacks are absent`() {
        var error by mutableStateOf(false)
        composeRule.setContent {
            NextTheme {
                if (error) {
                    ReaderErrorState(
                        ReaderFailureCause.NETWORK,
                    )
                } else {
                    ReaderEmptyState("No threads", "Reload later.")
                }
            }
        }
        composeRule.onNodeWithTag(ReaderStateTags.ACTION).assertDoesNotExist()

        error = true
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Network error").assertIsDisplayed()
        composeRule.onNodeWithTag(ReaderStateTags.ACTION).assertDoesNotExist()
    }

    @Test
    fun `error titles name each cause and retry invokes its host`() {
        var cause by mutableStateOf(ReaderFailureCause.NETWORK)
        var retries = 0
        composeRule.setContent { NextTheme { ReaderErrorState(cause, onRetry = { retries += 1 }) } }
        composeRule.onNodeWithText("Network error").assertIsDisplayed()
        composeRule.onNodeWithText("Retry").performClick()
        assertEquals(1, retries)

        cause = ReaderFailureCause.NOT_FOUND
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Not found").assertIsDisplayed()

        cause = ReaderFailureCause.SERVER
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Server error").assertIsDisplayed()
    }

    @Test
    fun `first load announces loading once and reserves the actual thumbnail square`() {
        composeRule.setContent { NextTheme { ReaderCatalogSkeleton() } }

        composeRule.onNodeWithContentDescription("Loading catalog").assertIsDisplayed()
        composeRule.onAllNodesWithTag(ReaderStateTags.PROGRESS).assertCountEquals(1)
        composeRule
            .onAllNodesWithTag(ReaderStateTags.SKELETON_THUMBNAIL, useUnmergedTree = true)
            .onFirst()
            .assertWidthIsEqualTo(ReaderSize.thumbnail)
            .assertHeightIsEqualTo(ReaderSize.thumbnail)
        composeRule
            .onAllNodesWithTag(ReaderStateTags.SKELETON_CELL, useUnmergedTree = true)
            .onFirst()
            .assertHeightIsAtLeast(ReaderSize.cellMin)
        composeRule.onNodeWithText("Anonymous", substring = true).assertDoesNotExist()
    }

    @Test
    fun `grid catalog skeleton follows the Coral catalog grid`() {
        composeRule.setContent { NextTheme { ReaderCatalogSkeleton(mode = CatalogMode.GRID, showProgress = false) } }

        val root = composeRule.onNodeWithTag(ReaderStateTags.CATALOG_SKELETON).getUnclippedBoundsInRoot()
        val expected = orbinCatalogGridSpec(root.right - root.left)
        val cells = composeRule.onAllNodesWithTag(ReaderStateTags.SKELETON_CELL, useUnmergedTree = true)
        val first = cells.onFirst().getUnclippedBoundsInRoot()
        assertEquals("a portrait phone is two columns", 2, expected.columns)
        val cellWidth =
            (root.right - root.left - ORBIN_CATALOG_GUTTER * 2 - ORBIN_CATALOG_GAP * (expected.columns - 1)) /
                expected.columns
        assertEquals(cellWidth.value, (first.right - first.left).value, 0.5f)
        assertTrue(
            "a cell is at least its image tall",
            (first.bottom - first.top) >= expected.imageHeight,
        )
        assertTrue("enough cells to cover the viewport", cells.fetchSemanticsNodes().size >= expected.columns)
    }

    @Test
    fun `wide thread skeleton reserves wide geometry and can share the host progress bar`() {
        composeRule.setContent {
            NextTheme {
                Column(Modifier.fillMaxSize()) {
                    ReaderRefreshProgress()
                    ReaderThreadSkeleton(wideWindow = true, showProgress = false)
                }
            }
        }

        composeRule.onNodeWithContentDescription("Loading thread").assertIsDisplayed()
        composeRule.onAllNodesWithTag(ReaderStateTags.PROGRESS).assertCountEquals(1)
        composeRule
            .onAllNodesWithTag(ReaderStateTags.SKELETON_THUMBNAIL, useUnmergedTree = true)
            .onFirst()
            .assertWidthIsEqualTo(ReaderSize.thumbnailWide)
            .assertHeightIsEqualTo(ReaderSize.thumbnailWide)
    }

    @Test
    fun `skeleton thumbnails mirror to the trailing side in right to left layout`() {
        composeRule.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                NextTheme { ReaderCatalogSkeleton() }
            }
        }

        val cell =
            composeRule
                .onAllNodesWithTag(ReaderStateTags.SKELETON_CELL, useUnmergedTree = true)
                .onFirst()
                .getUnclippedBoundsInRoot()
        val thumbnail =
            composeRule
                .onAllNodesWithTag(ReaderStateTags.SKELETON_THUMBNAIL, useUnmergedTree = true)
                .onFirst()
                .getUnclippedBoundsInRoot()
        assertEquals((cell.right - ReaderSpace.gutter).value, thumbnail.right.value, 0.5f)
    }

    @Test
    fun `refresh bar keeps content in place and refresh feedback leaves content visible`() {
        var refreshing by mutableStateOf(false)
        var failed by mutableStateOf(false)
        var retries = 0
        composeRule.setContent {
            NextTheme {
                Box(Modifier.fillMaxSize()) {
                    Column {
                        ReaderRefreshProgress(refreshing)
                        Text("Loaded thread")
                    }
                    if (failed) {
                        ReaderRefreshFailure(
                            ReaderFailureCause.NETWORK,
                            onRetry = { retries += 1 },
                            modifier = Modifier.align(Alignment.BottomCenter),
                        )
                    }
                }
            }
        }
        val before = composeRule.onNodeWithText("Loaded thread").getUnclippedBoundsInRoot()
        refreshing = true
        composeRule.waitForIdle()
        val during = composeRule.onNodeWithText("Loaded thread").getUnclippedBoundsInRoot()
        assertEquals(before, during)
        composeRule.onNodeWithTag(ReaderStateTags.PROGRESS).assertHeightIsEqualTo(ReaderSize.progress)

        refreshing = false
        failed = true
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Loaded thread").assertIsDisplayed()
        composeRule.onNodeWithTag(ReaderStateTags.REFRESH_FAILURE).assertIsDisplayed()
        composeRule.onNodeWithText("Retry").performClick()
        assertEquals(1, retries)
        assertTrue(
            composeRule.onNodeWithTag(ReaderStateTags.REFRESH_FAILURE).getUnclippedBoundsInRoot().top > before.bottom,
        )
    }

    @Test
    fun `refresh feedback has no retry control when its host cannot retry`() {
        composeRule.setContent { NextTheme { ReaderRefreshFailure(ReaderFailureCause.SERVER) } }

        composeRule.onNodeWithTag(ReaderStateTags.REFRESH_FAILURE).assertIsDisplayed()
        composeRule.onNodeWithTag(ReaderStateTags.ACTION).assertDoesNotExist()
    }

    @Test
    fun `refresh retry clears a nonzero navigation inset and remains physically tappable`() {
        var retries = 0
        composeRule.setContent {
            NextTheme {
                Box(Modifier.fillMaxSize().testTag("refresh-window")) {
                    Text("Loaded content")
                    ReaderRefreshFailure(
                        ReaderFailureCause.NETWORK,
                        onRetry = { retries++ },
                        modifier = Modifier.align(Alignment.BottomCenter),
                    )
                }
            }
        }
        val navigationInset = 32.dp
        val bars = with(composeRule.density) { Insets.of(0, 0, 0, navigationInset.roundToPx()) }
        val insets =
            WindowInsetsCompat
                .Builder()
                .setInsets(WindowInsetsCompat.Type.navigationBars(), bars)
                .build()
        composeRule.runOnUiThread {
            ViewCompat.dispatchApplyWindowInsets(composeRule.activity.window.decorView, insets)
        }
        composeRule.waitForIdle()
        val window = composeRule.onNodeWithTag("refresh-window").getUnclippedBoundsInRoot()
        val retry = composeRule.onNodeWithText("Retry").assertHeightIsAtLeast(ReaderSize.touchTarget)
        val bounds = retry.getUnclippedBoundsInRoot()
        assertTrue(
            "Retry must clear the navigation bar and the feedback's gap: $bounds in $window",
            bounds.bottom <= window.bottom - navigationInset - ReaderSpace.gapSmall,
        )
        retry.performTouchInput { click(center) }
        composeRule.runOnIdle { assertEquals(1, retries) }
        composeRule.onNodeWithText("Loaded content").assertIsDisplayed()
    }
}
