package com.orbin.uinext

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
    fun sourceQualifiedRowsOpenTheCorrectSavedThreadAndKeepTheFourTabsReachable() {
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
        composeRule.onNodeWithContentDescription("Search").performClick()
        assertEquals(NextDestination.SEARCH, destination)
    }
}
