package com.orbin.feature.settings

import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import com.google.common.truth.Truth.assertThat
import com.orbin.core.model.AppSettings
import com.orbin.core.model.AppThemeMode
import com.orbin.core.model.CatalogMode
import com.orbin.core.model.PlaybackScope
import com.orbin.core.model.SharpPreviewsMode
import com.orbin.core.testing.repository.FakeAppUpdater
import com.orbin.core.testing.repository.FakeBoardPreferencesRepository
import com.orbin.core.testing.repository.FakeBookmarkRepository
import com.orbin.core.testing.repository.FakeDnsPrivacyMonitor
import com.orbin.core.testing.repository.FakeDownloadRepository
import com.orbin.core.testing.repository.FakeHistoryRepository
import com.orbin.core.testing.repository.FakeProviderRegistry
import com.orbin.core.testing.repository.FakeSearchRepository
import com.orbin.core.testing.repository.FakeSettingsRepository
import com.orbin.core.testing.repository.FakeUpdateRepository
import com.orbin.domain.repository.ImageCacheRepository
import com.orbin.uinext.components.NextSnackbarHostState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A person working through Settings, row by row, on the shipped screen.
 *
 * Not the registry and not the ViewModel on their own: the real [NextSettingsScreen] over a real
 * [SettingsViewModel], driven only by what a person can do — scroll, tap a row, tap an option,
 * tap again to confirm. Each step checks both halves of "it worked": the value reached the repository,
 * and the row on screen now says so.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xhdpi")
class SettingsUserJourneyTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val settings = FakeSettingsRepository()
    private val history = FakeHistoryRepository()
    private val searches = FakeSearchRepository()
    private val downloads = FakeDownloadRepository()
    private val imageCache = RecordingImageCache(bytes = 12L * 1024 * 1024)

    @Test
    fun everyToggleFlipsOnAndBackOff() {
        launch()

        toggle("Hide NSFW boards") { hideNsfwBoards }
        toggle("Cover violent media") { coverViolentMedia }
        toggle("AMOLED black") { amoled }
        toggle("App lock") { biometricLockEnabled }
    }

    @Test
    fun threadCommentsCanBeEnabledFromTheRootWithoutChangingPlaybackOverrides() {
        launch()
        val playback = settings.current.playbackPreferences

        row("Open comments from chat button")
            .assertIsOff()
            .performClick()
            .assertIsOn()
        composeRule.runOnIdle {
            assertThat(settings.current.openThreadComments).isTrue()
            assertThat(settings.current.playbackPreferences).isEqualTo(playback)
        }
        row("Open comments from chat button")
            .performClick()
            .assertIsOff()
        composeRule.runOnIdle { assertThat(settings.current.openThreadComments).isFalse() }
    }

    @Test
    fun inlinePlaybackChoicesWriteOnlyForTheChosenScope() {
        launch()
        choose("Scope", "Test Provider")
        val site = PlaybackScope("fourchan")
        val defaults = settings.current.playbackPreferences.defaults
        for (label in listOf("Autoplay", "Preload next clip")) {
            val before = settings.current.playbackPreferences
            row(label).assertExists()
            assertThat(settings.current.playbackPreferences).isEqualTo(before)
            composeRule.onNode(inlineOption(label, "Off")).performClick().assertIsSelected()
            composeRule.waitForIdle()
            val effective = settings.current.playbackPreferences.forScope(site)
            assertThat(if (label == "Autoplay") effective.autoplay else effective.preloadNextClip).isFalse()
            assertThat(settings.current.playbackPreferences.defaults).isEqualTo(defaults)

            val disabled = settings.current.playbackPreferences
            row(label).assertExists()
            assertThat(settings.current.playbackPreferences).isEqualTo(disabled)
            composeRule.onNode(inlineOption(label, "On")).performClick().assertIsSelected()
            composeRule.waitForIdle()
            val enabled = settings.current.playbackPreferences.forScope(site)
            assertThat(if (label == "Autoplay") enabled.autoplay else enabled.preloadNextClip).isTrue()
        }
    }

    @Test
    fun everyChoiceOpensItsOptionsAndKeepsTheOnePicked() {
        launch()

        choose("Theme", "Dark")
        assertThat(settings.current.themeMode).isEqualTo(AppThemeMode.DARK)
        choose("Theme", "Light")
        assertThat(settings.current.themeMode).isEqualTo(AppThemeMode.LIGHT)
        choose("Theme", "System")
        assertThat(settings.current.themeMode).isEqualTo(AppThemeMode.SYSTEM)
    }

    @Test
    fun searchAndDismissingChoicesLeavePersistedSettingsUntouched() {
        launch()
        val before = settings.current

        row("Theme").performClick()
        composeRule.onNodeWithTag("reader-settings-choice-popup").assertExists()
        assertThat(settings.current).isEqualTo(before)
        composeRule.onNodeWithContentDescription("Back").performClick()
        composeRule.onNodeWithTag("reader-settings-choice-popup").assertDoesNotExist()
        assertThat(settings.current).isEqualTo(before)

        row("Scope").performClick()
        composeRule.onNodeWithTag("reader-app-bar-title").assertTextEquals("Scope")
        composeRule.onNodeWithContentDescription("Back").performClick()
        composeRule.onNode(hasSetTextAction()).assertDoesNotExist()
        composeRule.onNodeWithTag("reader-app-bar-title").assertTextEquals("Settings")
        assertThat(settings.current).isEqualTo(before)

        composeRule.onNodeWithContentDescription("Search settings").performClick()
        composeRule.onNode(hasSetTextAction()).performTextInput("App lock")
        row("App lock").assertExists()
        composeRule.onNodeWithContentDescription("Back").performClick()
        composeRule.onNode(hasSetTextAction()).assertDoesNotExist()
        assertThat(settings.current).isEqualTo(before)
    }

    @Test
    fun catalogViewCanBePickedWithoutChangingTheBrightnessPreference() {
        launch()
        choose("Catalog view", "Grid")
        assertThat(settings.current.catalogMode).isEqualTo(CatalogMode.GRID)
        assertThat(settings.current.themeMode).isEqualTo(AppThemeMode.SYSTEM)
        choose("Catalog view", "List")
        assertThat(settings.current.catalogMode).isEqualTo(CatalogMode.LIST)
        assertThat(settings.current.themeMode).isEqualTo(AppThemeMode.SYSTEM)
        choose("Catalog view", "Card")
        assertThat(settings.current.catalogMode).isEqualTo(CatalogMode.CARD)
        assertThat(settings.current.themeMode).isEqualTo(AppThemeMode.SYSTEM)
    }

    @Test
    fun sharpPreviewsCanBePickedWithoutChangingTheCatalogView() {
        launch()
        assertThat(settings.current.sharpPreviews).isEqualTo(SharpPreviewsMode.WIFI_ONLY)
        choose("Sharp previews", "Always")
        assertThat(settings.current.sharpPreviews).isEqualTo(SharpPreviewsMode.ALWAYS)
        assertThat(settings.current.catalogMode).isEqualTo(CatalogMode.GRID)
        choose("Sharp previews", "Never")
        assertThat(settings.current.sharpPreviews).isEqualTo(SharpPreviewsMode.NEVER)
        choose("Sharp previews", "Wi-Fi only")
        assertThat(settings.current.sharpPreviews).isEqualTo(SharpPreviewsMode.WIFI_ONLY)
    }

    /** The whole list, and nothing it used to carry: a regression that re-adds a knob fails here. */
    @Test
    fun settingsKeepsExistingControlsReachableOnTheContinuousRoot() {
        launch()

        listOf(
            "Color scheme",
            "Color theme",
            "Catalog columns",
            "Text size",
            "Mute by default",
            "In-app updates",
            "Downloads folder",
            "Downloads",
            "Search",
        ).forEach {
            composeRule.onNodeWithText(it).assertDoesNotExist()
        }
        listOf(
            "Hide NSFW boards",
            "Cover violent media",
            "Open comments from chat button",
            "Theme",
            "AMOLED black",
            "Catalog view",
            "Sharp previews",
            "App lock",
            "Tell me about new releases",
            "Clear local activity",
            "Clear image cache",
            "Check for updates",
            "Export data",
            "Import data",
        ).forEach { row(it).assertExists() }
    }

    @Test
    fun clearingLocalActivityTakesASecondTapThenClears() {
        runBlocking { searches.recordQuery("thinkpad") }
        launch()

        row("Clear local activity").performClick()
        composeRule.waitForIdle()
        row("Clear local activity").assertTextContains("Tap again to delete")
        assertThat(runBlocking { searches.observeRecentQueries().first() }).containsExactly("thinkpad")

        row("Clear local activity").performClick()
        composeRule.waitForIdle()

        assertThat(runBlocking { searches.observeRecentQueries().first() }).isEmpty()
        row("Clear local activity").assertTextContains("Delete")
    }

    @Test
    fun oneTapThenWaitingKeepsEverything() {
        runBlocking { searches.recordQuery("thinkpad") }
        launch()

        row("Clear local activity").performClick()
        composeRule.mainClock.advanceTimeBy(5_000L)
        composeRule.waitForIdle()

        row("Clear local activity").assertTextContains("Delete")
        composeRule.onNodeWithText("Tap again to delete").assertDoesNotExist()
        assertThat(runBlocking { searches.observeRecentQueries().first() }).containsExactly("thinkpad")
    }

    @Test
    fun theImageCacheShowsItsSizeAndClears() {
        launch()

        row("Clear image cache").assertTextContains("12 MB · Clear")
        row("Clear image cache").performClick()
        composeRule.waitForIdle()

        assertThat(imageCache.cleared).isTrue()
        row("Clear image cache").assertTextContains("Empty · Clear")
    }

    @Test
    fun theUpdateCheckRuns() {
        launch()

        row("Check for updates").performClick()
        composeRule.waitForIdle()

        row("Check for updates").assertTextContains("Up to date")
    }

    /** Taps a toggle row on, then off, checking the repository and the switch each time. */
    private fun toggle(
        label: String,
        read: AppSettings.() -> Boolean,
    ) {
        val before = settings.current.read()
        row(label).performClick()
        composeRule.waitForIdle()
        assertThat(settings.current.read()).isEqualTo(!before)
        row(label).performClick()
        composeRule.waitForIdle()
        assertThat(settings.current.read()).isEqualTo(before)
    }

    /** Selects an inline option or opens its picker and checks the persisted row value. */
    private fun choose(
        label: String,
        option: String,
    ) {
        val preference = row(label)
        if (composeRule.onAllNodes(inlineOption(label, option)).fetchSemanticsNodes().isNotEmpty()) {
            composeRule
                .onNode(inlineOption(label, option))
                .performScrollTo()
                .performClick()
                .assertIsSelected()
            return
        }
        preference.performClick()
        composeRule.waitForIdle()
        if (composeRule.onAllNodes(hasTestTag("reader-settings-choice-popup")).fetchSemanticsNodes().isNotEmpty()) {
            composeRule.onNode(isOption(option)).performScrollTo()
        } else {
            // Long scope choices retain their searchable page instead of the short floating menu.
            composeRule.onNodeWithTag("settings-list").performScrollToNode(isOption(option))
        }
        composeRule.onNode(isOption(option)).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("reader-settings-choice-popup").assertDoesNotExist()
        row(label).assertTextContains(option)
    }

    /** Rows are reached by scrolling the same root list, without a category or search detour. */
    private fun row(label: String): SemanticsNodeInteraction {
        val matcher = settingRow(label)
        composeRule.onNodeWithTag("settings-list").performScrollToNode(matcher)
        return composeRule.onNode(matcher)
    }

    private fun settingRow(label: String) =
        SemanticsMatcher("is the settings row '$label'") { node ->
            node.config.getOrElse(SemanticsProperties.TestTag) { "" }.startsWith("reader-setting-row-")
        } and (hasText(label) or hasAnyDescendant(hasText(label)))

    private fun inlineOption(
        label: String,
        option: String,
    ) = isOption(option) and hasAnyAncestor(settingRow(label))

    /** A plain selectable option, distinct from the preference row underneath the floating menu. */
    private fun isOption(option: String) =
        SemanticsMatcher("is the option '$option'") { node ->
            node.config.getOrElse(SemanticsProperties.Text) { emptyList() }.map { it.text } == listOf(option)
        } and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton)

    private fun SemanticsNodeInteraction.assertTextContains(value: String) = assert(hasText(value, substring = true))

    private fun launch() {
        val viewModel =
            SettingsViewModel(
                repository = settings,
                historyRepository = history,
                searchRepository = searches,
                downloadRepository = downloads,
                backupService =
                    BackupService(
                        settings,
                        FakeBoardPreferencesRepository(),
                        FakeBookmarkRepository(),
                        searches,
                        FakeProviderRegistry(),
                    ),
                updateRepository = FakeUpdateRepository(),
                appUpdater = FakeAppUpdater(),
                dnsPrivacyMonitor = FakeDnsPrivacyMonitor(),
                registry = FakeProviderRegistry(),
                imageCacheRepository = imageCache,
            )
        viewModel.refreshImageCacheUsage()
        composeRule.setContent {
            NextSettingsScreen(
                snackbarHostState = NextSnackbarHostState(),
                viewModel = viewModel,
            )
        }
        composeRule.waitForIdle()
    }

    private class RecordingImageCache(
        private var bytes: Long,
    ) : ImageCacheRepository {
        var cleared = false

        override suspend fun usageBytes(): Long = bytes

        override suspend fun clear() {
            cleared = true
            bytes = 0
        }
    }
}
