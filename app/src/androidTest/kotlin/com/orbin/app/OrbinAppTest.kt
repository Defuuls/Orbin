package com.orbin.app

import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A launch smoke test for the whole app.
 *
 * This is the only test that exercises the real dependency graph — Hilt, the encrypted database,
 * DataStore, the provider registry and the navigation host all have to construct for
 * `MainActivity` to render anything. Plenty of wiring mistakes are invisible to unit tests and to
 * compilation, and show up here as a blank screen or a crash on launch.
 *
 * It deliberately asserts on the first-run wizard rather than the feed: a fresh install has
 * `onboardingCompleted = false`, so that is what actually appears, and it needs no network.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class OrbinAppTest {
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun theAppLaunchesAndShowsFirstRunSetup() {
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Privacy & network").assertExists()
    }

    /**
     * The privacy step is where the always-on guarantees are stated. If it ever regains a DNS
     * switch, this fails — which is the point.
     */
    @Test
    fun theSetupPrivacyStepStatesWhatIsAlwaysOn() {
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("HTTPS only").assertExists()
        composeTestRule.onNodeWithText("DNS over HTTPS").assertExists()
        composeTestRule.onNodeWithText("Always on — pick a resolver in Settings").assertExists()
    }

    @Test
    fun theSetupStepsAreReachable() {
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Ready to browse").assertExists()
    }
}
