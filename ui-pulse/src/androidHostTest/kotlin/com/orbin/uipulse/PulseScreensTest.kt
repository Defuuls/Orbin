package com.orbin.uipulse

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PulseScreensTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val labels =
        PulseControlsLabels(
            title = "Controls",
            refresh = "Refresh",
            back = "Back",
            commandHint = "Type / for commands",
            close = "Close",
        )

    private val lenses =
        listOf(
            PulseLensUi("home", "Home", selected = true),
            PulseLensUi("watched", "Watched", selected = false),
            PulseLensUi("feeds", "Feeds", selected = false),
        )

    private val lines =
        listOf(
            PulseLineUi("t:1", "3m", "Tomato thread", "Lakeside /gdn/", trailing = "+2", unread = true),
            PulseLineUi("t:2", "1h", "Raised beds", "Ridgeline /trn/"),
        )

    @Test
    fun streamShowsEveryLineAndReportsTheLineTapped() {
        val tapped = mutableListOf<String>()
        composeRule.setContent {
            MaterialTheme {
                PulseStream("Home", "2 new", "Menu", lines, onLineClick = { tapped += it }, onOpenControls = {})
            }
        }
        composeRule.onNodeWithText("Tomato thread").assertExists()
        composeRule.onNodeWithText("Raised beds").performClick()
        assertEquals(listOf("t:2"), tapped)
    }

    @Test
    fun holdingTheStreamOpensTheControls() {
        var opened = 0
        composeRule.setContent {
            MaterialTheme {
                PulseStream("Home", "2 new", "Menu", lines, onLineClick = {}, onOpenControls = { opened++ })
            }
        }
        composeRule.onNodeWithTag(PULSE_STREAM_TAG).performTouchInput { longClick() }
        composeRule.runOnIdle { assertEquals(1, opened) }
    }

    @Test
    fun theVisibleMenuButtonOpensTheControlsForKeyboardsAndScreenReaders() {
        var opened = 0
        composeRule.setContent {
            MaterialTheme {
                PulseStream("Home", "2 new", "Menu", lines, onLineClick = {}, onOpenControls = { opened++ })
            }
        }
        composeRule.onNodeWithText("Menu").performClick()
        composeRule.runOnIdle { assertEquals(1, opened) }
    }

    @Test
    fun hiddenControlsShowNothing() {
        composeRule.setContent { MaterialTheme { Controls(visible = false) } }
        composeRule.onNodeWithText("Refresh").assertDoesNotExist()
    }

    @Test
    fun controlsMarkTheCurrentViewAndReportTheViewChosen() {
        val chosen = mutableListOf<String>()
        composeRule.setContent { MaterialTheme { Controls(visible = true, onLens = { chosen += it }) } }
        composeRule.onNodeWithText("Home").assertIsSelected()
        composeRule.onNodeWithText("Feeds").performClick()
        assertEquals(listOf("feeds"), chosen)
    }

    @Test
    fun backIsOfferedOnlyInAThread() {
        composeRule.setContent { MaterialTheme { Controls(visible = true, showBack = false) } }
        composeRule.onNodeWithText("Refresh").assertExists()
        composeRule.onNodeWithText("Back").assertDoesNotExist()
    }

    @Test
    fun refreshAndBackReportTheirTaps() {
        var refreshed = 0
        var wentBack = 0
        composeRule.setContent {
            MaterialTheme {
                Controls(visible = true, showBack = true, onRefresh = { refreshed++ }, onBack = { wentBack++ })
            }
        }
        composeRule.onNodeWithText("Refresh").performClick()
        composeRule.onNodeWithText("Back").performClick()
        composeRule.runOnIdle { assertEquals(1 to 1, refreshed to wentBack) }
    }

    @Test
    fun typingReportsTheTextAndASuggestionRunsItsCommand() {
        val typed = mutableListOf<String>()
        val ran = mutableListOf<String>()
        composeRule.setContent {
            MaterialTheme {
                Controls(
                    visible = true,
                    suggestions = listOf(PulseCommandUi("go:feeds", "Go to Feeds")),
                    onCommandChange = { typed += it },
                    onRunCommand = { ran += it },
                )
            }
        }
        composeRule.onNodeWithTag(PULSE_COMMAND_TAG).performTextInput("/feeds")
        composeRule.onNodeWithText("Go to Feeds").performClick()
        assertEquals(listOf("/feeds"), typed)
        assertEquals(listOf("go:feeds"), ran)
    }

    @Composable
    private fun Controls(
        visible: Boolean,
        showBack: Boolean = false,
        suggestions: List<PulseCommandUi> = emptyList(),
        onCommandChange: (String) -> Unit = {},
        onRunCommand: (String) -> Unit = {},
        onLens: (String) -> Unit = {},
        onRefresh: () -> Unit = {},
        onBack: () -> Unit = {},
    ) {
        PulseControls(
            visible = visible,
            labels = labels,
            lenses = lenses,
            showBack = showBack,
            command = "",
            suggestions = suggestions,
            onCommandChange = onCommandChange,
            onRunCommand = onRunCommand,
            onSubmit = {},
            onLens = onLens,
            onRefresh = onRefresh,
            onBack = onBack,
            onDismiss = {},
        )
    }
}
