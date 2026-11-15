package com.orbin.uinext

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.orbin.uinext.components.NextDestination
import com.orbin.uinext.components.NextTheme
import com.orbin.uinext.screens.ReaderCollectionTags
import com.orbin.uinext.screens.ReaderSavedTags
import com.orbin.uinext.screens.SearchRow
import com.orbin.uinext.screens.SearchScreen
import com.orbin.uinext.screens.SearchState
import com.orbin.uinext.screens.ThreadCollectionRow
import com.orbin.uinext.screens.ThreadCollectionScreen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.ParameterizedRobolectricTestRunner.Parameters
import org.robolectric.annotation.Config

/** Actual Search/Saved/Watched/history routes share accessible rounded cards and retain their data. */
@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-mdpi")
class ReaderRecordPresentationTest(
    private val destination: String,
) {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun phoneCardsUseReadableTextAndOpenTheCompleteRecord() = verifyRow(wide = false)

    @Test
    @Config(qualifiers = "w960dp-h800dp-mdpi")
    fun tabletCardsKeepTheLargerBodyAndOpenTheCompleteRecord() = verifyRow(wide = true)

    private fun verifyRow(wide: Boolean) {
        val record =
            ThreadCollectionRow(
                id = "bbwchan/g/7",
                title = "Actual stored subject",
                source = "BBWChan · /g/",
                detail = "Actual matching text from the stored thread",
                time = "12 Nov 2026, 10:30",
                threadNumber = "7",
                unread = 3,
            )
        val search = SearchRow(record.id, record.title, record.source, record.detail)
        var opened = ""
        var openedRecord: ThreadCollectionRow? = null
        compose.setContent {
            NextTheme {
                if (destination == "Search") {
                    SearchScreen(
                        query = "matching",
                        onQueryChange = {},
                        onSearch = {},
                        state = SearchState.Results(listOf(search)),
                        onOpenRow = { opened = it.id },
                    )
                } else {
                    ThreadCollectionScreen(
                        title = destination,
                        rows = listOf(record),
                        onOpen = {
                            opened = it.id
                            openedRecord = it
                        },
                        selectedDestination =
                            if (destination ==
                                "Watched"
                            ) {
                                NextDestination.WATCHED
                            } else {
                                NextDestination.SAVED
                            },
                        onBack = if (destination == "History") ({}) else null,
                        onDestination = {},
                    )
                }
            }
        }
        val rowTag =
            when (destination) {
                "Search" -> ReaderSearchTags.row(record.id)
                "Saved" -> ReaderSavedTags.row(record.id)
                else -> ReaderCollectionTags.row(record.id)
            }
        val row = compose.onNodeWithTag(rowTag).getUnclippedBoundsInRoot()
        val title = compose.onNodeWithText(record.title, useUnmergedTree = true).getUnclippedBoundsInRoot()
        val source = compose.onNodeWithText(record.source, useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertEquals(14f, (title.left - row.left).value, 0.5f)
        assertEquals("The source pill leaves room around its text", title.left.value + 8f, source.left.value, 0.5f)
        assertTrue("The qualified source stays below the subject", source.top >= title.bottom)
        assertTrue("The complete source stays inside its card", source.right <= row.right)
        assertEquals(15.sp, layout(record.title).layoutInput.style.fontSize)
        assertEquals(FontWeight.Bold, layout(record.title).layoutInput.style.fontWeight)
        assertEquals("The source uses the shared pill type", 11.sp, layout(record.source).layoutInput.style.fontSize)
        assertEquals(if (wide) 16.sp else 15.sp, layout(record.detail).layoutInput.style.fontSize)
        compose.onNodeWithText(record.detail, useUnmergedTree = true).assertExists()
        if (destination != "Search") {
            compose.onNodeWithText("No.7 · ${record.time}", useUnmergedTree = true).assertExists()
            compose.onNodeWithText("3 new replies", useUnmergedTree = true).assertExists()
        }
        compose.onNodeWithTag(rowTag).performClick()
        compose.runOnIdle {
            assertEquals(record.id, opened)
            if (destination != "Search") assertEquals(record, openedRecord)
        }
    }

    private fun layout(text: String): TextLayoutResult {
        val layouts = mutableListOf<TextLayoutResult>()
        compose
            .onNodeWithText(
                text,
                useUnmergedTree = true,
            ).performSemanticsAction(SemanticsActions.GetTextLayoutResult) {
                assertTrue(it(layouts))
            }
        return layouts.single()
    }

    companion object {
        @JvmStatic
        @Parameters(name = "{0}")
        fun destinations(): List<Array<String>> = listOf("Search", "Saved", "Watched", "History").map { arrayOf(it) }
    }
}
