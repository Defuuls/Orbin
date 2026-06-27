package com.orbin.data.worker

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Schedules the periodic [ThreadUpdateWorker]. Called once on app start; uses a unique periodic
 * work so re-scheduling is idempotent. 15 minutes is WorkManager's minimum periodic interval.
 */
@Singleton
class WatchScheduler
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        fun ensureScheduled() {
            val request =
                PeriodicWorkRequestBuilder<ThreadUpdateWorker>(REFRESH_INTERVAL_MIN, TimeUnit.MINUTES)
                    .setConstraints(
                        Constraints
                            .Builder()
                            .setRequiredNetworkType(NetworkType.CONNECTED)
                            .build(),
                    ).build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }

        private companion object {
            const val WORK_NAME = "orbin-thread-watch"
            const val REFRESH_INTERVAL_MIN = 15L
        }
    }
