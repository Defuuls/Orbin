package com.orbin.domain.repository

/**
 * Local crash diagnostics. Nothing here is ever transmitted: reports are written encrypted to app
 * storage and only leave the device if the user explicitly exports them.
 *
 * This exists because of the 82-Alioth launch crash, which was diagnosed from a stack trace a user
 * happened to know how to retrieve. Orbin ships no telemetry by design, so the alternative had to
 * be something the user holds and chooses to share.
 */
interface DiagnosticsRepository {
    /**
     * True when consecutive launches have crashed during startup — the app cannot get far enough
     * to be usable, and the UI should offer recovery instead of trying again.
     */
    fun isCrashLooping(): Boolean

    /** Clears the crash-loop count once a launch has stayed up long enough to count as working. */
    fun markLaunchSucceeded()

    /** True when there is anything to export. */
    suspend fun hasReports(): Boolean

    /** The recorded crashes as plain text the user can save, or null when there are none. */
    suspend fun exportReport(): String?

    suspend fun clearReports()

    /**
     * Deletes the local database so a launch blocked by unreadable local state can get past it.
     * Bookmarks, history and downloads are lost; settings are kept. Recovery of last resort.
     */
    suspend fun resetLocalData()
}
