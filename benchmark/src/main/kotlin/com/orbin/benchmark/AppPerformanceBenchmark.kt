package com.orbin.benchmark

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import org.junit.After
import org.junit.Rule
import org.junit.Test

/**
 * Real launcher startup plus populated production reader renderers. The scrolling measurements
 * contain no provider/backend work; every journey asserts that real, visible items actually move.
 */
class AppPerformanceBenchmark {
    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @After
    fun restoreWindow() = resetReaderWindow()

    @Test
    fun coldStartup() = measureStartup(StartupMode.COLD)

    @Test
    fun warmStartup() = measureStartup(StartupMode.WARM)

    @Test
    fun hotStartup() = measureStartup(StartupMode.HOT)

    @Test
    fun catalogListScrollFrames() = measureReader(ReaderJourney.LIST)

    @Test
    fun catalogGridScrollFrames() = measureReader(ReaderJourney.GRID)

    @Test
    fun threadScrollFrames() = measureReader(ReaderJourney.THREAD)

    @Test
    fun filesScrollFrames() = measureReader(ReaderJourney.FILES)

    @Test
    fun twoPaneScrollRetentionFrames() = measureReader(ReaderJourney.TWO_PANE)

    private fun measureReader(journey: ReaderJourney) {
        benchmarkRule.measureRepeated(
            packageName = READER_PACKAGE,
            metrics = listOf(FrameTimingMetric()),
            compilationMode = CompilationMode.Partial(),
            iterations = SCROLL_ITERATIONS,
            setupBlock = { startReaderJourney(journey) },
        ) {
            exerciseReaderJourney(journey)
        }
    }

    private fun measureStartup(mode: StartupMode) {
        benchmarkRule.measureRepeated(
            packageName = READER_PACKAGE,
            metrics = listOf(StartupTimingMetric()),
            compilationMode = CompilationMode.Partial(),
            startupMode = mode,
            iterations = STARTUP_ITERATIONS,
            setupBlock = {
                pressHome()
                // Macrobenchmark drops the shader cache between cold iterations by broadcasting to
                // the app, which can start its process again; a cold start then fails with "must not
                // be running prior to cold start". Kill whatever that left running.
                if (mode == StartupMode.COLD) killProcess()
            },
        ) {
            startActivityAndWait()
        }
    }

    private companion object {
        const val STARTUP_ITERATIONS = 10
        const val SCROLL_ITERATIONS = 5
    }
}
