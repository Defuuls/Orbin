package com.orbin.ios

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.launch
import platform.BackgroundTasks.BGAppRefreshTask
import platform.BackgroundTasks.BGAppRefreshTaskRequest
import platform.BackgroundTasks.BGTaskScheduler
import platform.Foundation.NSDate
import platform.Foundation.dateWithTimeIntervalSinceNow
import kotlin.time.Clock

/** The background task's name; `Info.plist` lists it under BGTaskSchedulerPermittedIdentifiers. */
private const val REFRESH_TASK = "io.github.defuuls.orbin.refresh"

/** Asked for no sooner than this; iOS decides when, from how the reader uses the app. */
private const val REFRESH_INTERVAL_SECONDS = 30.0 * 60

/**
 * Registers the watched-thread refresh with iOS. It has to happen before the app finishes
 * launching, so the Swift side calls it from the app's initializer.
 */
@Suppress("unused") // Called from Swift as BackgroundRefreshKt.registerBackgroundRefresh().
fun registerBackgroundRefresh() {
    BGTaskScheduler.sharedScheduler.registerForTaskWithIdentifier(REFRESH_TASK, usingQueue = null) { task ->
        val refresh = task as? BGAppRefreshTask ?: return@registerForTaskWithIdentifier
        // The next one is asked for first, so a refresh the system cuts short still leaves one due.
        scheduleBackgroundRefresh()
        val job = AppGraph.scope.launch { backgroundWatch().refreshNow() }
        refresh.expirationHandler = { job.cancel() }
        job.invokeOnCompletion { cause -> refresh.setTaskCompletedWithSuccess(cause == null) }
    }
}

/** The watched threads as the background task sees them: no screens, just the shared stores. */
private fun backgroundWatch(): WatchedThreads =
    WatchedThreads(
        bookmarks = AppGraph.bookmarks,
        scope = AppGraph.scope,
        now = { Clock.System.now().toEpochMilliseconds() },
        notifier = AppGraph.notifier,
    ) { key -> AppGraph.providers.first { it.metadata.id == key.provider }.getThread(key.board, key.thread) }

/** Asks iOS to wake the app for a refresh; called whenever the app goes to the background. */
@OptIn(ExperimentalForeignApi::class) // The NSError out-parameter, left null.
fun scheduleBackgroundRefresh() {
    val request =
        BGAppRefreshTaskRequest(identifier = REFRESH_TASK).apply {
            earliestBeginDate = NSDate.dateWithTimeIntervalSinceNow(REFRESH_INTERVAL_SECONDS)
        }
    // Refused in the simulator and when background refresh is off; the in-app refresh still runs.
    BGTaskScheduler.sharedScheduler.submitTaskRequest(request, error = null)
}
