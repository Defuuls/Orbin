package com.orbin.benchmark

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test

private const val PACKAGE_NAME = "com.orbin.app"
private const val WAIT_TIMEOUT_MS = 10_000L

/**
 * Records the classes and methods used on Orbin's startup path so ART can compile them ahead of
 * time, rather than interpreting them on a user's first launch.
 *
 * This needs real hardware — a rooted emulator or an unlocked physical device — so it cannot run
 * in CI and is not wired into any CI workflow. Run it deliberately:
 *
 * ```
 * ./gradlew :app:generateReleaseBaselineProfile
 * ```
 *
 * The result lands in `app/src/release/generated/baselineProfiles/` and is **committed**, because
 * nothing rebuilds it automatically. Re-record it when startup or the feed changes shape; a stale
 * profile is not harmful, only progressively less useful.
 */
class BaselineProfileGenerator {
    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun startupAndFeed() {
        rule.collect(packageName = PACKAGE_NAME) {
            pressHome()
            startActivityAndWait()

            // Startup alone would profile a screen the user leaves immediately. The subscribed
            // feed is where they land and scroll, so its first frames belong in the profile too.
            device.wait(Until.hasObject(By.pkg(PACKAGE_NAME).depth(0)), WAIT_TIMEOUT_MS)
            device.findObject(By.scrollable(true))?.let { feed ->
                repeat(SCROLL_PASSES) {
                    feed.fling(Direction.DOWN)
                    device.waitForIdle()
                }
            }
        }
    }

    private companion object {
        const val SCROLL_PASSES = 3
    }
}
