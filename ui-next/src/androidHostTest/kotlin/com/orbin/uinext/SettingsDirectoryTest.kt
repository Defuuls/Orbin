package com.orbin.uinext

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.navigationevent.DirectNavigationEventInput
import androidx.navigationevent.NavigationEventDispatcher
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    fun groupedRootExposesPreferencesDirectlyAndBackLeavesSettings() {
        var exits = 0
        composeRule.setContent {
            NextTheme {
                SettingsScreen(
                    groups =
                        listOf(
                            "Appearance" to listOf(SettingItem("amoled", "AMOLED black", "Off", checked = false)),
                        ),
                    onBack = { exits++ },
                    bottomBar = { Text("App navigation") },
                )
            }
        }
        composeRule.onNodeWithTag("settings-brand-hero").assertIsDisplayed()
        composeRule.onNodeWithTag("reader-setting-row-amoled").assertIsOff()
        composeRule.onNodeWithTag(ReaderAppBarTags.TITLE).assertTextEquals("Settings")
        composeRule.onNode(hasSetTextAction()).assertDoesNotExist()
        composeRule.onNodeWithText("App navigation").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Back").performClick()
        assertEquals(1, exits)
    }

    @Test
    fun restoringAScrolledGroupedRootKeepsItsPosition() {
        val restoration = StateRestorationTester(composeRule)
        restoration.setContent {
            NextTheme { SettingsScreen(modifier = Modifier.height(320.dp), groups = allGroups) }
        }
        scrollTo("futureSetting")
        val before = scrollPosition()
        assertTrue("The grouped root must be scrolled before restoration", before > 0f)

        restoration.emulateSavedInstanceStateRestore()

        composeRule.onNodeWithTag(ReaderAppBarTags.TITLE).assertTextEquals("Settings")
        composeRule.onNodeWithTag("reader-setting-row-futureSetting").assertIsDisplayed()
        assertEquals("The grouped root retains its saved scroll position", before, scrollPosition(), 0.001f)
    }

    @Test
    fun settingsSearchReturnsToTheSameRootPositionAndAppNavigation() {
        var exits = 0
        composeRule.setContent {
            NextTheme {
                SettingsScreen(
                    modifier = Modifier.height(320.dp),
                    groups = allGroups,
                    onBack = { exits++ },
                    bottomBar = { Text("App navigation") },
                )
            }
        }
        scrollTo("futureSetting")
        val before = scrollPosition()
        composeRule.onNodeWithText("App navigation").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Search settings").performClick()
        composeRule.onNode(hasSetTextAction()).performTextInput("AMOLED")
        composeRule.onNodeWithTag("settings-list").performScrollToNode(hasTestTag("reader-setting-row-amoled"))
        composeRule.onNodeWithTag("reader-setting-row-amoled").assertIsDisplayed()
        composeRule.onNodeWithText("App navigation").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Back").performClick()

        composeRule.onNode(hasSetTextAction()).assertDoesNotExist()
        composeRule.onNodeWithTag("reader-setting-row-futureSetting").assertIsDisplayed()
        composeRule.onNodeWithText("App navigation").assertIsDisplayed()
        assertEquals(before, scrollPosition(), 0.001f)
        assertEquals(0, exits)
    }

    @Test
    fun longPickerBackRestoresRootScrollWithoutSavingAnOption() {
        var selections = 0
        composeRule.setContent {
            NextTheme {
                SettingsScreen(
                    modifier = Modifier.height(320.dp),
                    groups = allGroups,
                    onSelectOption = { _, _ -> selections++ },
                    onBack = {},
                    bottomBar = { Text("App navigation") },
                )
            }
        }
        scrollTo("playbackScope")
        val before = scrollPosition()
        composeRule.onNodeWithTag("reader-setting-row-playbackScope").performClick()
        composeRule.onNodeWithTag(ReaderAppBarTags.TITLE).assertTextEquals("Scope")
        composeRule.onNodeWithText("App navigation").assertDoesNotExist()
        composeRule.onNode(hasSetTextAction()).performTextInput("board12")
        composeRule.onNodeWithText("Site · /board12/").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Back").performClick()

        composeRule.onNodeWithTag(ReaderAppBarTags.TITLE).assertTextEquals("Settings")
        composeRule.onNodeWithTag("reader-setting-row-playbackScope").assertIsDisplayed()
        composeRule.onNodeWithText("App navigation").assertIsDisplayed()
        assertEquals(before, scrollPosition(), 0.001f)
        assertEquals(0, selections)
    }

    @Test
    fun systemBackClosesThePickerThenSearchBeforeTheHostReceivesBack() {
        var hostBacks = 0
        lateinit var dispatcher: NavigationEventDispatcher
        composeRule.setContent {
            dispatcher = requireNotNull(LocalNavigationEventDispatcherOwner.current).navigationEventDispatcher
            // NavHost and the iOS root use this same modern navigation handler.
            NavigationBackHandler(
                state = rememberNavigationEventState(NavigationEventInfo.None),
                isBackEnabled = true,
                onBackCompleted = { hostBacks++ },
            )
            NextTheme { SettingsScreen(groups = allGroups) }
        }
        val input = DirectNavigationEventInput()
        composeRule.runOnUiThread { dispatcher.addInput(input) }
        composeRule.onNodeWithContentDescription("Search settings").performClick()
        composeRule.onNode(hasSetTextAction()).performTextInput("Scope")
        composeRule.onNodeWithTag("settings-list").performScrollToNode(hasTestTag("reader-setting-row-playbackScope"))
        composeRule.onNodeWithTag("reader-setting-row-playbackScope").performClick()
        composeRule.waitForIdle()
        composeRule.runOnUiThread { input.backCompleted() }
        composeRule.waitForIdle()
        assertEquals(0, hostBacks)
        composeRule.onNodeWithTag(ReaderAppBarTags.TITLE).assertTextEquals("Settings")
        composeRule.onNode(hasSetTextAction()).assert(hasText("Scope"))

        composeRule.runOnUiThread { input.backCompleted() }
        composeRule.waitForIdle()
        composeRule.onNode(hasSetTextAction()).assertDoesNotExist()
        assertEquals(0, hostBacks)
        composeRule.runOnUiThread { input.backCompleted() }
        composeRule.waitForIdle()
        assertEquals(1, hostBacks)
        composeRule.runOnUiThread { dispatcher.removeInput(input) }
    }

    @Test
    fun everySuppliedRowAppearsOnceAndUnknownPlatformActionsRemainReachable() {
        val rows = allGroups.flatMap { it.second }
        val sections = settingsSections(allGroups)
        val grouped = sections.values.flatten()
        assertEquals("Grouping must neither omit nor duplicate a supplied row", rows.size, grouped.size)
        assertEquals(rows.toSet(), grouped.toSet())
        assertEquals(listOf(rows.last()), sections["other"])
        assertTrue(sections.getValue("boards").any { it.id == "feedColumns" })
        assertEquals(
            setOf("clearActivity", "clearImageCache", "videoCacheLimit", "clearVideoCache"),
            sections.getValue("storage").map { it.id }.toSet(),
        )
        var activated = ""
        composeRule.setContent {
            NextTheme {
                SettingsScreen(
                    modifier = Modifier.height(320.dp),
                    groups = allGroups,
                    onActivate = { activated = it.id },
                )
            }
        }
        rows.forEach { row ->
            scrollTo(row.id)
            composeRule.onAllNodesWithTag("reader-setting-row-${row.id}").assertCountEquals(1)
        }
        composeRule.onNodeWithTag("reader-setting-row-futureSetting").performClick()
        assertEquals("futureSetting", activated)
    }

    @Test
    fun chatButtonPreferenceIsDiscoverableUnderThreadsAndInSearch() {
        val row =
            SettingItem(
                "openThreadComments",
                "Open comments from chat button",
                "Off",
                checked = false,
                hint = "Off opens thread videos; on opens comments.",
            )
        val groups = listOf("Playback" to listOf(row))
        assertEquals(listOf(row), settingsSections(groups)["threads"])
        assertEquals(listOf(row), filterSettings(groups, "thread videos").flatMap { it.second })
        assertEquals(listOf(row), filterSettings(groups, "chat comments").flatMap { it.second })
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun summariesWrapBelowLabelsAtDefaultAndLargeTextInBothDirections() {
        var scale by mutableStateOf(1f)
        var direction by mutableStateOf(LayoutDirection.Ltr)
        composeRule.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides direction) {
                NextTheme(fontScale = scale) {
                    SettingsScreen(
                        modifier = Modifier.width(320.dp),
                        groups =
                            listOf(
                                "Settings" to
                                    listOf(
                                        SettingItem(
                                            "scopeInfo",
                                            "Playback defaults",
                                            "App defaults wrap beneath the preference label",
                                            SettingKind.INFO,
                                        ),
                                    ),
                            ),
                    )
                }
            }
        }
        for (fontScale in listOf(1f, 2f)) {
            for (layoutDirection in listOf(LayoutDirection.Ltr, LayoutDirection.Rtl)) {
                composeRule.runOnIdle {
                    scale = fontScale
                    direction = layoutDirection
                }
                scrollTo("scopeInfo")
                val label = composeRule.onNodeWithText("Playback defaults").getUnclippedBoundsInRoot()
                val summary = composeRule.onNodeWithText("App defaults wrap beneath the preference label")
                val value = summary.getUnclippedBoundsInRoot()
                assertTrue("Summaries remain below labels at $fontScale/$layoutDirection", value.top >= label.bottom)
                assertTrue(
                    "Labels and summaries share their reading edge at $layoutDirection",
                    if (layoutDirection == LayoutDirection.Ltr) {
                        value.left == label.left
                    } else {
                        value.right == label.right
                    },
                )
                val layouts = mutableListOf<TextLayoutResult>()
                summary.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
                assertFalse("The complete summary must wrap without truncation", layouts.single().hasVisualOverflow)
            }
        }
    }

    @Test
    fun navigationShortcutsOpenTheirActualDestinations() {
        val visited = mutableListOf<String>()
        composeRule.setContent {
            NextTheme { SettingsScreen(groups = emptyList(), enabledSiteCount = 2, onNavigate = { visited += it }) }
        }
        listOf(
            "Enabled sites" to "boards",
            "Followed boards" to "favorites",
            "View browsing history" to "history",
            "Downloads" to "downloads",
            "Help" to "help",
        ).forEach { (label, destination) ->
            composeRule.onNodeWithTag("settings-list").performScrollToNode(hasText(label))
            val shortcut = composeRule.onNodeWithText(label)
            if (destination == "boards") shortcut.assert(hasText("2"))
            if (destination == "favorites") shortcut.assert(hasText("Choose boards for Home"))
            shortcut.performClick()
            assertEquals(destination, visited.last())
        }
        assertEquals(5, visited.size)
        composeRule.onNodeWithText("Choose boards for Following").assertDoesNotExist()
    }

    private fun scrollTo(id: String) {
        composeRule.onNodeWithTag("settings-list").performScrollToNode(hasTestTag("reader-setting-row-$id"))
        composeRule.onNodeWithTag("reader-setting-row-$id").assertIsDisplayed()
    }

    private fun scrollPosition(): Float =
        composeRule
            .onNodeWithTag("settings-list")
            .fetchSemanticsNode()
            .config[SemanticsProperties.VerticalScrollAxisRange]
            .value()

    private val allGroups =
        listOf(
            "Platform settings" to
                listOf(
                    SettingItem(
                        "themeMode",
                        "Theme",
                        "System",
                        SettingKind.CHOICE,
                        options = listOf("System", "Light", "Dark"),
                    ),
                    SettingItem("amoled", "AMOLED black", "Off", checked = false),
                    SettingItem(
                        "catalogMode",
                        "Catalog view",
                        "List",
                        SettingKind.CHOICE,
                        options = listOf("List", "Grid", "Card"),
                    ),
                    SettingItem("feedColumns", "Catalog columns", "Automatic", SettingKind.INFO),
                    SettingItem("openThreadComments", "Open comments from chat button", "Off", checked = false),
                    SettingItem(
                        "playbackScope",
                        "Scope",
                        "App defaults",
                        SettingKind.CHOICE,
                        options = listOf("App defaults") + (1..12).map { "Site · /board$it/" },
                    ),
                    SettingItem("autoplay", "Autoplay", "On"),
                    SettingItem("startMuted", "Start muted", "On"),
                    SettingItem("loopVideos", "Loop videos", "On"),
                    SettingItem("preloadNextClip", "Preload next clip", "On"),
                    SettingItem("inheritPlayback", "Use inherited playback", "On"),
                    SettingItem("resetPlayback", "Reset playback", "Reset", SettingKind.ACTION),
                    SettingItem("hideNsfw", "Hide NSFW boards", "On"),
                    SettingItem("coverViolent", "Cover violent media", "On"),
                    SettingItem("biometric", "App lock", "Off"),
                    SettingItem("screenPrivacy", "Privacy shield", "On"),
                    SettingItem("dohProvider", "DNS provider", "System", SettingKind.INFO),
                    SettingItem("strictDns", "Strict DNS", "Off"),
                    SettingItem("saveSearchHistory", "Save search history", "On"),
                    SettingItem("saveBrowsingHistory", "Save browsing history", "On"),
                    SettingItem("clearActivity", "Clear local activity", "Delete", SettingKind.ACTION),
                    SettingItem("clearImageCache", "Clear image cache", "Clear", SettingKind.ACTION),
                    SettingItem("videoCacheLimit", "Video cache limit", "256 MB", SettingKind.INFO),
                    SettingItem("clearVideoCache", "Clear video cache", "Clear", SettingKind.ACTION),
                    SettingItem("exportBackup", "Export data", "Export", SettingKind.ACTION),
                    SettingItem("importBackup", "Import data", "Import", SettingKind.ACTION),
                    SettingItem("updateOnLaunch", "Tell me about new releases", "On"),
                    SettingItem("checkUpdates", "Check for updates", "Up to date", SettingKind.ACTION),
                    SettingItem("version", "Version", "1.0", SettingKind.INFO),
                    SettingItem("privacy", "Privacy policy", "Open", SettingKind.ACTION),
                    SettingItem("contact", "Contact", "Open", SettingKind.ACTION),
                    SettingItem("futureSetting", "New platform setting", "Open", SettingKind.ACTION),
                ),
        )
}
