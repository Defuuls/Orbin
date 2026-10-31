package com.orbin.app.routing

import android.content.Intent
import com.orbin.data.notification.AndroidThreadNotifier
import com.orbin.data.notification.NotificationLaunchToken

sealed interface IntentRoute {
    data class Thread(
        val provider: String,
        val board: String,
        val thread: Long,
        val title: String,
    ) : IntentRoute

    data object FeedShortcut : IntentRoute

    data object DownloadsShortcut : IntentRoute

    data object BoardsShortcut : IntentRoute

    data object Unknown : IntentRoute
}

private const val MAX_PROVIDER_LENGTH = 64
private const val MAX_BOARD_LENGTH = 64
private const val MAX_TITLE_LENGTH = 256

/** Intent extra used when launching a deep shortcut destination. */
const val EXTRA_SHORTCUT_DESTINATION = "com.orbin.extra.SHORTCUT_DESTINATION"

/**
 * The route this launch asks for. Thread extras are honoured only when [isNotificationToken]
 * accepts the intent's [NotificationLaunchToken.EXTRA_TOKEN]: MainActivity is exported, so without
 * it any app could open an arbitrary thread, switch the active provider and write history.
 */
fun Intent.parseRoute(isNotificationToken: (String?) -> Boolean): IntentRoute {
    if (hasExtra(AndroidThreadNotifier.EXTRA_THREAD) &&
        isNotificationToken(getStringExtra(NotificationLaunchToken.EXTRA_TOKEN))
    ) {
        val provider = (getStringExtra(AndroidThreadNotifier.EXTRA_PROVIDER) ?: "").take(MAX_PROVIDER_LENGTH)
        val board = (getStringExtra(AndroidThreadNotifier.EXTRA_BOARD) ?: "").take(MAX_BOARD_LENGTH)
        val thread = getLongExtra(AndroidThreadNotifier.EXTRA_THREAD, 0L)
        val title = (getStringExtra(AndroidThreadNotifier.EXTRA_TITLE) ?: "").take(MAX_TITLE_LENGTH)
        if (thread != 0L && board.isNotEmpty()) {
            return IntentRoute.Thread(
                provider = provider,
                board = board,
                thread = thread,
                title = title,
            )
        }
    }

    val shortcut = getStringExtra(EXTRA_SHORTCUT_DESTINATION) ?: action
    return when (shortcut) {
        "feed", "com.orbin.shortcut.FEED" -> IntentRoute.FeedShortcut
        "downloads", "com.orbin.shortcut.DOWNLOADS" -> IntentRoute.DownloadsShortcut
        "boards", "com.orbin.shortcut.BOARDS" -> IntentRoute.BoardsShortcut
        else -> IntentRoute.Unknown
    }
}
