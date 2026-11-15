package com.orbin.uinext

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.orbin.uinext.components.NextTheme
import com.orbin.uinext.screens.MediaCell
import com.orbin.uinext.screens.Post
import com.orbin.uinext.screens.ThreadLayout
import com.orbin.uinext.screens.ThreadScreen
import com.orbin.uinext.screens.ThreadTags
import com.orbin.uinext.tokens.ReaderSize
import com.orbin.uinext.tokens.ReaderSpace
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Post and album geometry, media slots, responsive text and visible metadata. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xhdpi")
class ReaderThreadPresentationTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `a post places its platform media in a bounded full width preview below the comment`() {
        composeRule.setContent {
            NextTheme {
                ThreadScreen(
                    subject = "Portrait images",
                    board = "/wg/",
                    posts = listOf(Post(number = "No. 1", time = "2m", body = "A portrait", hasMedia = true)),
                    media = { _, modifier -> Box(modifier.testTag("platform-media")) },
                )
            }
        }
        val preview = composeRule.onNodeWithTag(ThreadTags.THUMBNAIL, true).getUnclippedBoundsInRoot()
        val platform = composeRule.onNodeWithTag("platform-media", true).getUnclippedBoundsInRoot()
        val comment = composeRule.onNodeWithText("A portrait", useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertEquals(14f, preview.left.value, 0.5f)
        assertEquals(383f, (preview.right - preview.left).value, 0.5f)
        assertEquals((preview.right - preview.left).value * 0.75f, (preview.bottom - preview.top).value, 0.5f)
        assertEquals(preview, platform)
        assertTrue("Comments are read before their inline attachment", comment.bottom <= preview.top)
    }

    @Test
    @Config(qualifiers = "w960dp-h800dp-xhdpi")
    fun `a wide window keeps a full width preview below the four hundred dp height limit`() {
        composeRule.setContent {
            NextTheme {
                ThreadScreen(
                    subject = "A thread",
                    board = "/g/",
                    posts = listOf(Post(number = "No. 1", time = "2m", body = "A post", hasMedia = true)),
                    wideWindow = true,
                )
            }
        }
        composeRule
            .onNodeWithTag(ThreadTags.THUMBNAIL, useUnmergedTree = true)
            .assertWidthIsEqualTo(932.dp)
            .assertHeightIsEqualTo(400.dp)
    }

