package com.orbin.uinext

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
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
            var collapsed by mutableStateOf(emptySet<String>())
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
    fun `source labels distinguish provider ids without changing custom sources`() {
        assertEquals("4chan", sourceLabel("fourchan"))
        assertEquals("BBWChan", sourceLabel("BBW Chan"))
        assertEquals("A custom board", sourceLabel(" A custom board "))
    }
}
