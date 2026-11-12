package com.orbin.ios

import com.orbin.data.database.OrbinDatabase
import kotlinx.cinterop.ExperimentalForeignApi
import okio.Path.Companion.toPath
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSBundle
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileProtectionComplete
import platform.Foundation.NSFileProtectionKey
import platform.Foundation.NSURL
import platform.Foundation.NSURLIsExcludedFromBackupKey
import platform.Foundation.NSUserDefaults
import platform.Foundation.NSUserDomainMask
import platform.UIKit.UIDevice
import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.ReportUnhandledExceptionHook
import kotlin.native.setUnhandledExceptionHook
import kotlin.native.terminateWithUnhandledException
import kotlin.time.Clock

/**
 * The app's [StartupCrashes], kept as protected files in Application Support rather than
 * UserDefaults or the database: safe mode has to work when the database is what keeps the app
 * from starting, and the files use complete Data Protection so they are unreadable while the
 * device is locked.
 */
internal object IosStartupCrashes {
    val crashes = StartupCrashes(createStore()) { nowMillis() }

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
            appendLine()
            append(throwable.stackTraceToString().replace(URL_REGEX, "[url]"))
        }

    private fun info(key: String): String? =
        (NSBundle.mainBundle.objectForInfoDictionaryKey(key) as? String)?.takeUnless { it.isBlank() }

    private fun nowMillis(): Long = Clock.System.now().toEpochMilliseconds()

    private val URL_REGEX = Regex("""https?://\S+""", RegexOption.IGNORE_CASE)
}

@OptIn(ExperimentalForeignApi::class)
private fun createStore(): CrashStore {
    val store =
        FileCrashStore(
            directory = diagnosticsDirectory().toPath(),
            protect = { path -> protectComplete(path.toString()) },
        )
    adoptUserDefaults(store)
    return store
}

@OptIn(ExperimentalForeignApi::class)
private fun adoptUserDefaults(store: CrashStore) {
    val defaults = NSUserDefaults.standardUserDefaults
    val reports = defaults.stringArrayForKey(LEGACY_KEY_REPORTS)?.filterIsInstance<String>().orEmpty()
    val count = defaults.integerForKey(LEGACY_KEY_STARTUP_CRASHES).toInt()
    adoptLegacyCrashRecords(store, reports, count) {
        defaults.removeObjectForKey(LEGACY_KEY_REPORTS)
        defaults.removeObjectForKey(LEGACY_KEY_STARTUP_CRASHES)
        defaults.synchronize()
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun diagnosticsDirectory(): String {
    val parent =
        NSFileManager.defaultManager.URLForDirectory(
            directory = NSApplicationSupportDirectory,
            inDomain = NSUserDomainMask,
            appropriateForURL = null,
            create = true,
            error = null,
        )
    parent?.setResourceValue(value = true, forKey = NSURLIsExcludedFromBackupKey, error = null)
    val dirPath = requireNotNull(parent?.path) { "No Application Support directory" } + "/diagnostics"
    val manager = NSFileManager.defaultManager
    if (!manager.fileExistsAtPath(dirPath)) {
        manager.createDirectoryAtPath(
            path = dirPath,
            withIntermediateDirectories = true,
            attributes = mapOf(NSFileProtectionKey to NSFileProtectionComplete),
            error = null,
        )
    }
    protectComplete(dirPath)
    return dirPath
}

@OptIn(ExperimentalForeignApi::class)
private fun protectComplete(path: String) {
    NSFileManager.defaultManager.setAttributes(
        attributes = mapOf(NSFileProtectionKey to NSFileProtectionComplete),
        ofItemAtPath = path,
        error = null,
    )
    NSURL.fileURLWithPath(path).setResourceValue(
        value = true,
        forKey = NSURLIsExcludedFromBackupKey,
        error = null,
    )
}

private const val LEGACY_KEY_STARTUP_CRASHES = "orbin.diagnostics.startupCrashes"
private const val LEGACY_KEY_REPORTS = "orbin.diagnostics.reports"