    @Test
    fun `a post with several files shows how many more there are and one with a single file does not`() {
        composeRule.setContent {
            NextTheme {
                ThreadScreen(
                    subject = "A thread",
                    board = "/g/",
                    posts =
                        listOf(
                            Post(number = "No. 1", time = "2m", body = "Three", hasMedia = true, mediaCount = 3),
                            Post(number = "No. 2", time = "1m", body = "One", hasMedia = true),
                        ),
                )
            }
        }
        composeRule.onNodeWithText("+2", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText("+0", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun `files use three Clover album cells with one dp margins and a metadata strip`() {
        composeRule.setContent {
            NextTheme {
                ThreadScreen(
                    subject = "A thread",
                    board = "/g/",
                    posts = ONE_POST,
                    layout = ThreadLayout.FILES,
                    files =
                        (1..4).map {
                            MediaCell(id = "file-$it", board = "/g/", details = "JPG 640x480 2 KB")
                        },
                )
            }
        }
        val tiles = composeRule.onAllNodesWithTag(ThreadTags.FILE)
        val first = tiles[0].getUnclippedBoundsInRoot()
        val third = tiles[2].getUnclippedBoundsInRoot()
        val fourth = tiles[3].getUnclippedBoundsInRoot()
        assertEquals(first.top.value, third.top.value, 0.5f)
        assertTrue("the fourth file starts a new row", fourth.top > first.top)
        assertEquals((ReaderSpace.albumPadding + ReaderSpace.albumCellMargin).value, first.left.value, 0.5f)
        // A short last row keeps the tile size of the full rows above it.
        assertEquals((first.right - first.left).value, (fourth.right - fourth.left).value, 0.5f)
        assertEquals(
            (first.right - first.left + ReaderSize.albumDetails).value,
            (first.bottom - first.top).value,
            0.5f,
        )
        composeRule.onAllNodesWithTag(ThreadTags.FILE_DETAILS).assertCountEquals(4)
        composeRule.onAllNodesWithTag(ThreadTags.FILE_DETAILS)[0].assertHeightIsEqualTo(ReaderSize.albumDetails)
    }

    @Test
    fun `post headers comments and inline attachments share the same reading inset`() {
        composeRule.setContent {
            NextTheme {
                ThreadScreen(
                    subject = "A thread",
                    board = "/g/",
                    posts =
                        listOf(
                            Post(number = "No. 1", time = "2m", body = "Original post", hasMedia = true),
                            Post(number = "No. 2", time = "1m", body = "A new project", author = "skywatcher"),
                        ),
                )
            }
        }
        val author = composeRule.onNodeWithText("skywatcher", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val body = composeRule.onNodeWithText("A new project", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val withMedia = composeRule.onNodeWithText("Original post", useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertEquals(author.left.value, body.left.value, 0.5f)
        assertEquals(14f, body.left.value, 0.5f)
        assertEquals(body.left.value, withMedia.left.value, 0.5f)
        val thumbnail = composeRule.onNodeWithTag(ThreadTags.THUMBNAIL, true).getUnclippedBoundsInRoot()
        assertEquals(withMedia.left.value, thumbnail.left.value, 0.5f)
        assertTrue(withMedia.bottom <= thumbnail.top)
    }

    @Test
    fun `replies indent by at most three gaps from their reading edge in both directions`() {
        var direction by mutableStateOf(LayoutDirection.Ltr)
        composeRule.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides direction) {
                NextTheme {
                    ThreadScreen(
                        subject = "A thread",
                        board = "/g/",
                        posts =
                            listOf(
                                Post(number = "No. 1", time = "2m", body = "Level zero"),
                                Post(number = "No. 2", time = "2m", body = "Level one", depth = 1),
                                Post(number = "No. 3", time = "2m", body = "Level three", depth = 3),
                                Post(number = "No. 4", time = "2m", body = "Level nine", depth = 9),
                            ),
                    )
                }
            }
        }
        for (layoutDirection in listOf(LayoutDirection.Ltr, LayoutDirection.Rtl)) {
            composeRule.runOnIdle { direction = layoutDirection }

            fun inset(text: String): Float {
                val bounds = composeRule.onNodeWithText(text, useUnmergedTree = true).getUnclippedBoundsInRoot()
                return if (layoutDirection == LayoutDirection.Ltr) bounds.left.value else 411f - bounds.right.value
            }
            assertEquals(14f, inset("Level zero"), 0.5f)
            assertEquals(14f + ReaderSpace.gap.value, inset("Level one"), 0.5f)
            assertEquals(14f + ReaderSpace.gap.value * 3, inset("Level three"), 0.5f)
            assertEquals(inset("Level three"), inset("Level nine"), 0.5f)
        }
    }

    @Test
    fun `the footer counts replies and files`() {
        composeRule.setContent {
            NextTheme {
                ThreadScreen(
                    subject = "A thread",
                    board = "/g/",
                    posts =
                        listOf(
                            Post(number = "No. 1", time = "2m", body = "One", hasMedia = true, mediaCount = 2),
                            Post(number = "No. 2", time = "1m", body = "Two"),
                            Post(number = "No. 3", time = "1m", body = "Three", hasMedia = true),
                        ),
                )
            }
        }
        composeRule.onNodeWithTag(ThreadTags.LIST).performScrollToNode(hasTestTag(ThreadTags.STATUS))
        composeRule.onNodeWithTag(ThreadTags.STATUS).assertHeightIsEqualTo(ReaderSize.threadStatus)
        composeRule.onNodeWithText("2 replies · 3 files").assertExists()
    }

    @Test
    fun `at font two a post keeps the full width preview below its readable comment`() {
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                NextTheme {
                    ThreadScreen(
                        subject = "A thread",
                        board = "/g/",
                        posts = listOf(Post(number = "1", time = "2m", body = "Large text", hasMedia = true)),
                    )
                }
            }
        }
        val thumbnail = composeRule.onNodeWithTag(ThreadTags.THUMBNAIL, true).getUnclippedBoundsInRoot()
        val body = composeRule.onNodeWithText("Large text", useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertEquals(383f, (thumbnail.right - thumbnail.left).value, 0.5f)
        assertEquals((thumbnail.right - thumbnail.left).value * 0.75f, (thumbnail.bottom - thumbnail.top).value, 0.5f)
        assertTrue("Large text stays above the media rather than sharing its width", body.bottom <= thumbnail.top)
        assertEquals(14f, body.left.value, 0.5f)
    }

    private companion object {
        val ONE_POST = listOf(Post(number = "No. 1", time = "2m", body = "A quiet lake"))
    }
}
