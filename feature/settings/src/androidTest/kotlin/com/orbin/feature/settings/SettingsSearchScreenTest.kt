package com.orbin.feature.settings

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.orbin.core.designsystem.theme.OrbinTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsSearchScreenTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun blankQueryPromptsForInput() {
        setContent()

        composeTestRule.onNodeWithText("Search for a setting by name").assertIsDisplayed()
    }

    @Test
    fun matchingQueryShowsResultsAndDeepLinksOnTap() {
        var opened: SettingsSection? = null
        setContent(onOpenSection = { opened = it })

        composeTestRule.onNodeWithText("Search").performTextInput("biometric")

        composeTestRule.onNodeWithText("Lock with biometrics").assertIsDisplayed()
        composeTestRule.onNodeWithText("Lock with biometrics").performClick()
        assertThat(opened).isEqualTo(SettingsSection.PRIVACY)
    }

    @Test
    fun unmatchedQueryShowsNoResultsMessage() {
        setContent()

        composeTestRule.onNodeWithText("Search").performTextInput("xyzzy")

        composeTestRule.onNodeWithText("No settings match \"xyzzy\"").assertIsDisplayed()
    }

    private fun setContent(onOpenSection: (SettingsSection) -> Unit = {}) {
        composeTestRule.setContent {
            OrbinTheme {
                SettingsSearchScreen(onBack = {}, onOpenSection = onOpenSection)
            }
        }
    }
}
