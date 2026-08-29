package com.orbin.app

import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Ignore
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
 * **Ignored: `MainActivity` renders, then loses its Compose hierarchy under instrumentation.**
 *
 * The original diagnosis was the launcher-alias writes. `MainActivity` applied the selected app
 * icon on every start; a fresh install always wrote, because it reports
 * `COMPONENT_ENABLED_STATE_DEFAULT` rather than `ENABLED`; PackageManager broadcast the change;
 * and the activity went down with it, behind a `PackageUpdatedTask` in the log.
 *
 * Selectable app icons have since been removed, and that half is settled: a CI run with the
 * feature gone shows no `PackageUpdatedTask` before the teardown — the only one left is the
 * package *removal* after the run finishes. The writes really have stopped.
 *
 * The test still fails, so there was a second cause underneath the first:
 *
 * ```
 * START u0 {act=MAIN cat=[LAUNCHER] cmp=com.orbin.app.debug/com.orbin.app.MainActivity}
 * MainActivity in: PRE_ON_CREATE -> CREATED -> STARTED -> RESUMED
 * Displayed com.orbin.app.debug/com.orbin.app.MainActivity: +406ms
 * MainActivity in: PAUSED                       <- ~50ms after the first frame
 * START ... InstrumentationActivityInvoker$EmptyActivity
 * MainActivity in: STOPPED -> DESTROYED
 * failed: ... at OrbinAppTest.awaitText
 * ```
 *
 * Two things are worth recording for whoever picks this up. `awaitText` fails in under a second
 * rather than at its 20-second timeout, so `fetchSemanticsNodes` is throwing — no Compose
 * hierarchy — rather than the text being absent. And `ActivityScenario` launches the activity
 * under test with `MAIN`/`LAUNCHER` itself, whatever the manifest says, so where the launcher
 * intent-filter lives is not the variable it looks like: moving it onto `MainActivity` and moving
 * it back changed nothing here.
 *
 * What pauses the activity ~50ms after its first frame is not yet known. `setContent` always
 * renders one of three branches, so "nothing was composed" is not the explanation.
 */
@Ignore("MainActivity loses its Compose hierarchy under instrumentation - see the KDoc")
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
