package com.orbin.data.diagnostics

import android.content.Context
import android.os.Build
import android.os.SystemClock
import com.orbin.core.common.dispatchers.Dispatcher
import com.orbin.core.common.dispatchers.OrbinDispatcher
import com.orbin.data.crypto.LocalDataCipher
import com.orbin.data.database.OrbinDatabase
import com.orbin.domain.repository.DiagnosticsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Records uncaught exceptions locally and detects a startup crash loop.
 *
 * A crash that happens within [STARTUP_WINDOW_MILLIS] of process start counts as a startup crash;
 * [CRASH_LOOP_THRESHOLD] of those in a row means the app cannot reach a usable state on its own and
 * the UI should offer recovery. The counter is written with `commit()` rather than `apply()`
 * because the process is on its way out and an async write would not survive.
 */
@Singleton
class DiagnosticsRepositoryImpl
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        @Dispatcher(OrbinDispatcher.IO) private val ioDispatcher: CoroutineDispatcher,
    ) : DiagnosticsRepository {
        private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        /**
         * Set when [install] runs, from `Application.onCreate`. `elapsedRealtime` counts from device
         * boot, so "how long has this launch been going" needs a start of its own.
         */
        private var launchStartedAt = SystemClock.elapsedRealtime()

        private val store =
            CrashLogStore(
                directory = File(context.filesDir, DIAGNOSTICS_DIR),
                encrypt = LocalDataCipher::encrypt,
                decrypt = LocalDataCipher::decrypt,
            )

        /** Installs the handler, chaining to whatever was there so the crash still surfaces. */
        fun install() {
            launchStartedAt = SystemClock.elapsedRealtime()
            val previous = Thread.getDefaultUncaughtExceptionHandler()
            Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
                runCatching {
                    if (SystemClock.elapsedRealtime() - launchStartedAt < STARTUP_WINDOW_MILLIS) {
                        val count = prefs.getInt(KEY_STARTUP_CRASHES, 0) + 1
                        prefs.edit().putInt(KEY_STARTUP_CRASHES, count).commit()
                    }
                    store.record(format(thread, throwable))
                }
                previous?.uncaughtException(thread, throwable)
            }
        }

        override fun isCrashLooping(): Boolean = prefs.getInt(KEY_STARTUP_CRASHES, 0) >= CRASH_LOOP_THRESHOLD

        override fun markLaunchSucceeded() {
            if (prefs.getInt(KEY_STARTUP_CRASHES, 0) != 0) {
                prefs.edit().putInt(KEY_STARTUP_CRASHES, 0).apply()
            }
        }

        override suspend fun hasReports(): Boolean = withContext(ioDispatcher) { store.readAll().isNotEmpty() }

        override suspend fun exportReport(): String? =
            withContext(ioDispatcher) {
                val reports = store.readAll()
                if (reports.isEmpty()) null else reports.joinToString("\n\n${"-".repeat(SEPARATOR_WIDTH)}\n\n")
            }

        override suspend fun clearReports() {
            withContext(ioDispatcher) { store.clear() }
        }

        override suspend fun resetLocalData() {
            withContext(ioDispatcher) {
                context.deleteDatabase(OrbinDatabase.NAME)
                store.clear()
                prefs.edit().putInt(KEY_STARTUP_CRASHES, 0).commit()
            }
        }

        /**
         * Device and build context plus the stack trace. Deliberately nothing about what the user
         * was browsing — a report should be safe to paste into a public issue without thinking.
         */
        private fun format(
            thread: Thread,
            throwable: Throwable,
        ): String {
            val stackTrace =
                StringWriter().also { writer ->
                    PrintWriter(writer).use(throwable::printStackTrace)
                }

            return buildString {
                appendLine("time: ${SimpleDateFormat(TIME_FORMAT, Locale.US).format(Date())}")
                appendLine("app: ${appVersion()}")
                appendLine("android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
                appendLine("device: ${Build.MANUFACTURER} ${Build.MODEL}")
                appendLine("thread: ${thread.name}")
                appendLine()
                append(stackTrace)
            }
        }

        private fun appVersion(): String =
            runCatching {
                val info = context.packageManager.getPackageInfo(context.packageName, 0)
                "${info.versionName} (${info.longVersionCode})"
            }.getOrDefault("unknown")

        private companion object {
            const val PREFS_NAME = "orbin_diagnostics"
            const val KEY_STARTUP_CRASHES = "startup_crashes"
            const val DIAGNOSTICS_DIR = "diagnostics"
            const val TIME_FORMAT = "yyyy-MM-dd HH:mm:ss z"
            const val SEPARATOR_WIDTH = 40

            /** A crash this soon after process start is a startup crash, not a usage crash. */
            const val STARTUP_WINDOW_MILLIS = 10_000L

            /** One bad launch can be a fluke; two in a row is a loop the user cannot escape. */
            const val CRASH_LOOP_THRESHOLD = 2
        }
    }
