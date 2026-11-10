package com.orbin.uinext

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.navigationevent.DirectNavigationEventInput
import androidx.navigationevent.NavigationEventDispatcher
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SettingsDirectoryTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun directoryOpensSubpageAndReturnsWithoutLeavingSettings() {
        var exits = 0
        composeRule.setContent {
            NextTheme {
                SettingsScreen(
                    groups =
                        listOf(
                            "Appearance" to listOf(SettingItem("amoled", "True black", "Off", checked = false)),
                        ),
                    onBack = { exits++ },
                )
            }
        }
        composeRule.onNodeWithText("True black").assertDoesNotExist()
        composeRule.onNodeWithText("Appearance").performClick()
        composeRule.onNodeWithText("True black").assertIsOff()
        composeRule.onNodeWithContentDescription("Back").performClick()
        composeRule.onNodeWithText("Settings and activity").assertExists()
        assertEquals(0, exits)
        composeRule.onNodeWithContentDescription("Back").performClick()
        assertEquals(1, exits)
    }

    @Test
    fun systemBackClosesTheSubpageBeforeTheHostReceivesBack() {
        var hostBacks = 0
        lateinit var dispatcher: NavigationEventDispatcher
        composeRule.setContent {
            dispatcher = requireNotNull(LocalNavigationEventDispatcherOwner.current).navigationEventDispatcher
            // NavHost 2.10 and the iOS root use this same modern navigation handler.
            NavigationBackHandler(
                state = rememberNavigationEventState(NavigationEventInfo.None),
                isBackEnabled = true,
                onBackCompleted = { hostBacks++ },
            )
            NextTheme {
                SettingsScreen(
                    groups =
                        listOf(
                            "Appearance" to listOf(SettingItem("amoled", "True black", "Off", checked = false)),
                        ),
                )
            }
        }
        val input = DirectNavigationEventInput()
        composeRule.runOnUiThread { dispatcher.addInput(input) }
        composeRule.onNodeWithText("Appearance").performClick()
        composeRule.waitForIdle()
        composeRule.runOnUiThread { input.backCompleted() }
        assertEquals("The settings handler should receive back before its host", 0, hostBacks)
        composeRule.onNodeWithText("Settings and activity").assertExists()
        composeRule.waitForIdle()
        composeRule.runOnUiThread { input.backCompleted() }
        composeRule.waitForIdle()
        assertEquals(1, hostBacks)
        composeRule.runOnUiThread { dispatcher.removeInput(input) }
    }

    @Test
    fun backLeavesAPageEvenWhileItsEditorRemainsExpandedInTheHost() {
        composeRule.setContent {
            NextTheme {
                SettingsScreen(
                    groups =
                        listOf(
                            "Appearance" to
                                listOf(
                                    SettingItem(
                                        "colorTheme",
                                        "Color theme",
                                        "Blue",
                                        SettingKind.CHOICE,
                                        options = listOf("Blue", "Teal"),
                                    ),
                                ),
                        ),
                    expandedId = "colorTheme",
                    onBack = {},
                )
            }
        }
        composeRule.onNodeWithText("Color theme").assertExists()
        composeRule.onNodeWithContentDescription("Back").performClick()
        composeRule.onNodeWithText("Settings and activity").assertExists()
        composeRule.onNodeWithText("Color theme").assertDoesNotExist()
    }

    @Test
    fun allRowsRemainReachableIncludingUnknownPlatformSettings() {
        val rows =
            listOf(
                SettingItem("themeMode", "Theme", "System"),
                SettingItem("screenPrivacy", "Privacy shield", "On"),
                SettingItem("futureSetting", "New platform setting", "On"),
            )
        assertEquals(rows.toSet(), settingsPages(listOf("Settings" to rows)).values.flatten().toSet())
        assertEquals(listOf(rows.last()), settingsPages(listOf("Settings" to rows))["other"])
    }
}
