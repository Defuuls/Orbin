package com.orbin.uinext

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.navigationevent.DirectNavigationEventInput
import androidx.navigationevent.NavigationEventDispatcher
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

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
        composeRule.onNode(hasSetTextAction()).assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Back").performClick()
        composeRule.onNodeWithText("Settings and privacy").assertExists()
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
        composeRule.onNodeWithText("Settings and privacy").assertExists()
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
        composeRule.onNodeWithText("Appearance").assertExists()
        composeRule.onNodeWithText("Color theme").assertExists()
        composeRule.onNodeWithContentDescription("Back").performClick()
        composeRule.onNodeWithText("Settings and privacy").assertExists()
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

    @Test
    fun chatButtonPreferenceIsDiscoverableInPlaybackAndSearch() {
        val row =
            SettingItem(
                "openThreadComments",
                "Open comments from chat button",
                "Off",
                checked = false,
                hint = "Off opens thread videos; on opens comments.",
            )
        val groups = listOf("Playback" to listOf(row))

        assertEquals(listOf(row), settingsPages(groups)["playback"])
        assertEquals(listOf(row), filterSettings(groups, "thread videos").flatMap { it.second })
        assertEquals(listOf(row), filterSettings(groups, "chat comments").flatMap { it.second })
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun settingsValuesMoveBelowLabelsAtLargeTextWithoutTruncation() {
        var scale by mutableStateOf(1f)
        composeRule.setContent {
            NextTheme(fontScale = scale) {
                SettingsScreen(
                    modifier = Modifier.width(320.dp),
                    groups =
                        listOf(
                            "Settings" to
                                listOf(
                                    SettingItem(
                                        id = "scopeInfo",
                                        label = "Playback defaults",
                                        value = "App defaults",
                                        kind = SettingKind.INFO,
                                    ),
                                ),
                        ),
                    expandedId = "scopeInfo",
                )
            }
        }
        var label = composeRule.onNodeWithText("Playback defaults").getUnclippedBoundsInRoot()
        var value = composeRule.onNodeWithText("App defaults").getUnclippedBoundsInRoot()
        assertTrue(value.left > label.left)
        assertTrue(value.top < label.bottom)
        composeRule.runOnIdle { scale = 2f }
        composeRule.waitForIdle()
        label = composeRule.onNodeWithText("Playback defaults").getUnclippedBoundsInRoot()
        value = composeRule.onNodeWithText("App defaults").getUnclippedBoundsInRoot()
        assertTrue(
            "The value must wrap below the label at large text: label=$label value=$value",
            value.top >= label.bottom,
        )
    }

    @Test
    fun enabledSitesReportsTheConfiguredCountAndOpensItsDirectory() {
        var destination = ""
        composeRule.setContent {
            NextTheme {
                SettingsScreen(groups = emptyList(), enabledSiteCount = 2, onNavigate = { destination = it })
            }
        }
        composeRule.onNodeWithText("Enabled sites").assert(hasText("2")).performClick()
        assertEquals("boards", destination)
    }
}
