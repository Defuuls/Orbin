package com.orbin.uinext

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import com.orbin.uinext.tokens.ReaderSize
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xhdpi")
class ReaderCatalogControlsTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun boardPickerKeepsSameNamedBoardsQualifiedBySiteAndMarksTheCurrentBoard() {
        val options =
            listOf(
                ReaderBoardOption("fourchan", "4chan", "g", "Technology"),
                ReaderBoardOption("bbwchan", "BBWChan", "g", "Discussion"),
            )
        var selected: ReaderBoardOption? = null
        compose.setContent {
            NextTheme {
                ReaderBoardPicker(options, { selected = it }, {}, selectedProvider = "fourchan", selectedBoard = "g")
            }
        }
        compose.onNodeWithText("4chan").assertExists()
        compose.onNodeWithText("BBWChan").assertExists()
        compose.onNodeWithTag("reader-board-option-fourchan/g").assertIsSelected()
        compose.onNodeWithTag("reader-board-option-bbwchan/g").performClick()
        compose.runOnIdle { assertThat(selected).isEqualTo(options[1]) }
    }

    @Test
    fun catalogSearchEditsTheLoadedFilterAndProvidesAnAccessibleCloseAction() {
        var query = ""
        var closes = 0
        compose.setContent {
            NextTheme { ReaderCatalogSearch("", { query = it }, { closes++ }) }
        }
        compose.onNodeWithTag("reader-catalog-query").performTextInput("quiet hills")
        compose.onNodeWithContentDescription("Close catalog search").performClick()
        compose.runOnIdle {
            assertThat(query).isEqualTo("quiet hills")
            assertThat(closes).isEqualTo(1)
        }
    }

    @Test
    fun narrowRtlCatalogKeepsTheBoardPickerTargetAndMovesSearchIntoOverflow() {
        var picks = 0
        var searches = 0
        compose.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                NextTheme(fontScale = 2f) {
                    Box(Modifier.size(248.dp, 400.dp)) {
                        BoardScreen(
                            board = "/g/",
                            description = "Technology",
                            itemCount = 0,
                            rowAt = { null },
                            navigation = ReaderNavigation.MENU,
                            onNavigation = {},
                            onPickBoard = { picks++ },
                            onReload = {},
                            onSearch = { searches++ },
                            onModeChange = {},
                        )
                    }
                }
            }
        }
        compose
            .onNodeWithTag(ReaderAppBarTags.BOARD_PICKER)
            .assertWidthIsAtLeast(ReaderSize.touchTarget)
            .assertHeightIsAtLeast(ReaderSize.touchTarget)
            .performClick()
        compose.onNodeWithContentDescription("Search").assertDoesNotExist()
        compose.onNodeWithContentDescription("More options").performClick()
        compose.onNodeWithText("Search").performClick()
        compose.runOnIdle {
            assertThat(picks).isEqualTo(1)
            assertThat(searches).isEqualTo(1)
        }
    }

    @Test
    fun appBarOmitsNavigationWhenItsCallbackIsAbsent() {
        compose.setContent { NextTheme { ReaderAppBar("Board", navigation = ReaderNavigation.MENU) } }
        compose.onNodeWithContentDescription("Menu").assertDoesNotExist()
        compose.onNodeWithContentDescription("Back").assertDoesNotExist()
    }
}
