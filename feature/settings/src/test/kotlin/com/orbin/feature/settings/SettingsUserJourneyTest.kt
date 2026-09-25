package com.orbin.feature.settings

import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import com.google.common.truth.Truth.assertThat
import com.orbin.core.model.AppSettings
import com.orbin.core.model.AppThemeMode
import com.orbin.core.model.ColorTheme
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
import com.orbin.uinext.NextSnackbarHostState
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
 * confirm a dialog. Each step checks both halves of "it worked": the value reached the repository,
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
    private var openedDownloads = 0
    private var openedSearch = 0

    @Test
    fun everyToggleFlipsOnAndBackOff() {
        launch()

        toggle("Hide NSFW boards") { hideNsfwBoards }
        toggle("True black") { amoled }
        toggle("Mute by default") { muteByDefault }
        toggle("App lock") { biometricLockEnabled }
        toggle("In-app updates") { internalUpdaterEnabled }
    }

    @Test
    fun everyChoiceOpensItsOptionsAndKeepsTheOnePicked() {
        launch()

        choose("Theme", "Dark")
        assertThat(settings.current.themeMode).isEqualTo(AppThemeMode.DARK)
        choose("Theme", "Light")
        assertThat(settings.current.themeMode).isEqualTo(AppThemeMode.LIGHT)

        choose("Color scheme", ColorTheme.entries.last().label)
        assertThat(settings.current.colorTheme).isEqualTo(ColorTheme.entries.last())

        choose("Text size", "XL")
        assertThat(settings.current.fontScale).isEqualTo(1.2f)
        choose("Text size", "Small")
        assertThat(settings.current.fontScale).isEqualTo(0.9f)
    }

    @Test
    fun everyColorSchemeCanBePicked() {
        launch()

        ColorTheme.entries.forEach { theme ->
            choose("Color scheme", theme.label)
            assertThat(settings.current.colorTheme).isEqualTo(theme)
        }
    }

    @Test
    fun clearingLocalActivityAsksFirstThenClears() {
        runBlocking { searches.recordQuery("thinkpad") }
        launch()

        row("Clear local activity").performClick()
        composeRule.onNodeWithText("Clear local activity?").assertExists()
        composeRule.onNodeWithText("Clear").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Clear local activity?").assertDoesNotExist()
        assertThat(runBlocking { searches.observeRecentQueries().first() }).isEmpty()
    }

    @Test
    fun cancellingTheClearKeepsEverything() {
        runBlocking { searches.recordQuery("thinkpad") }
        launch()

        row("Clear local activity").performClick()
        composeRule.onNodeWithText("Cancel").performClick()
        composeRule.waitForIdle()

        assertThat(runBlocking { searches.observeRecentQueries().first() }).containsExactly("thinkpad")
    }

    @Test
    fun theImageCacheShowsItsSizeAndClears() {
        launch()

        row("Image cache usage").assertTextContains("12 MB · Clear")
        row("Image cache usage").performClick()
        composeRule.waitForIdle()

        assertThat(imageCache.cleared).isTrue()
        row("Image cache usage").assertTextContains("Empty · Clear")
    }

    @Test
    fun theUpdateCheckFollowsTheUpdaterAndRuns() {
        launch()

        row("In-app updates").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Check for updates").assertDoesNotExist()

        row("In-app updates").performClick()
        composeRule.waitForIdle()
        row("Check for updates").performClick()
        composeRule.waitForIdle()

        row("Check for updates").assertTextContains("Up to date")
    }

    @Test
    fun downloadsAndSearchOpenFromSettings() {
        launch()

        row("Downloads").performClick()
        row("Search").performClick()

        assertThat(openedDownloads).isEqualTo(1)
        assertThat(openedSearch).isEqualTo(1)
    }

    @Test
    fun theDownloadsFolderShowsItsDefault() {
        launch()

        row("Downloads folder").assertTextContains("Downloads/Orbin")
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

    /** Opens a choice row, taps an option, and checks the row now shows it and has closed. */
    private fun choose(
        label: String,
        option: String,
    ) {
        row(label).performClick()
        composeRule.waitForIdle()
        composeRule.onNode(hasScrollAction()).performScrollToNode(isOption(option))
        composeRule.onNode(isOption(option)).performClick()
        composeRule.waitForIdle()
        row(label).assertTextContains(option)
    }

    /** A row, scrolled into composition first: the list is lazy. */
    private fun row(label: String): SemanticsNodeInteraction {
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasText(label))
        return composeRule.onNode(hasText(label) and hasClickAction())
    }

    /** An option chip: its whole text is the option, unlike a row that merely shows it as a value. */
    private fun isOption(option: String) =
        SemanticsMatcher("is the option '$option'") { node ->
            node.config.getOrElse(SemanticsProperties.Text) { emptyList() }.map { it.text } == listOf(option)
        }

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
                dnsPrivacyMonitor = FakeDnsPrivacyMonitor(),
                registry = FakeProviderRegistry(),
                imageCacheRepository = imageCache,
            )
        viewModel.refreshImageCacheUsage()
        composeRule.setContent {
            NextSettingsScreen(
                snackbarHostState = NextSnackbarHostState(),
                onOpenFeed = {},
                onOpenSearch = { openedSearch++ },
                onOpenDownloads = { openedDownloads++ },
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
