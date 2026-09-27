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
                    context.getString(R.string.notification_channel_watched_threads),
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply {
                    description = context.getString(R.string.notification_channel_watched_threads_desc)
                }
            NotificationManagerCompat.from(context).createNotificationChannel(channel)
        }

        override suspend fun notifyThreadUpdate(
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

            val intent = launchIntent(key, title)
            val publicNotification =
                NotificationCompat
                    .Builder(context, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_stat_notify_thread)
                    .setContentTitle(context.getString(R.string.notification_thread_watched_update))
                    .setContentText(
                        context.resources.getQuantityString(
                            R.plurals.notification_thread_new_replies,
                            newReplyCount,
                            newReplyCount,
                        ),
                    ).setContentIntent(intent)
                    .setAutoCancel(true)
                    .build()

            val notification =
                NotificationCompat
                    .Builder(context, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_stat_notify_thread)
                    .setContentTitle(title)
                    .setContentText(
                        context.resources.getQuantityString(
                            R.plurals.notification_thread_new_replies,
                            newReplyCount,
                            newReplyCount,
                        ),
                    ).setContentIntent(intent)
                    .setAutoCancel(true)
                    .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                    .setPublicVersion(publicNotification)
                    .build()

            @Suppress("MissingPermission")
            manager.notify(key.notificationId(), notification)
        }

        /**
         * Opens the app when the notification is tapped, passing thread extras for deep linking.
         *
         * FLAG_IMMUTABLE because nothing here wants the receiver filling the intent in; it is also
         * required from API 31, which is this project's minimum.
         */
        private fun launchIntent(
            key: ThreadKey,
            title: String,
        ): PendingIntent? {
            val launch =
                context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return null
            launch.putExtra(EXTRA_PROVIDER, key.provider.value)
            launch.putExtra(EXTRA_BOARD, key.board.value)
            launch.putExtra(EXTRA_THREAD, key.thread.value)
            launch.putExtra(EXTRA_TITLE, title)
            return PendingIntent.getActivity(
                context,
                key.notificationId(),
                launch,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }

        private fun ThreadKey.notificationId(): Int = (provider.value + board.value + thread.value).hashCode()

        companion object {
            const val CHANNEL_ID = "orbin_watched_threads"
            const val EXTRA_PROVIDER = "com.orbin.extra.PROVIDER"
            const val EXTRA_BOARD = "com.orbin.extra.BOARD"
            const val EXTRA_THREAD = "com.orbin.extra.THREAD"
            const val EXTRA_TITLE = "com.orbin.extra.TITLE"
        }
    }
