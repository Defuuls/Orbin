package com.orbin.uinext

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** A source remains visible while reading, and compact header controls keep their real actions. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xhdpi")
class SocialThreadTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `thread source stays visible after scrolling to a reply`() {
        composeRule.setContent {
            NextTheme {
                ThreadScreen(
                    subject = "Weekly discussion",
                    siteName = "bbwchan",
                    board = "/gen/",
                    posts = (1..30).map { Post(number = "No. $it", time = "2m", body = "Post $it") },
                )
            }
        }
        composeRule.onNode(hasScrollToIndexAction()).performScrollToIndex(20)
        composeRule.onNodeWithText("BBWChan · /gen/").assertIsDisplayed()
        composeRule.onNodeWithText("Weekly discussion").assertIsDisplayed()
    }

    @Test
    fun `header back share and watch controls call their actions`() {
        var backs = 0
        var shares = 0
        var watches = 0
        composeRule.setContent {
            NextTheme {
                ThreadScreen(
                    subject = "Landscapes",
                    siteName = "fourchan",
                    board = "/wg/",
                    posts = listOf(Post(number = "No. 1", time = "2m", body = "A quiet lake")),
                    onBack = { backs++ },
                    onShare = { shares++ },
                    onWatch = { watches++ },
                )
            }
        }
        composeRule.onNodeWithContentDescription("Back").performClick()
        composeRule.onNodeWithContentDescription("Share thread").performClick()
        composeRule.onNodeWithContentDescription("Watch").performClick()
        assertEquals(1, backs)
        assertEquals(1, shares)
        assertEquals(1, watches)
    }

    @Test
    fun `collapsing an anonymous post removes its body without losing its identity`() {
        val post = Post(number = "No. 42", time = "3m", body = "A small project")
        composeRule.setContent {
            var collapsed by remember { mutableStateOf(emptySet<String>()) }
            NextTheme {
                ThreadScreen(
                    subject = "Weekly discussion",
                    board = "/gen/",
                    posts = listOf(post),
                    collapsed = collapsed,
                    onToggleCollapse = { collapsed = if (collapsed.isEmpty()) setOf(it.id) else emptySet() },
                )
            }
        }
        composeRule.onNodeWithText("Anonymous").performClick()
        composeRule.onNodeWithText("A small project").assertDoesNotExist()
        composeRule.onNodeWithText("No. 42 · 3m").assertExists()
        composeRule.onNodeWithText("Anonymous").performClick()
        composeRule.onNodeWithText("A small project").assertIsDisplayed()
    }

    @Test
    fun `bookmark control saves independently of watching`() {
        var saves = 0
        var watches = 0
        composeRule.setContent {
            NextTheme {
                ThreadScreen(
                    subject = "Landscapes",
                    board = "/wg/",
                    posts = listOf(Post(number = "No. 1", time = "2m", body = "A quiet lake")),
                    onToggleSaved = { saves++ },
                    onWatch = { watches++ },
                )
            }
        }
        composeRule.onNodeWithContentDescription("Save Landscapes").performClick()
        assertEquals(1, saves)
        assertEquals(0, watches)
    }

    @Test
    fun `a watched thread explains why its bookmark remains saved without offering a no-op button`() {
        composeRule.setContent {
            NextTheme {
                ThreadScreen(
                    subject = "Landscapes",
                    board = "/wg/",
                    posts = listOf(Post(number = "No. 1", time = "2m", body = "A quiet lake")),
                    watching = true,
                    saved = true,
                    onToggleSaved = {},
                )
            }
        }
        composeRule
            .onNodeWithContentDescription(
                "Saved while watching. Turn off Watch to remove Landscapes.",
            ).assertHasNoClickAction()
    }

    @Test
    fun `thread media keeps the platform slots natural height`() {
        composeRule.setContent {
            NextTheme {
                ThreadScreen(
                    subject = "Portrait images",
                    board = "/wg/",
                    posts = listOf(Post(number = "No. 1", time = "2m", body = "A portrait", hasMedia = true)),
                    media = { _, modifier ->
                        Box(modifier.height(320.dp).testTag("portrait-media"))
                    },
                )
            }
        }
        composeRule.onNodeWithTag("portrait-media", useUnmergedTree = true).assertHeightIsEqualTo(320.dp)
    }

    @Test
    fun `flat posts and files tabs expose selection and keep full touch targets`() {
        composeRule.setContent {
            var layout by remember { mutableStateOf(ThreadLayout.POSTS) }
            NextTheme {
                ThreadScreen(
                    subject = "A thread",
                    board = "/g/",
                    posts = listOf(Post(number = "No. 1", time = "2m", body = "A post")),
                    layout = layout,
                    onLayoutChange = { layout = it },
                )
            }
        }
        composeRule.onNodeWithText("Posts").assertIsSelected().assertHeightIsAtLeast(MIN_TOUCH_TARGET)
        composeRule
            .onNodeWithText("Files")
            .performClick()
            .assertIsSelected()
            .assertHeightIsAtLeast(MIN_TOUCH_TARGET)
        composeRule.onNodeWithText("A post").assertDoesNotExist()
        composeRule.onNodeWithText("Posts").performClick()
        composeRule.onNodeWithText("A post").assertIsDisplayed()
    }

    @Test
    fun `reply text aligns beneath the author beside its small avatar`() {
        composeRule.setContent {
            NextTheme {
                ThreadScreen(
                    subject = "A thread",
                    board = "/g/",
                    posts =
                        listOf(
                            Post(number = "No. 1", time = "2m", body = "Original post"),
                            Post(number = "No. 2", time = "1m", body = "A new project", author = "skywatcher"),
                        ),
                )
            }
        }
        val author = composeRule.onNodeWithText("skywatcher", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val body = composeRule.onNodeWithText("A new project", useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertEquals(author.left.value, body.left.value, 0.5f)
        assertTrue(body.left > 40.dp)
    }

    @Test
    fun `opening files preserves the exact posts reading position`() {
        var layout by mutableStateOf(ThreadLayout.POSTS)
        lateinit var postsState: LazyListState
        composeRule.setContent {
            postsState = rememberLazyListState()
            NextTheme {
                ThreadScreen(
                    subject = "A long thread",
                    board = "/g/",
                    posts = (1..40).map { Post(number = "No. $it", time = "2m", body = "Post $it") },
                    listState = postsState,
                    layout = layout,
                    files = (1..60).map { MediaCell(id = "file-$it", board = "/g/") },
                )
            }
        }
        composeRule.onNode(hasScrollToIndexAction()).performScrollToIndex(20)
        var index = 0
        var offset = 0
        composeRule.runOnIdle {
            index = postsState.firstVisibleItemIndex
            offset = postsState.firstVisibleItemScrollOffset
            layout = ThreadLayout.FILES
        }
        composeRule.onNode(hasScrollToIndexAction()).performScrollToIndex(8)
        composeRule.runOnIdle { layout = ThreadLayout.POSTS }
        composeRule.runOnIdle {
            assertEquals(index, postsState.firstVisibleItemIndex)
            assertEquals(offset, postsState.firstVisibleItemScrollOffset)
        }
    }

    @Test
    fun `jumping to a collapsed quoted post expands its content before scrolling`() {
        var collapsed by mutableStateOf(setOf("20"))
        var target by mutableStateOf<String?>(null)
        composeRule.setContent {
            NextTheme {
                ThreadScreen(
                    subject = "A long thread",
                    board = "/g/",
                    posts = (1..30).map { Post(number = "No. $it", time = "2m", body = "Post $it", id = "$it") },
                    collapsed = collapsed,
                    onToggleCollapse = { collapsed = collapsed - it.id },
                    scrollToPostId = target,
                    onScrollConsumed = { target = null },
                )
            }
        }
        composeRule.runOnIdle { target = "20" }
        composeRule.onNodeWithText("Post 20").assertIsDisplayed()
        composeRule.runOnIdle { assertTrue("20" !in collapsed) }
    }

    @Test
    fun `source labels distinguish provider ids without changing custom sources`() {
        assertEquals("4chan", sourceLabel("fourchan"))
        assertEquals("BBWChan", sourceLabel("BBW Chan"))
        assertEquals("A custom board", sourceLabel(" A custom board "))
    }
}
