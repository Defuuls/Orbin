package com.orbin.app.pulse

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import com.google.common.truth.Truth.assertThat
import com.orbin.core.model.stream.StreamLens
import com.orbin.uipulse.PULSE_COMMAND_TAG
import com.orbin.uipulse.PulseLineUi
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35], qualifiers = "w390dp-h844dp-mdpi")
class PulseShellTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val lines =
        listOf(
            PulseLineUi("t:p|g|1", "1h", "Seed saving", "/g/ · Garden", trailing = "7"),
            PulseLineUi("t:p|trn|2", "2h", "Night service", "/trn/ · Trains", trailing = "1"),
        )
    private val tapped = mutableListOf<String>()
    private val lensesOpened = mutableListOf<StreamLens>()
    private var refreshed = 0
    private var wentBack = 0

    private fun show(
        showBack: Boolean = false,
        selectedLens: StreamLens? = StreamLens.Home,
    ) {
        composeRule.setContent {
            MaterialTheme {
                PulseShell(
                    PulseScreen(
                        title = "Stream",
                        summary = "2 threads",
                        lines = lines,
                        selectedLens = selectedLens,
                        showBack = showBack,
                        onLineClick = { tapped += it },
                        onRefresh = { refreshed++ },
                        onBack = { wentBack++ },
                        onOpenLens = { lensesOpened += it },
                    ),
                )
            }
        }
    }

    private fun openControls() = composeRule.onNodeWithText("Menu").performClick()

    @Test
    fun theStreamShowsItsLinesAndReportsTheLineTapped() {
        show()
        composeRule.onNodeWithText("Seed saving").assertExists()
        composeRule.onNodeWithText("Night service").performClick()
        assertThat(tapped).containsExactly("t:p|trn|2")
    }

    @Test
    fun theControlsStayClosedUntilAskedFor() {
        show()
        composeRule.onNodeWithText("Controls").assertDoesNotExist()
        openControls()
        composeRule.onNodeWithText("Controls").assertExists()
    }

    @Test
    fun refreshRunsTheScreensRefreshAndClosesTheControls() {
        show()
        openControls()
        composeRule.onNodeWithText("Refresh").performClick()
        composeRule.runOnIdle { assertThat(refreshed).isEqualTo(1) }
        composeRule.onNodeWithText("Controls").assertDoesNotExist()
    }

    @Test
    fun backIsOnlyOfferedInAThreadAndRunsTheScreensBack() {
        show(showBack = false)
        openControls()
        composeRule.onNodeWithText("Back").assertDoesNotExist()
    }

    @Test
    fun backRunsTheScreensBack() {
        show(showBack = true, selectedLens = null)
        openControls()
        composeRule.onNodeWithText("Back").performClick()
        composeRule.runOnIdle { assertThat(wentBack).isEqualTo(1) }
    }

    @Test
    fun choosingAnotherViewOpensItAndChoosingTheCurrentOneDoesNot() {
        show()
        openControls()
        composeRule.onNodeWithText("Home").performClick()
        composeRule.runOnIdle { assertThat(lensesOpened).isEmpty() }

        openControls()
        composeRule.onNodeWithText("Settings").performClick()
        composeRule.runOnIdle { assertThat(lensesOpened).containsExactly(StreamLens.Settings) }
    }

    @Test
    fun typingFiltersTheStream() {
        show()
        openControls()
        composeRule.onNodeWithTag(PULSE_COMMAND_TAG).performTextInput("night")
        composeRule.onNodeWithText("Seed saving").assertDoesNotExist()
        composeRule.onNodeWithText("Night service").assertExists()
    }

    @Test
    fun aSlashSuggestsCommandsAndKeepsTheStreamWhole() {
        show()
        openControls()
        composeRule.onNodeWithTag(PULSE_COMMAND_TAG).performTextInput("/saved")
        composeRule.onNodeWithText("Seed saving").assertExists()
        composeRule.onNodeWithText("Night service").assertExists()
    }

    @Test
    fun theKeyboardActionRunsTheBestCommand() {
        show()
        openControls()
        composeRule.onNodeWithTag(PULSE_COMMAND_TAG).performTextInput("/watch")
        composeRule.onNodeWithTag(PULSE_COMMAND_TAG).performImeAction()
        composeRule.runOnIdle { assertThat(lensesOpened).containsExactly(StreamLens.Watched) }
        composeRule.onNodeWithText("Controls").assertDoesNotExist()
    }

    @Test
    fun theKeyboardActionOnAPlainFilterJustClosesTheControls() {
        show()
        openControls()
        composeRule.onNodeWithTag(PULSE_COMMAND_TAG).performTextInput("night")
        composeRule.onNodeWithTag(PULSE_COMMAND_TAG).performImeAction()
        composeRule.runOnIdle { assertThat(lensesOpened).isEmpty() }
        composeRule.onNodeWithText("Controls").assertDoesNotExist()
        composeRule.onNodeWithText("Seed saving").assertExists()
    }
}
