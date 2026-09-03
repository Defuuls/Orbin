package com.orbin.uinext

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** What the interface tells a screen reader. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xhdpi")
class InterfaceSemanticsTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `the layout switcher exposes grid and images without list`() {
        composeRule.setContent {
            NextTheme { FeedScreen(rows = ROWS, layout = FeedLayout.GRID) }
        }
        composeRule.onNodeWithText("Grid").assertIsSelected()
        composeRule.onNodeWithText("Images").assertIsNotSelected()
        composeRule.onNodeWithText("List").assertDoesNotExist()
    }

    @Test
    fun `the grid layout option is a single choice rather than a plain button`() {
        composeRule.setContent {
            NextTheme { FeedScreen(rows = ROWS, layout = FeedLayout.GRID) }
        }
        composeRule.onNodeWithText("Grid").assert(hasRole(Role.RadioButton))
    }

    @Test
    fun `a legacy list layout request renders the readable grid`() {
        composeRule.setContent {
            NextTheme { FeedScreen(rows = ROWS, layout = FeedLayout.LIST) }
        }
        composeRule.onNodeWithText("Grid").assertIsSelected()
        composeRule.onNodeWithText("List").assertDoesNotExist()
    }

    @Test
    fun `an image cell names the thread it opens`() {
        composeRule.setContent {
            NextTheme { FeedScreen(rows = ROWS, layout = FeedLayout.IMAGES) }
        }
        composeRule
            .onNodeWithContentDescription("${ROWS[0].subject}, ${ROWS[0].board}")
            .assertHasClickAction()
    }

    @Test
    fun `a grid feed cell is a button`() {
        composeRule.setContent {
            NextTheme { FeedScreen(rows = ROWS, layout = FeedLayout.GRID) }
        }
        composeRule
            .onNodeWithText(ROWS[0].subject)
            .assertHasClickAction()
            .assert(hasRole(Role.Button))
    }

    @Test
    fun `a grid feed cell reads as one node carrying its metadata`() {
        composeRule.setContent {
            NextTheme { FeedScreen(rows = ROWS, layout = FeedLayout.GRID) }
        }
        composeRule
            .onNodeWithText(ROWS[0].subject)
            .assert(hasTextContaining(ROWS[0].board))
            .assert(hasTextContaining("218 replies"))
    }

    @Test
    fun `a board row announces whether it is subscribed`() {
        composeRule.setContent {
            NextTheme {
                BoardPickerScreen(
                    boards =
                        listOf(
                            BoardChoice(id = "g", title = "Technology", subscribed = true),
                            BoardChoice(id = "ck", title = "Food", subscribed = false),
                        ),
                )
            }
        }
        composeRule.onNodeWithText("/g/").assertIsOn()
        composeRule.onNodeWithText("/ck/").assertIsOff()
    }

    private fun hasRole(role: Role) = SemanticsMatcher.expectValue(SemanticsProperties.Role, role)

    private fun hasTextContaining(fragment: String) =
        SemanticsMatcher("text contains '$fragment'") { node ->
            node.config
                .getOrElse(SemanticsProperties.Text) { emptyList() }
                .any { it.text.contains(fragment) }
        }

    private companion object {
        val ROWS =
            listOf(
                FeedRow(
                    subject = "Anyone else running a home server on ARM?",
                    board = "/g/",
                    activity = "4m",
                    replies = 218,
                    media = 31,
                ),
                FeedRow(
                    subject = "Weekly desktop thread",
                    board = "/g/",
                    activity = "12m",
                    replies = 94,
                    media = 88,
                ),
            )
    }
}
