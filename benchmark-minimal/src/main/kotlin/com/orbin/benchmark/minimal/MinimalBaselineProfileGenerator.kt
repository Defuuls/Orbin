package com.orbin.benchmark.minimal

import androidx.benchmark.macro.junit4.BaselineProfileRule
import org.junit.Rule
import org.junit.Test

private const val PACKAGE_NAME = "com.orbin.minimal"

/** Captures Minimal's startup/bootstrap path for ahead-of-time profile installation. */
class MinimalBaselineProfileGenerator {
    @get:Rule
    val baselineProfileRule = BaselineProfileRule()

    @Test
    fun generate() {
        baselineProfileRule.collect(
            packageName = PACKAGE_NAME,
            includeInStartupProfile = true,
        ) {
            pressHome()
            startActivityAndWait()
            device.waitForIdle()
        }
    }
}
