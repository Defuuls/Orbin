package com.orbin.data.notification

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.orbin.core.model.ThreadKey
import com.orbin.domain.notification.ThreadNotifier
import dagger.hilt.android.qualifiers.ApplicationContext
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

        override fun notifyThreadUpdate(
            key: ThreadKey,
            title: String,
            newReplyCount: Int,
        ) {
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
                    .setContentText("$newReplyCount new repl${if (newReplyCount == 1) "y" else "ies"}")
                    .setAutoCancel(true)
                    .build()

            @Suppress("MissingPermission")
            manager.notify(key.notificationId(), notification)
        }

        private fun ThreadKey.notificationId(): Int = (provider.value + board.value + thread.value).hashCode()

        private companion object {
            const val CHANNEL_ID = "orbin_watched_threads"
        }
    }
