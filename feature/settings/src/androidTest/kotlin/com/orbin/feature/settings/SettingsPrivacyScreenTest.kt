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
import com.orbin.core.testing.repository.FakeDnsPrivacyMonitor
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsPrivacyScreenTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val dnsMonitor = FakeDnsPrivacyMonitor()

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

        composeTestRule.onNodeWithTag(switchTagFor("Internal updater")).performScrollTo().performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Check for updates").assertDoesNotExist()
    }

    /** The nested Advanced page is reached from here, not from the Settings hub directly. */
    @Test
    fun advancedRowOpensTheAdvancedPage() {
        var opened = false
        setContent(onOpenAdvanced = { opened = true })

        composeTestRule.onNodeWithText("Advanced").performScrollTo().performClick()

        assertThat(opened).isTrue()
    }

    private fun setContent(
        initial: AppSettings = AppSettings.Default,
        onOpenAdvanced: () -> Unit = {},
    ) {
        val viewModel = testSettingsViewModel(fakeSettingsRepository(initial), dnsMonitor)
        composeTestRule.setContent {
            OrbinTheme {
                SettingsPrivacyScreen(onBack = {}, onOpenAdvanced = onOpenAdvanced, viewModel = viewModel)
            }
        }
    }
}
