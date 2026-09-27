package com.orbin.ios

import com.orbin.core.model.BoardId
import com.orbin.core.model.ProviderId
import com.orbin.core.model.ThreadId
import com.orbin.core.model.ThreadKey
import com.orbin.domain.notification.ThreadNotifier
import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionBadge
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotification
import platform.UserNotifications.UNNotificationPresentationOptionBanner
import platform.UserNotifications.UNNotificationPresentationOptionSound
import platform.UserNotifications.UNNotificationPresentationOptions
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNNotificationResponse
import platform.UserNotifications.UNUserNotificationCenter
import platform.UserNotifications.UNUserNotificationCenterDelegateProtocol
import platform.darwin.NSObject

/**
 * New replies on a watched thread, posted as a local notification, as Android's notifier posts
 * them: the thread's title, and how many replies are new. One notification per thread, replaced
 * as more arrive rather than piling up. Nothing is posted until the reader has allowed it.
 */
class IosThreadNotifier : ThreadNotifier {
    private val center = UNUserNotificationCenter.currentNotificationCenter()
    private var onOpenThread: ((ThreadKey) -> Unit)? = null

    private val delegate =
        object : NSObject(), UNUserNotificationCenterDelegateProtocol {
            override fun userNotificationCenter(
                center: UNUserNotificationCenter,
                willPresentNotification: UNNotification,
                withCompletionHandler: (UNNotificationPresentationOptions) -> Unit,
            ) {
                withCompletionHandler(
                    UNNotificationPresentationOptionBanner or UNNotificationPresentationOptionSound,
                )
            }

            override fun userNotificationCenter(
                center: UNUserNotificationCenter,
                didReceiveNotificationResponse: UNNotificationResponse,
                withCompletionHandler: () -> Unit,
            ) {
                val identifier = didReceiveNotificationResponse.notification.request.identifier
                parseNotificationKey(identifier)?.let { onOpenThread?.invoke(it) }
                withCompletionHandler()
            }
        }

    init {
        center.delegate = delegate
    }

    /** Wires thread navigation when the reader taps a notification. */
    fun attachNavigation(onOpen: (ThreadKey) -> Unit) {
        onOpenThread = onOpen
    }

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

internal fun parseNotificationKey(identifier: String): ThreadKey? {
    val parts = identifier.split('/')
    if (parts.size != NOTIFICATION_KEY_PARTS) return null
    val threadNumber = parts[2].toLongOrNull() ?: return null
    return ThreadKey(ProviderId(parts[0]), BoardId(parts[1]), ThreadId(threadNumber))
}

private const val NOTIFICATION_KEY_PARTS = 3
