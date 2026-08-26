package com.orbin.data.notification

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.orbin.core.model.ThreadKey
import com.orbin.data.R
import com.orbin.domain.notification.ThreadNotifier
import com.orbin.domain.repository.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import java.time.LocalTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [ThreadNotifier] backed by the system notification tray. Posts one notification per watched
 * thread (deduplicated by a stable id derived from the [ThreadKey]). Requires the
 * POST_NOTIFICATIONS runtime permission on Android 13+; if it is denied the post is a no-op.
 */
@Singleton
class AndroidThreadNotifier
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val settingsRepository: SettingsRepository,
    ) : ThreadNotifier {
        init {
            val channel =
                NotificationChannel(
                    CHANNEL_ID,
                    "Watched threads",
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply { description = "Updates for threads you are watching" }
            NotificationManagerCompat.from(context).createNotificationChannel(channel)
        }

        override suspend fun notifyThreadUpdate(
            key: ThreadKey,
            title: String,
            newReplyCount: Int,
        ) {
            val settings = settingsRepository.settings.first()
            if (!settings.threadWatchNotificationsEnabled) return
            if (isInQuietHours(settings.quietHoursStart, settings.quietHoursEnd)) return

            val manager = NotificationManagerCompat.from(context)
            if (!manager.areNotificationsEnabled()) return

            val hasPostNotificationsPermission =
                Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                    ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.POST_NOTIFICATIONS,
                    ) == PackageManager.PERMISSION_GRANTED
            if (!hasPostNotificationsPermission) return

            val notification =
                NotificationCompat
                    .Builder(context, CHANNEL_ID)
                    .setSmallIcon(android.R.drawable.stat_notify_chat)
                    .setContentTitle(title)
                    .setContentText(
                        context.resources.getQuantityString(
                            R.plurals.notification_thread_new_replies,
                            newReplyCount,
                            newReplyCount,
                        ),
                    ).setContentIntent(launchIntent())
                    .setAutoCancel(true)
                    .build()

            @Suppress("MissingPermission")
            manager.notify(key.notificationId(), notification)
        }

        /**
         * Opens the app when the notification is tapped.
         *
         * Without a content intent a tap only dismissed it, so a notification about a thread could
         * not take a reader to the app at all. Resolved through the package manager rather than
         * naming an activity, because this module cannot see either app's launcher — and the two
         * apps have different ones.
         *
         * FLAG_IMMUTABLE because nothing here wants the receiver filling the intent in; it is also
         * required from API 31, which is this project's minimum.
         */
        private fun launchIntent(): PendingIntent? {
            val launch =
                context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return null
            return PendingIntent.getActivity(
                context,
                0,
                launch,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }

        private fun isInQuietHours(
            start: String,
            end: String,
        ): Boolean {
            if (start.isBlank() || end.isBlank()) return false
            return runCatching {
                val now = LocalTime.now()
                val startTime = LocalTime.parse(start)
                val endTime = LocalTime.parse(end)
                if (startTime.isBefore(endTime)) {
                    now.isAfter(startTime) && now.isBefore(endTime)
                } else {
                    now.isAfter(startTime) || now.isBefore(endTime)
                }
            }.getOrDefault(false)
        }

        private fun ThreadKey.notificationId(): Int = (provider.value + board.value + thread.value).hashCode()

        private companion object {
            const val CHANNEL_ID = "orbin_watched_threads"
        }
    }
