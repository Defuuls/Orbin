package com.orbin.uinext

import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xhdpi")
class ThreadCollectionInteractionTest {
    @get:Rule
    val composeRule = createComposeRule()

    /** Replaces sourceQualifiedRowsOpenTheCorrectSavedThreadAndKeepTheFiveTabsReachable. */
    @Test
    fun sourceQualifiedRowsOpenTheCorrectSavedThreadAndKeepTheDrawerReachable() {
        var opened = ""
        var destination: NextDestination? = null
        composeRule.setContent {
            NextTheme {
                ThreadCollectionScreen(
                    title = "Saved",
                    rows =
                        listOf(
                            ThreadCollectionRow("fourchan/g/7", "First", "4chan · /g/"),
                            ThreadCollectionRow("bbwchan/g/7", "Second", "BBWChan · /g/"),
                        ),
                    onOpen = { opened = it.id },
                    onDestination = { destination = it },
                )
            }
        }
        composeRule.onNodeWithText("BBWChan · /g/").performClick()
        assertEquals("bbwchan/g/7", opened)
        composeRule.onNodeWithContentDescription("Menu").performClick()
        composeRule.onNodeWithTag(ReaderDrawerTags.ENTRY + NextDestination.WATCHED.name).performClick()
        composeRule.waitForIdle()
        assertEquals(NextDestination.WATCHED, destination)
        composeRule.onNodeWithContentDescription("Menu").performClick()
        composeRule.onNodeWithTag(ReaderDrawerTags.ENTRY + NextDestination.SEARCH.name).performClick()
        composeRule.waitForIdle()
        assertEquals(NextDestination.SEARCH, destination)
    }

    /** Replaces watchedHasItsOwnSelectedTabAndOpensTheSourceQualifiedThread. */
    @Test
    fun watchedHasItsOwnSelectedDrawerEntryAndOpensTheSourceQualifiedThread() {
        var opened = ""
        composeRule.setContent {
            NextTheme {
                ThreadCollectionScreen(
                    title = "Watched threads",
                    rows = listOf(ThreadCollectionRow("bbwchan/gen/7", "Discussion", "BBWChan · /gen/")),
                    onOpen = { opened = it.id },
                    selectedDestination = NextDestination.WATCHED,
                    onDestination = {},
                )
            }
        }
        composeRule.onNodeWithText("Discussion").performClick()
        assertEquals("bbwchan/gen/7", opened)
        composeRule.onNodeWithContentDescription("Menu").performClick()
        composeRule.onNodeWithTag(ReaderDrawerTags.ENTRY + NextDestination.WATCHED.name).assertIsSelected()
    }
}
