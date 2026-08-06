package com.orbin.feature.settings

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.orbin.core.designsystem.theme.OrbinTheme
import com.orbin.core.model.AppSettings
import com.orbin.core.testing.repository.FakeBoardPreferencesRepository
import com.orbin.core.testing.repository.FakeBookmarkRepository
import com.orbin.core.testing.repository.FakeDnsPrivacyMonitor
import com.orbin.core.testing.repository.FakeDownloadRepository
import com.orbin.core.testing.repository.FakeHistoryRepository
import com.orbin.core.testing.repository.FakeProviderRegistry
import com.orbin.core.testing.repository.FakeSearchRepository
import com.orbin.core.testing.repository.FakeSettingsRepository
import com.orbin.core.testing.repository.FakeUpdateRepository
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumentation tests for the Settings screen.
 *
 * The screen takes its ViewModel as a defaulted parameter, so these build a real one over in-memory
 * repositories rather than standing up Hilt. That keeps the subject the screen and its wiring to
 * the ViewModel, which is the part these tests can actually speak to.
 */
@RunWith(AndroidJUnit4::class)
class SettingsScreenTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val dnsMonitor = FakeDnsPrivacyMonitor()
    private lateinit var settingsRepository: FakeSettingsRepository

    @Test
    fun theSectionsAreRendered() {
        setContent()

        composeTestRule.onNodeWithText("Content").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Appearance").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Network & privacy").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Storage").performScrollTo().assertIsDisplayed()
    }

    /** A switch that does not reach the repository is decoration; this is the wiring under test. */
    @Test
    fun togglingAnOptionWritesItThrough() {
        setContent()

        toggle("Thread watch notifications")

        composeTestRule.waitForIdle()
        assertThat(settingsRepository.current.threadWatchNotificationsEnabled).isFalse()
    }

    /** Quiet hours only mean anything while watch notifications are on, so they are gated on it. */
    @Test
    fun quietHoursAppearOnlyWhileWatchNotificationsAreEnabled() {
        setContent()

        composeTestRule.onNodeWithText("Quiet hours start").performScrollTo().assertIsDisplayed()

        toggle("Thread watch notifications")
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Quiet hours start").assertDoesNotExist()
    }

    /** HTTPS-only is shown for transparency and must read as on without being switchable. */
    @Test
    fun httpsOnlyIsShownAsAlwaysEnforced() {
        setContent()

        composeTestRule.onNodeWithText("Always enforced").performScrollTo().assertIsDisplayed()
    }

    /**
     * Encrypted DNS has no off switch, so this notice is the only way a user learns their lookups
     * have stopped being private. It has to actually change when the monitor says so.
     */
    @Test
    fun theDnsNoticeReflectsWhetherLookupsAreEncrypted() {
        setContent()

        composeTestRule
            .onNodeWithText("Encrypted DNS is always on", substring = true)
            .performScrollTo()
            .assertIsDisplayed()

        dnsMonitor.setFallbackActive(true)
        composeTestRule.waitForIdle()

        composeTestRule
            .onNodeWithText("your DNS is not private right now", substring = true)
            .performScrollTo()
            .assertIsDisplayed()
    }

    /** The "Check for updates" row is gated on the internal-updater toggle. */
    @Test
    fun theUpdateCheckAppearsOnlyWhileTheUpdaterIsEnabled() {
        setContent()

        composeTestRule.onNodeWithText("Check for updates").performScrollTo().assertIsDisplayed()

        toggle("Internal updater")
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Check for updates").assertDoesNotExist()
    }

    /** Flips the switch in the row titled [label]; the row title itself is not clickable. */
    private fun toggle(label: String) {
        composeTestRule.onNodeWithTag(switchTagFor(label)).performScrollTo().performClick()
        composeTestRule.waitForIdle()
    }

    private fun setContent(initial: AppSettings = AppSettings.Default) {
        settingsRepository = FakeSettingsRepository(initial)
        val viewModel =
            SettingsViewModel(
                repository = settingsRepository,
                historyRepository = FakeHistoryRepository(),
                searchRepository = FakeSearchRepository(),
                downloadRepository = FakeDownloadRepository(),
                backupService =
                    BackupService(
                        settingsRepository,
                        FakeBoardPreferencesRepository(),
                        FakeBookmarkRepository(),
                        FakeSearchRepository(),
                        FakeProviderRegistry(),
                    ),
                updateRepository = FakeUpdateRepository(),
                dnsPrivacyMonitor = dnsMonitor,
                registry = FakeProviderRegistry(),
            )
        composeTestRule.setContent {
            OrbinTheme {
                SettingsScreen(
                    onBack = {},
                    onOpenDownloads = {},
                    onOpenSubscriptions = {},
                    onOpenSetup = {},
                    viewModel = viewModel,
                )
            }
        }
    }
}
