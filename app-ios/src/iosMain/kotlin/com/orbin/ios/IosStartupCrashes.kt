package com.orbin.ios

import com.orbin.data.database.OrbinDatabase
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSBundle
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDefaults
import platform.Foundation.NSUserDomainMask
import platform.UIKit.UIDevice
import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.ReportUnhandledExceptionHook
import kotlin.native.setUnhandledExceptionHook
import kotlin.native.terminateWithUnhandledException
import kotlin.time.Clock

/**
 * The app's [StartupCrashes], kept in the standard user defaults rather than the database: safe
 * mode has to work when the database is what keeps the app from starting.
 */
internal object IosStartupCrashes {
    val crashes = StartupCrashes(UserDefaultsCrashStore) { nowMillis() }

    private var installed = false

    /**
     * Records every uncaught Kotlin exception as a crash report before the app goes down, as
     * Android's uncaught-exception handler does. Crashes outside Kotlin are not seen.
     */
    @OptIn(ExperimentalNativeApi::class)
    fun install() {
        if (installed) return
        installed = true
        var previous: ReportUnhandledExceptionHook? = null
        previous =
            setUnhandledExceptionHook { throwable ->
                runCatching { crashes.recordCrash(format(throwable)) }
                previous?.invoke(throwable) ?: terminateWithUnhandledException(throwable)
            }
    }

    /** Safe mode's reset: the database files and the crash records go; settings stay. */
    @OptIn(ExperimentalForeignApi::class)
    fun resetLocalData() {
        val directory =
            NSFileManager.defaultManager
                .URLForDirectory(
                    directory = NSApplicationSupportDirectory,
                    inDomain = NSUserDomainMask,
                    appropriateForURL = null,
                    create = false,
                    error = null,
                )?.path
        if (directory != null) {
            listOf("", "-wal", "-shm").forEach { suffix ->
                NSFileManager.defaultManager.removeItemAtPath("$directory/${OrbinDatabase.NAME}$suffix", error = null)
            }
        }
        crashes.reset()
    }

    private fun format(throwable: Throwable): String =
        buildString {
            appendLine("time: ${Clock.System.now()}")
            appendLine(
                "app: ${info("OrbinVersionName") ?: info("CFBundleShortVersionString")} (${info("CFBundleVersion")})",
            )
            appendLine("ios: ${UIDevice.currentDevice.systemVersion}")
            appendLine("device: ${UIDevice.currentDevice.model}")
            appendLine()
            append(throwable.stackTraceToString())
        }

    private fun info(key: String): String? =
        (NSBundle.mainBundle.objectForInfoDictionaryKey(key) as? String)?.takeUnless { it.isBlank() }

    private fun nowMillis(): Long = Clock.System.now().toEpochMilliseconds()
}

private object UserDefaultsCrashStore : CrashStore {
    private val defaults get() = NSUserDefaults.standardUserDefaults

    override var startupCrashes: Int
        get() = defaults.integerForKey(KEY_STARTUP_CRASHES).toInt()
        set(value) {
            defaults.setInteger(value.toLong(), KEY_STARTUP_CRASHES)
            // The app is about to go down when this is a crash: write it now, not later.
            defaults.synchronize()
        }

    override fun appendReport(report: String) {
        defaults.setObject((reports() + report).takeLast(MAX_REPORTS), KEY_REPORTS)
        defaults.synchronize()
    }

    override fun reports(): List<String> = defaults.stringArrayForKey(KEY_REPORTS)?.filterIsInstance<String>().orEmpty()

    override fun clearReports() {
        defaults.removeObjectForKey(KEY_REPORTS)
    }

    private const val KEY_STARTUP_CRASHES = "orbin.diagnostics.startupCrashes"
    private const val KEY_REPORTS = "orbin.diagnostics.reports"

    /** The newest few: enough to show a pattern, small enough for user defaults. */
    private const val MAX_REPORTS = 5
}
