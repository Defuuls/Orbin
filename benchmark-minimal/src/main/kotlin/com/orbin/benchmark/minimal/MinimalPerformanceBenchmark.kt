package com.orbin.benchmark.minimal

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test

private const val PACKAGE_NAME = "com.orbin.minimal"
private const val WAIT_TIMEOUT_MS = 10_000L

/** Perfetto-backed checks for Minimal's cold launch and first-run board chooser path. */
class MinimalPerformanceBenchmark {
    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun coldStartup() {
        benchmarkRule.measureRepeated(
            packageName = PACKAGE_NAME,
            metrics = listOf(StartupTimingMetric()),
            compilationMode = CompilationMode.Partial(),
            startupMode = StartupMode.COLD,
            iterations = STARTUP_ITERATIONS,
            setupBlock = { pressHome() },
        ) {
            startActivityAndWait()
        }
    }

    @Test
    fun firstRunBoardsFrames() {
        benchmarkRule.measureRepeated(
            packageName = PACKAGE_NAME,
            metrics = listOf(FrameTimingMetric()),
            compilationMode = CompilationMode.Partial(),
            iterations = FRAME_ITERATIONS,
            setupBlock = {
                pressHome()
                killProcess()
            },
        ) {
            startActivityAndWait()
            device.wait(Until.hasObject(By.pkg(PACKAGE_NAME).depth(0)), WAIT_TIMEOUT_MS)
            device.waitForIdle()
        }
    }

    private companion object {
        const val STARTUP_ITERATIONS = 10
        const val FRAME_ITERATIONS = 8
    }
}
