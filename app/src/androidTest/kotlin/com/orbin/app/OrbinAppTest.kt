package com.orbin.app

import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val READY_TIMEOUT_MS = 20_000L

/**
 * A launch smoke test for the whole app.
 *
 * This is the only test that exercises the real dependency graph — Hilt, the database, the provider
 * registry and the navigation host all have to construct for `MainActivity` to render anything.
 * Wiring mistakes that are invisible to unit tests and to compilation show up here as a blank
 * screen or a crash on launch.
 *
 * It asserts on the first-run wizard, which is what a fresh settings store produces and which needs
 * no network.
 *
 * **Previously ignored, and no longer.** The activity launched and rendered, then was torn down
 * mid-test — `PAUSED -> STOPPED -> DESTROYED`, preceded by `PackageUpdatedTask: Package updated`.
 * That package update came from the launcher-alias writes: `MainActivity` applied the selected app
 * icon on every start, a fresh install always wrote (it reports `COMPONENT_ENABLED_STATE_DEFAULT`
 * rather than `ENABLED`), and every instrumentation run is a fresh install. Pinning the aliases
 * up-front did not help, because the writes are broadcast asynchronously.
 *
 * The remedy at the time would have been to put `AppIconManager` behind an interface purely so a
 * test could replace it. Selectable app icons have since been removed outright, taking the writes
 * with them — a run on CI confirms the `PackageUpdatedTask` is gone — so the documented cause no
 * longer exists and the test is enabled on that basis rather than left disabled on a stale one.
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
        // The wizard opens on its START step, which is this text. "Privacy & network" belongs to
        // the PRIVACY step several taps away, and asserting on it here was a second, independent
        // mistake in this test.
        awaitText("Orbin setup")
    }

    /**
     * The privacy step is where the always-on guarantees are stated. If a DNS switch ever returns
     * to it, this fails — which is the point.
     */
    @Test
    fun theSetupPrivacyStepStatesWhatIsAlwaysOn() {
        awaitText("Orbin setup")
        composeTestRule.onNodeWithText("Privacy").performClick()

        composeTestRule.onNodeWithText("HTTPS only").assertExists()
        composeTestRule.onNodeWithText("DNS over HTTPS").assertExists()
        composeTestRule.onNodeWithText("Always on — pick a resolver in Settings").assertExists()
    }

    /**
     * MainActivity gates its content on an async readiness flag, so nothing is on screen for the
     * first frames. Waiting for the text rather than for idleness is what makes that deterministic.
     */
    private fun awaitText(text: String) {
        composeTestRule.waitUntil(READY_TIMEOUT_MS) {
            composeTestRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
    }
}
