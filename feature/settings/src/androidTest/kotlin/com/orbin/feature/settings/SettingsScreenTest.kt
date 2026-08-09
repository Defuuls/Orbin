package com.orbin.feature.settings

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.orbin.core.designsystem.theme.OrbinTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumentation tests for the Settings hub. The hub itself only lists categories and forwards
 * taps to navigation callbacks — each category's actual options are covered by that category's
 * own screen test (e.g. [SettingsNotificationsScreenTest], [SettingsPrivacyScreenTest]).
 */
@RunWith(AndroidJUnit4::class)
class SettingsScreenTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun everyCategoryIsRendered() {
        setContent()

        composeTestRule.onNodeWithText("Content & Feed").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Notifications").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Appearance").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Media & Playback").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Privacy & Network").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Storage & Backup").performScrollTo().assertIsDisplayed()
    }

    /** Tapping a category is the whole job of this row; the callback firing is what matters. */
    @Test
    fun tappingACategoryNavigatesToIt() {
        var opened = false
        setContent(onOpenPrivacy = { opened = true })

        composeTestRule.onNodeWithText("Privacy & Network").performScrollTo().performClick()

        assertThat(opened).isTrue()
    }

    @Test
    fun tappingSearchOpensSettingsSearch() {
        var opened = false
        setContent(onOpenSearch = { opened = true })

        composeTestRule.onNodeWithContentDescription("Search settings").performClick()

        assertThat(opened).isTrue()
    }

    private fun setContent(
        onOpenContent: () -> Unit = {},
        onOpenNotifications: () -> Unit = {},
        onOpenAppearance: () -> Unit = {},
        onOpenMedia: () -> Unit = {},
        onOpenPrivacy: () -> Unit = {},
        onOpenStorage: () -> Unit = {},
        onOpenSearch: () -> Unit = {},
    ) {
        val viewModel = testSettingsViewModel(fakeSettingsRepository())
        composeTestRule.setContent {
            OrbinTheme {
                SettingsScreen(
                    onBack = {},
                    onOpenContent = onOpenContent,
                    onOpenNotifications = onOpenNotifications,
                    onOpenAppearance = onOpenAppearance,
                    onOpenMedia = onOpenMedia,
                    onOpenPrivacy = onOpenPrivacy,
                    onOpenStorage = onOpenStorage,
                    onOpenSearch = onOpenSearch,
                    viewModel = viewModel,
                )
            }
        }
    }
}
