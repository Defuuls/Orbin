package com.orbin.domain.notification

import com.orbin.core.model.ThreadKey

/**
 * Posts notifications about watched-thread updates. Abstracting this behind an interface keeps the
 * background worker independent of the delivery channel, so alternative notifiers (system tray,
 * push, in-app) can be swapped in by binding a different implementation.
 */
interface ThreadNotifier {
    /**
     * Notify that [newReplyCount] new replies arrived in the watched thread [key] ([title]).
     *
     * Suspending because deciding whether to post reads the reader's settings. The only caller is
     * a coroutine worker, and the implementation had been bridging that gap with `runBlocking` —
     * blocking a WorkManager thread once per notification, from inside a coroutine.
     */
    suspend fun notifyThreadUpdate(
        key: ThreadKey,
        title: String,
        newReplyCount: Int,
    )
}
