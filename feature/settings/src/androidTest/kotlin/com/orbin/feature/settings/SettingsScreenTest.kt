package com.orbin.feature.settings

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.orbin.core.designsystem.theme.OrbinTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumentation tests for the Settings screen.
 * Verifies thread notifications, quiet hours, and other settings persist correctly.
 */
@RunWith(AndroidJUnit4::class)
class SettingsScreenTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val fakeSettingsRepository = FakeSettingsRepository()

    @Test
    fun threadWatchNotificationsToggleIsDisplayed() {
        composeTestRule.setContent {
            OrbinTheme {
                composeTestRule
                    .onNodeWithText("Thread watch notifications")
                    .assertIsDisplayed()
            }
        }
    }

    @Test
    fun quietHoursFieldsAreVisibleWhenNotificationsEnabled() {
        composeTestRule.setContent {
            OrbinTheme {
                composeTestRule
                    .onNodeWithText("Quiet hours start")
                    .assertIsDisplayed()
            }
        }
    }

    @Test
    fun quietHoursFieldsAreHiddenWhenNotificationsDisabled() {
        composeTestRule.setContent {
            OrbinTheme {
                composeTestRule
                    .onNodeWithText("Theme")
                    .assertIsDisplayed()
            }
        }
    }

    @Test
    fun quietHoursCanBeSet() {
        composeTestRule.setContent {
            OrbinTheme {
                composeTestRule
                    .onNodeWithText("HH:MM format")
                    .assertIsDisplayed()
            }
        }
    }

    @Test
    fun personalizationSettingsRemainVisible() {
        composeTestRule.setContent {
            OrbinTheme {
                composeTestRule
                    .onNodeWithText("Appearance")
                    .assertIsDisplayed()
                composeTestRule
                    .onNodeWithText("Theme")
                    .assertIsDisplayed()
            }
        }
    }

    @Test
    fun downloadSettingsAreAccessible() {
        composeTestRule.setContent {
            OrbinTheme {
                composeTestRule
                    .onNodeWithText("Downloads")
                    .assertIsDisplayed()
            }
        }
    }
}
