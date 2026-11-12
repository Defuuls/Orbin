package com.orbin.uinext

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
        composeRule.onNodeWithTag(ReaderSavedTags.row("bbwchan/g/7")).performClick()
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

    @Test
    fun savedReaderRowsAnnounceTheirRealMetadataWithoutAProfileHeader() {
        val records =
            listOf(
                ThreadCollectionRow("fourchan/g/7", "First saved thread", "4chan · /g/"),
                ThreadCollectionRow(
                    id = "bbwchan/g/7",
                    title = "Another source with the same thread number",
                    source = "BBWChan · /g/",
                    detail = "Saved for later reading",
                    time = "12 Nov 2026, 10:30",
                    threadNumber = "7",
                    unread = 3,
                ),
            )
        var opened: ThreadCollectionRow? = null
        composeRule.setContent {
            NextTheme {
                ThreadCollectionScreen("Saved", records, onOpen = { opened = it }, onDestination = {})
            }
        }
        composeRule.onNodeWithTag(ReaderSavedTags.LIST).assertIsDisplayed()
        val first = composeRule.onNodeWithTag(ReaderSavedTags.row(records[0].id)).getUnclippedBoundsInRoot()
        val bar = composeRule.onNodeWithTag(ReaderAppBarTags.BAR).getUnclippedBoundsInRoot()
        assertTrue(
            "Saved records start under the reader bar rather than a profile header",
            first.top.value <= bar.bottom.value + 16f,
        )
        composeRule
            .onNodeWithTag(ReaderSavedTags.row(records[1].id))
            .assert(announces(records[1].title))
            .assert(announces(records[1].source))
            .assert(announces("No.7"))
            .assert(announces(records[1].time))
            .assert(announces(records[1].detail))
            .assert(announces("3 new replies"))
            .performClick()
        composeRule.runOnIdle { assertEquals(records[1], opened) }
        composeRule.onNodeWithText("Sites").assertDoesNotExist()
        composeRule.onNodeWithText("Threads").assertDoesNotExist()
    }

    @Test
    fun savedReaderKeepsItsReadingPositionWhenAnotherRecordIsInserted() {
        var records by mutableStateOf(List(30) { ThreadCollectionRow("site/g/$it", "Saved thread $it", "Site · /g/") })
        composeRule.setContent {
            NextTheme {
                ThreadCollectionScreen(
                    "Saved",
                    records,
                    onOpen = {},
                    onDestination = {},
                )
            }
        }
        composeRule.onNodeWithTag(ReaderSavedTags.LIST).performScrollToIndex(18)
        val tag = ReaderSavedTags.row(records[18].id)
        val before = composeRule.onNodeWithTag(tag).getUnclippedBoundsInRoot()
        composeRule.runOnIdle {
            records =
                listOf(ThreadCollectionRow("other/g/99", "Just saved", "Other · /g/")) + records
        }
        val after = composeRule.onNodeWithTag(tag).assertIsDisplayed().getUnclippedBoundsInRoot()
        assertEquals(
            "A new bookmark must not replace the current reading anchor",
            before.top.value,
            after.top.value,
            0.5f,
        )
    }

    @Test
    fun emptySavedUsesTheReaderEmptyStateWithoutProfileStatistics() {
        composeRule.setContent {
            NextTheme {
                ThreadCollectionScreen(
                    "Saved",
                    emptyList(),
                    onOpen = {},
                    onDestination = {},
                )
            }
        }
        composeRule.onNodeWithTag(ReaderStateTags.EMPTY).assertIsDisplayed()
        composeRule.onNodeWithText("Threads you save or visit will appear here.").assertIsDisplayed()
        composeRule.onNodeWithText("Sites").assertDoesNotExist()
        composeRule.onNodeWithText("Threads").assertDoesNotExist()
    }

    private fun announces(text: String) =
        hasText(text, substring = true) or hasContentDescription(text, substring = true)

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
