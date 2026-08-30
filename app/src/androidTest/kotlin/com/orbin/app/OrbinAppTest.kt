package com.orbin.app

import android.Manifest
import android.os.Build
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
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
 * **This test was disabled twice, for two different reasons, and both are now gone.**
 *
 * The first was the launcher-alias writes. `MainActivity` applied the selected app icon on every
 * start; a fresh install always wrote, because it reports `COMPONENT_ENABLED_STATE_DEFAULT` rather
 * than `ENABLED`; PackageManager broadcast the change; and the activity went down with it, behind
 * a `PackageUpdatedTask` in the log. Selectable app icons have since been removed, and a CI run
 * confirmed the broadcast is gone with them.
 *
 * The test still failed, on API 35 only, which is what identified the second: the runtime
 * notification-permission request. `MainActivity` asks for `POST_NOTIFICATIONS` as soon as it is
 * ready, the system dialog opens over it, and the activity is paused about 50ms after its first
 * frame — after which there is no Compose hierarchy for the test to query, which is why it failed
 * in under a second rather than at its 20-second timeout. `POST_NOTIFICATIONS` did not exist
 * before API 33, so the API 31 run passed on the same commit; that split is what named the cause.
 *
 * Granting the permission before the activity launches is the fix. It changes no production
 * behaviour — a real first launch still asks — and it is why the rules below are ordered: the
 * grant has to wrap the Compose rule, because that rule is what starts the activity.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class OrbinAppTest {
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    /**
     * Granted before the activity starts, so the request `MainActivity` makes when it becomes
     * ready resolves without a dialog. A no-op below API 33, where the permission does not exist
     * and asking the platform to grant it would throw.
     */
    @get:Rule(order = 1)
    val notificationPermissionRule: TestRule =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            TestRule { base, _ -> base }
        }

    @get:Rule(order = 2)
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
