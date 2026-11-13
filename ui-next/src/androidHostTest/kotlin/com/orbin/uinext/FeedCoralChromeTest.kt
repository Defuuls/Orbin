package com.orbin.uinext

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w390dp-h844dp-mdpi")
class FeedCoralChromeTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun inlineModeControlChangesTheRealLayoutWithoutOpeningOptions() {
        var mode by mutableStateOf(CatalogMode.LIST)
        val rows = rows()
        compose.setContent {
            NextTheme {
                FeedScreen(rows = rows, mode = mode, onModeChange = { mode = it })
            }
        }
        compose.onNodeWithTag(ReaderAppBarTags.TITLE).assertTextEquals("Home")
        compose.onNodeWithTag(ReaderFeedTags.MODE).assertContentDescriptionEquals("Grid view").performClick()
        compose.runOnIdle { assertEquals(CatalogMode.GRID, mode) }
        val first = compose.onNodeWithTag(ReaderFeedTags.row(rows[0].id)).getUnclippedBoundsInRoot()
        val second = compose.onNodeWithTag(ReaderFeedTags.row(rows[1].id)).getUnclippedBoundsInRoot()
        assertEquals(first.top.value, second.top.value, 0.5f)
        assertTrue("A phone grid has two Coral card columns", second.left > first.left)

        compose.onNodeWithTag(ReaderFeedTags.MODE).assertContentDescriptionEquals("List view").performClick()
        compose.runOnIdle { assertEquals(CatalogMode.LIST, mode) }
        val listFirst = compose.onNodeWithTag(ReaderFeedTags.row(rows[0].id)).getUnclippedBoundsInRoot()
        val listSecond = compose.onNodeWithTag(ReaderFeedTags.row(rows[1].id)).getUnclippedBoundsInRoot()
        assertTrue("List mode keeps full-width rows", listSecond.top > listFirst.top)
        assertEquals(listFirst.left.value, listSecond.left.value, 0.5f)
    }

    @Test
    fun theBottomBarNavigatesOnlyWiredDestinationsAndUpdatesItsDownloadBadge() {
        var badge by mutableIntStateOf(2)
        val opened = mutableListOf<String>()
        compose.setContent {
            NextTheme {
                FeedScreen(
                    rows = emptyList(),
                    downloadBadge = badge,
                    onOpenBoards = { opened += "boards" },
                    onOpenDownloads = { opened += "downloads" },
                    onSettings = { opened += "settings" },
                )
            }
        }
        compose.onNode(tab("Feed")).assertIsSelected().performClick()
        compose.onNode(tab("Downloads")).assertIsDisplayed().performClick()
        compose.onNode(tab("Settings")).assertIsDisplayed().performClick()
        compose.onNode(tab("Boards")).assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(listOf("downloads", "settings", "boards"), opened) }
        compose.onNode(tab("Downloads")).assert(
            SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "2 downloads in progress"),
        )
        compose.runOnIdle { badge = 0 }
        compose.onNode(tab("Downloads")).assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.StateDescription))
        compose.onNode(tab("Search")).assertDoesNotExist()
        compose.onNode(tab("Watched")).assertDoesNotExist()
        compose.onNode(tab("Saved")).assertDoesNotExist()
    }

    private fun tab(label: String) =
        hasContentDescription(label) and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)

    private fun rows() =
        List(3) { index ->
            FeedRow(
                id = "fourchan/g/$index",
                subject = "Fixture $index",
                board = "/g/",
                activity = "1m",
                replies = 1,
                media = 1,
                hasPreview = false,
            )
        }
}
