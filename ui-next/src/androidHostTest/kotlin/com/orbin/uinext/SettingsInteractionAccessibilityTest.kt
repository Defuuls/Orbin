package com.orbin.uinext

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SettingsInteractionAccessibilityTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val isSwitch = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch)

    @Test
    fun settingsToggleHasOneLabeledActionAndSearchKeepsItEditable() {
        var calls = 0
        composeRule.setContent {
            var checked by remember { mutableStateOf(false) }
            NextTheme {
                SettingsScreen(
                    groups = listOf("Privacy" to listOf(SettingItem("lock", "App lock", "Off", checked = checked))),
                    showRail = false,
                    onActivate = {
                        checked = !checked
                        calls++
                    },
                )
            }
        }
        composeRule.onNode(hasSetTextAction()).performTextInput("lock")
        composeRule.onAllNodes(isSwitch).assertCountEquals(1)
        composeRule
            .onNodeWithText("App lock")
            .assertIsOff()
            .performClick()
            .assertIsOn()
        assertEquals(1, calls)
    }

    @Test
    fun onboardingFollowHasOneActionAndReportsItsState() {
        var calls = 0
        composeRule.setContent {
            var followed by remember { mutableStateOf(false) }
            NextTheme(platform = NextPlatform.IOS) {
                OnboardingScreen(
                    boards = OnboardingBoards.Ready(listOf(OnboardingBoard("g", "Technology", "", false))),
                    following = if (followed) setOf("g") else emptySet(),
                    sites = listOf("Example"),
                    selectedSite = 0,
                    onSelectSite = {},
                    onFollow = { _, value ->
                        followed = value
                        calls++
                    },
                    onRetry = {},
                    onStart = {},
                )
            }
        }
        composeRule.onAllNodes(isSwitch).assertCountEquals(1)
        composeRule
            .onNode(isSwitch)
            .assertIsOff()
            .performClick()
            .assertIsOn()
        assertEquals(1, calls)
    }

    @Test
    fun onboardingRequiresASavedBoardAndDisablesActionsWhileSaving() {
        var startCalls = 0
        var saving by mutableStateOf(false)
        var following by mutableStateOf(emptySet<String>())
        composeRule.setContent {
            NextTheme {
                OnboardingScreen(
                    boards = OnboardingBoards.Ready(listOf(OnboardingBoard("g", "Technology", "", false))),
                    following = following,
                    sites = listOf("4chan", "BBWChan"),
                    selectedSite = 0,
                    onSelectSite = {},
                    onFollow = { _, _ -> saving = true },
                    onRetry = {},
                    onStart = { startCalls++ },
                    isSaving = saving,
                )
            }
        }
        composeRule.onNodeWithText("Start browsing").assertIsNotEnabled().performClick()
        assertEquals(0, startCalls)
        composeRule.onNode(isSwitch).performClick().assertIsNotEnabled()
        composeRule.onNodeWithText("4chan").assertIsNotEnabled()
        composeRule.runOnIdle { following = setOf("g") }
        composeRule.onNodeWithText("Start browsing").assertIsNotEnabled()
        composeRule.runOnIdle { saving = false }
        composeRule.onNodeWithText("Start browsing").assertIsEnabled().performClick()
        assertEquals(1, startCalls)
    }

    @Test
    fun onboardingCanStartWithAFollowOnAnotherSite() {
        var startCalls = 0
        composeRule.setContent {
            NextTheme(platform = NextPlatform.IOS) {
                OnboardingScreen(
                    boards = OnboardingBoards.Ready(emptyList()),
                    following = emptySet(),
                    sites = listOf("4chan", "BBWChan"),
                    selectedSite = 1,
                    onSelectSite = {},
                    onFollow = { _, _ -> },
                    onRetry = {},
                    onStart = { startCalls++ },
                    totalFollowing = 1,
                )
            }
        }
        composeRule.onNodeWithText("1 following").assertExists()
        composeRule.onNodeWithText("Start browsing").assertIsEnabled().performClick()
        assertEquals(1, startCalls)
    }

    @Test
    fun onboardingCompletionGuardCannotEnableAnEmptyFeed() {
        var startCalls = 0
        composeRule.setContent {
            NextTheme {
                OnboardingScreen(
                    boards = OnboardingBoards.Ready(emptyList()),
                    following = emptySet(),
                    sites = listOf("4chan"),
                    selectedSite = 0,
                    onSelectSite = {},
                    onFollow = { _, _ -> },
                    onRetry = {},
                    onStart = { startCalls++ },
                    canStart = true,
                    errorMessage = "Couldn't save your followed boards. Try again.",
                )
            }
        }
        composeRule.onNodeWithText("Follow at least one board to continue").assertExists()
        composeRule.onNodeWithText("Couldn't save your followed boards. Try again.").assertExists()
        composeRule.onNodeWithText("Start browsing").assertIsNotEnabled().performClick()
        assertEquals(0, startCalls)
    }
}
