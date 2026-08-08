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
import com.orbin.core.testing.repository.FakeSettingsRepository
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsNotificationsScreenTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var settingsRepository: FakeSettingsRepository

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

    /** Flips the switch in the row titled [label]; the row title itself is not clickable. */
    private fun toggle(label: String) {
        composeTestRule.onNodeWithTag(switchTagFor(label)).performScrollTo().performClick()
        composeTestRule.waitForIdle()
    }

    private fun setContent(initial: AppSettings = AppSettings.Default) {
        settingsRepository = fakeSettingsRepository(initial)
        val viewModel = testSettingsViewModel(settingsRepository)
        composeTestRule.setContent {
            OrbinTheme {
                SettingsNotificationsScreen(onBack = {}, viewModel = viewModel)
            }
        }
    }
}
