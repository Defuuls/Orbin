package com.orbin.uinext

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
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
    fun appNavigationBelongsToTheDirectoryAndReturnsAfterLocalBack() {
        composeRule.setContent {
            NextTheme {
                SettingsScreen(
                    groups =
                        listOf(
                            "Appearance" to listOf(SettingItem("amoled", "AMOLED black", "Off", checked = false)),
                        ),
                    onBack = {},
                    bottomBar = { Text("App navigation") },
                )
            }
        }

        composeRule.onNodeWithText("App navigation").assertIsDisplayed()
        composeRule.onNodeWithText("Appearance").performClick()
        composeRule.onNodeWithText("App navigation").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Back").performClick()
        composeRule.onNodeWithText("App navigation").assertIsDisplayed()

        composeRule.onNodeWithContentDescription("Search settings").performClick()
        composeRule.onNodeWithText("App navigation").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Back").performClick()
        composeRule.onNodeWithText("App navigation").assertIsDisplayed()
    }

    @Test
    fun directoryOpensSubpageAndReturnsWithoutLeavingSettings() {
        var exits = 0
        composeRule.setContent {
            NextTheme {
                SettingsScreen(
                    groups =
                        listOf(
                            "Appearance" to listOf(SettingItem("amoled", "AMOLED black", "Off", checked = false)),
                        ),
                    onBack = { exits++ },
                )
            }
        }
        composeRule.onNodeWithText("AMOLED black").assertDoesNotExist()
        composeRule.onNodeWithText("Appearance").performClick()
        composeRule.onNodeWithText("AMOLED black").assertIsOff()
        composeRule.onNode(hasSetTextAction()).assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Back").performClick()
        composeRule.onNodeWithTag(ReaderAppBarTags.TITLE).assertTextEquals("Settings")
        assertEquals(0, exits)
        composeRule.onNodeWithContentDescription("Back").performClick()
        assertEquals(1, exits)
    }

    @Test
    fun restoringAScrolledSettingsSubpageKeepsItsPosition() {
        val restoration = StateRestorationTester(composeRule)
        restoration.setContent {
            NextTheme {
                SettingsScreen(
                    modifier = Modifier.height(320.dp),
                    groups =
                        listOf(
                            "Playback" to
                                listOf(
                                    SettingItem("openThreadComments", "Open comments from chat button", "Off"),
                                    SettingItem("playbackScope", "Scope", "App defaults", SettingKind.INFO),
                                    SettingItem("autoplay", "Autoplay", "On"),
                                    SettingItem("startMuted", "Start muted", "On"),
                                    SettingItem("loopVideos", "Loop videos", "On"),
                                    SettingItem("preloadNextClip", "Preload next clip", "On"),
                                ),
                        ),
                )
            }
        }
        composeRule.onNodeWithText("Playback").performClick()
        composeRule.onNodeWithTag("reader-setting-row-preloadNextClip").performScrollTo().assertIsDisplayed()
        val list = composeRule.onNodeWithTag("settings-list")
        val before = list.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()
        assertTrue("The playback page must be scrolled before restoration", before > 0f)

        restoration.emulateSavedInstanceStateRestore()

        composeRule.onNodeWithTag(ReaderAppBarTags.TITLE).assertTextEquals("Playback")
        composeRule.onNodeWithTag("reader-setting-row-preloadNextClip").assertIsDisplayed()
        val restored = list.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()
        assertEquals("Restoring a subpage must retain its saved scroll position", before, restored, 0.001f)

        composeRule.onNodeWithContentDescription("Back").performClick()
        composeRule.onNodeWithText("Playback").performClick()
        val reopened = list.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()
        assertEquals("Explicitly opening a subpage still starts at the top", 0f, reopened, 0.001f)
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
                            "Appearance" to listOf(SettingItem("amoled", "AMOLED black", "Off", checked = false)),
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
        composeRule.onNodeWithTag(ReaderAppBarTags.TITLE).assertTextEquals("Settings")
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
                                        "catalogMode",
                                        "Catalog view",
                                        "List",
                                        SettingKind.CHOICE,
                                        options = listOf("List", "Grid"),
                                    ),
                                ),
                        ),
                    expandedId = "catalogMode",
                    onBack = {},
                )
            }
        }
        composeRule.onNodeWithText("Catalog view").assertExists()
        composeRule.onNodeWithContentDescription("Back").performClick()
        composeRule.onNodeWithTag(ReaderAppBarTags.TITLE).assertTextEquals("Appearance")
        composeRule.onNodeWithText("Catalog view").assertExists()
        composeRule.onNodeWithContentDescription("Back").performClick()
        composeRule.onNodeWithTag(ReaderAppBarTags.TITLE).assertTextEquals("Settings")
        composeRule.onNodeWithText("Catalog view").assertDoesNotExist()
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
    fun preferenceSummariesRemainBelowLabelsAtDefaultAndLargeTextWithoutTruncation() {
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
        assertTrue(value.left == label.left)
        assertTrue("Preference summaries start below the label at default size", value.top >= label.bottom)
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
    fun enabledSitesAndFollowedBoardsOpenTheirActualDirectories() {
        var destination = ""
        composeRule.setContent {
            NextTheme {
                SettingsScreen(groups = emptyList(), enabledSiteCount = 2, onNavigate = { destination = it })
            }
        }
        composeRule.onNodeWithText("Enabled sites").assert(hasText("2")).performClick()
        assertEquals("boards", destination)
        composeRule.onNodeWithText("Followed boards").assert(hasText("Choose boards for Home")).performClick()
        assertEquals("favorites", destination)
        composeRule.onNodeWithText("Choose boards for Following").assertDoesNotExist()
    }
}
