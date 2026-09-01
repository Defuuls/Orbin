package com.orbin.benchmark

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test

private const val BENCHMARK_PACKAGE_NAME = "com.orbin.app"
private const val BENCHMARK_WAIT_TIMEOUT_MS = 10_000L

/**
 * Repeatable performance checks for the two paths most affected by the efficiency audit: process
 * startup and scrolling the subscribed feed. Macrobenchmark records frame timing through Perfetto,
 * so regressions can be compared on a physical/unlocked benchmark device instead of inferred from
 * code inspection alone.
 */
class AppPerformanceBenchmark {
    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun coldStartup() {
        benchmarkRule.measureRepeated(
            packageName = BENCHMARK_PACKAGE_NAME,
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
    fun subscribedFeedScrollFrames() {
        benchmarkRule.measureRepeated(
            packageName = BENCHMARK_PACKAGE_NAME,
            metrics = listOf(FrameTimingMetric()),
            compilationMode = CompilationMode.Partial(),
            iterations = SCROLL_ITERATIONS,
            setupBlock = {
                pressHome()
                startActivityAndWait()
                device.wait(Until.hasObject(By.pkg(BENCHMARK_PACKAGE_NAME).depth(0)), BENCHMARK_WAIT_TIMEOUT_MS)
            },
        ) {
            val feed = device.findObject(By.scrollable(true)) ?: return@measureRepeated
            repeat(SCROLL_PASSES) {
                feed.fling(Direction.DOWN)
                device.waitForIdle()
            }
            repeat(SCROLL_PASSES) {
                feed.fling(Direction.UP)
                device.waitForIdle()
            }
        }
    }

    private companion object {
        const val STARTUP_ITERATIONS = 10
        const val SCROLL_ITERATIONS = 5
        const val SCROLL_PASSES = 3
    }
}
