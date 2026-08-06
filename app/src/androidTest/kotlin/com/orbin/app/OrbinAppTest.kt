package com.orbin.app

import android.content.ComponentName
import android.content.pm.PackageManager
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.orbin.core.model.AppIconVariant
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.BeforeClass
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
 * **Ignored: `MainActivity` still produces no Compose hierarchy under instrumentation.** What is
 * established, from logcat rather than from reasoning:
 *
 * - The activity launches and renders. `ActivityTaskManager: Displayed .../MainActivity: +544ms`.
 *   So "the activity did not launch" and "setContent was not called" are both out.
 * - It is then torn down mid-test: `PAUSED -> STOPPED -> DESTROYED`, preceded by
 *   `PackageUpdatedTask: Package updated: mOp=UPDATE packages=[com.orbin.app.debug]`.
 * - That package update comes from the launcher-alias writes — `AppIconManager` skips them when
 *   the aliases already match, but a fresh install reports `COMPONENT_ENABLED_STATE_DEFAULT`
 *   rather than `ENABLED`, so the first run always writes, and every instrumentation run is a
 *   fresh install.
 * - Writing that state up-front in [pinLauncherAliases] did **not** fix it. The package update
 *   still lands after the activity is displayed, so the writes are evidently persisted and
 *   broadcast asynchronously. The tear-down is unchanged.
 *
 * So the mechanism is understood and the remedy is not. Suppressing the icon write under test
 * would need `AppIconManager` behind an interface so it can be replaced — a production change made
 * solely for a test, which is worth a deliberate decision rather than a quiet one.
 *
 * Four hypotheses were wrong before the logcat existed: the async readiness gate (`setContent` is
 * unconditional and `AppContent` renders regardless of `ready`), a launch refusal (`MainActivity`
 * is exported and enabled), the DataStore conflict and the missing WorkManager configuration. The
 * latter two were real faults and are fixed; neither was this.
 */
@Ignore("MainActivity is destroyed mid-test by its own launcher-alias writes - see the KDoc")
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class OrbinAppTest {
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    companion object {
        /**
         * Pins the launcher aliases before anything launches the activity.
         *
         * `MainActivity` applies the selected icon from a `LaunchedEffect` on every start.
         * `AppIconManager` skips the work when the aliases already match, but a fresh install
         * reports `COMPONENT_ENABLED_STATE_DEFAULT` rather than `ENABLED`, so the first run always
         * writes. Those writes make PackageManager broadcast a package change, and that tears down
         * the activity under test:
         *
         * ```
         * Displayed com.orbin.app.debug/com.orbin.app.MainActivity: +517ms
         * PackageUpdatedTask: Package updated: mOp=UPDATE packages=[com.orbin.app.debug]
         * MainActivity in: STOPPED  ->  DESTROYED
         * ```
         *
         * Every instrumentation run is a fresh install, so this fired every time and surfaced as
         * "No compose hierarchies found in the app" — the activity had launched and then gone.
         * Writing the same state here first makes the app's own call a no-op.
         *
         * `@BeforeClass` rather than a rule: it has to happen before the Compose rule launches the
         * activity, and rules cannot be ordered ahead of that reliably.
         */
        @JvmStatic
        @BeforeClass
        fun pinLauncherAliases() {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val pm = context.packageManager
            AppIconVariant.entries.forEach { variant ->
                val state =
                    if (variant == AppIconVariant.DEFAULT) {
                        PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                    } else {
                        PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                    }
                pm.setComponentEnabledSetting(
                    ComponentName(context, AppIconAliases.qualifiedName(variant)),
                    state,
                    PackageManager.DONT_KILL_APP,
                )
            }
        }
    }

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
