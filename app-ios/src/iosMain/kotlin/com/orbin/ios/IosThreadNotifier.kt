package com.orbin.ios

import com.orbin.core.model.ThreadKey
import com.orbin.domain.notification.ThreadNotifier
import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionBadge
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNUserNotificationCenter

/**
 * New replies on a watched thread, posted as a local notification, as Android's notifier posts
 * them: the thread's title, and how many replies are new. One notification per thread, replaced
 * as more arrive rather than piling up. Nothing is posted until the reader has allowed it.
 */
class IosThreadNotifier : ThreadNotifier {
    private val center = UNUserNotificationCenter.currentNotificationCenter()

    /** Asks, the first time only, to post notifications; iOS remembers the answer after that. */
    fun requestPermission() {
        center.requestAuthorizationWithOptions(
            UNAuthorizationOptionAlert or UNAuthorizationOptionSound or UNAuthorizationOptionBadge,
        ) { _, _ -> }
    }

    override suspend fun notifyThreadUpdate(
        key: ThreadKey,
        title: String,
        newReplyCount: Int,
    ) {
        val heading = title.ifBlank { "/${key.board.value}/" }
        val content = UNMutableNotificationContent()
        content.setTitle(heading)
        content.setBody(if (newReplyCount == 1) "1 new reply" else "$newReplyCount new replies")
        val request =
            UNNotificationRequest.requestWithIdentifier(
                identifier = "${key.provider.value}/${key.board.value}/${key.thread.value}",
                content = content,
                trigger = null,
            )
        center.addNotificationRequest(request, withCompletionHandler = null)
    }
}
