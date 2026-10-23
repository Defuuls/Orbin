package com.orbin.ios

/** Where [StartupCrashes] keeps its count and the crash reports: outside the app's database. */
interface CrashStore {
    var startupCrashes: Int

    fun appendReport(report: String)

    fun reports(): List<String>

    fun clearReports()
}

/**
 * Android's crash-loop guard, on iOS: a crash within [STARTUP_WINDOW_MILLIS] of launch counts
 * towards safe mode, two in a row put the next launch there, and a launch that stays up clears the
 * count. Every crash is kept as a report the reader can save; nothing leaves the device otherwise.
 */
class StartupCrashes(
    private val store: CrashStore,
    private val now: () -> Long,
) {
    private val launchedAt = now()

    /** Whether the last launches crashed while starting: show safe mode instead of the app. */
    val isCrashLooping: Boolean get() = store.startupCrashes >= CRASH_LOOP_THRESHOLD

    fun recordCrash(report: String) {
        if (now() - launchedAt < STARTUP_WINDOW_MILLIS) store.startupCrashes += 1
        store.appendReport(report)
    }

    /** This launch got going: it no longer counts towards safe mode. */
    fun launchSucceeded() {
        if (store.startupCrashes != 0) store.startupCrashes = 0
    }

    /** The recorded crashes as one text to save, or null when there are none. */
    fun report(): String? =
        store
            .reports()
            .takeIf { it.isNotEmpty() }
            ?.joinToString("\n\n${"-".repeat(SEPARATOR_WIDTH)}\n\n")

    /** Safe mode's reset: the count and the reports go with the local data. */
    fun reset() {
        store.startupCrashes = 0
        store.clearReports()
    }

    companion object {
        /** As on Android: how long after launch a crash counts as a startup crash. */
        const val STARTUP_WINDOW_MILLIS = 10_000L
        const val CRASH_LOOP_THRESHOLD = 2
        private const val SEPARATOR_WIDTH = 40
    }
}
