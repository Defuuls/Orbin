package com.orbin.uinext

import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
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

    @Test
    fun sourceQualifiedRowsOpenTheCorrectSavedThreadAndKeepTheFiveTabsReachable() {
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
        composeRule.onNodeWithContentDescription("Watched").performClick()
        assertEquals(NextDestination.WATCHED, destination)
        composeRule.onNodeWithContentDescription("Search").performClick()
        assertEquals(NextDestination.SEARCH, destination)
    }

    @Test
    fun watchedHasItsOwnSelectedTabAndOpensTheSourceQualifiedThread() {
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
        composeRule.onNodeWithContentDescription("Watched").assertIsSelected()
        composeRule.onNodeWithText("Discussion").performClick()
        assertEquals("bbwchan/gen/7", opened)
    }
}
