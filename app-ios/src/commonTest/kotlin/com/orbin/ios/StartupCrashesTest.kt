package com.orbin.ios

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StartupCrashesTest {
    private class MemoryStore : CrashStore {
        override var startupCrashes = 0
        val kept = mutableListOf<String>()

        override fun appendReport(report: String) {
            kept += report
        }

        override fun reports(): List<String> = kept

        override fun clearReports() = kept.clear()
    }

    private var clock = 0L
    private val store = MemoryStore()

    private fun launch() = StartupCrashes(store) { clock }

    @Test
    fun twoStartupCrashesInARowStartTheNextLaunchInSafeMode() {
        launch().recordCrash("first")
        assertFalse(launch().isCrashLooping)
        launch().recordCrash("second")

        assertTrue(launch().isCrashLooping)
        assertEquals("first\n\n${"-".repeat(40)}\n\nsecond", launch().report())
    }

    @Test
    fun aCrashLongAfterLaunchIsKeptButDoesNotCount() {
        val running = launch()
        clock += StartupCrashes.STARTUP_WINDOW_MILLIS
        running.recordCrash("later")

        assertEquals(0, store.startupCrashes)
        assertEquals(listOf("later"), store.kept)
    }

    @Test
    fun aLaunchThatStaysUpClearsTheCountAndResetClearsTheReports() {
        launch().recordCrash("one")
        launch().recordCrash("two")
        launch().launchSucceeded()
        assertFalse(launch().isCrashLooping)

        launch().reset()
        assertNull(launch().report())
    }
}
