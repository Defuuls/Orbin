package com.orbin.uinext

import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w390dp-h844dp-mdpi")
class BrowsingFeatureParityTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun allFiveSortsRemainReachableAndDispatchTheirStableIdsAtLargeText() {
        val chosen = mutableListOf<String>()
        compose.setContent {
            var selected by remember { mutableStateOf(CatalogSortTabs.BUMP_ORDER) }
            NextTheme(fontScale = 2f) {
                BoardScreen(
                    board = "/g/",
                    description = "Technology",
                    itemCount = 0,
                    rowAt = { null },
                    sortTabs = catalogSortTabs(),
                    selectedSortId = selected,
                    onSelectSort = {
                        selected = it
                        chosen += it
                    },
                )
            }
        }
        listOf(
            "Newest" to CatalogSortTabs.CATALOG,
            "Oldest" to CatalogSortTabs.OLDEST,
            "Reply count" to CatalogSortTabs.REPLY_COUNT,
            "Images" to CatalogSortTabs.IMAGE_COUNT,
            "Bump order" to CatalogSortTabs.BUMP_ORDER,
        ).forEach { (label, id) ->
            compose
                .onNodeWithContentDescription(
                    label,
                ).performScrollTo()
                .assertIsDisplayed()
                .performClick()
                .assertIsSelected()
            assertEquals(id, chosen.last())
        }
        assertEquals(5, chosen.size)
    }

    @Test
    fun invalidPastedLinkStaysEditableAndCancelDoesNotNavigate() {
        val opened = mutableListOf<String>()
        var dismisses = 0
        compose.setContent {
            NextTheme {
                OpenThreadLinkDialog(
                    onDismiss = { dismisses++ },
                    onOpen = { value ->
                        opened += value
                        value == "https://boards.4chan.org/g/thread/123"
                    },
                )
            }
        }
        compose.onNode(hasSetTextAction()).performTextInput("https://unknown.example/g/123")
        compose.onNodeWithText("Open", substring = false).performClick()
        compose.onNodeWithText("Enter a supported thread link.").assertIsDisplayed()
        assertEquals(0, dismisses)
        compose.onNode(hasSetTextAction()).performTextInput("ignored")
        compose.onNodeWithText("Enter a supported thread link.").assertDoesNotExist()
        // Cancellation must not dispatch a second navigation request.
        compose.onNodeWithText("Cancel").performClick()
        assertEquals(1, dismisses)
        assertEquals(1, opened.size)
    }

    @Test
    fun searchScopesDispatchOnlyTheSelectedBackendAndAllowRepeatingTheSameQuery() {
        val local = mutableListOf<String>()
        val global = mutableListOf<String>()
        compose.setContent {
            var query by remember { mutableStateOf("cats") }
            NextTheme {
                SearchScreen(
                    query,
                    { query = it },
                    { local += query },
                    SearchState.Idle,
                    {},
                    webSearch = { submitted ->
                        LaunchedEffect(Unit) { if (submitted.isNotEmpty()) global += submitted }
                        Text("Website query: $submitted")
                    },
                )
            }
        }
        compose.onNodeWithText("All of 4chan").performClick()
        compose.onNodeWithText("Search", substring = false).performClick()
        compose.runOnIdle {
            assertEquals(listOf("cats"), global)
            assertEquals(emptyList<String>(), local)
        }
        compose.onNodeWithText("Search", substring = false).performClick()
        compose.runOnIdle { assertEquals(listOf("cats", "cats"), global) }
        compose.onNodeWithText("Followed boards").performClick()
        compose.onNodeWithText("Search", substring = false).performClick()
        compose.runOnIdle {
            assertEquals(listOf("cats"), local)
            assertEquals(listOf("cats", "cats"), global)
        }
    }

    @Test
    fun acceptedPastedLinkIsTrimmedAndDismissedOnce() {
        var opened = ""
        var dismisses = 0
        compose.setContent {
            NextTheme {
                OpenThreadLinkDialog(
                    onDismiss = { dismisses++ },
                    onOpen = {
                        opened = it
                        true
                    },
                )
            }
        }
        compose.onNode(hasSetTextAction()).performTextReplacement("  https://boards.4chan.org/g/thread/123  ")
        compose.onNodeWithText("Open", substring = false).performClick()
        assertEquals("https://boards.4chan.org/g/thread/123", opened)
        assertEquals(1, dismisses)
    }
}
