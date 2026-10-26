package com.orbin.ios

import com.orbin.core.model.Thread
import com.orbin.core.model.ThreadKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** A one-shot return position, kept while the thread is off screen behind its media viewer. */
internal class MediaScrollSync(
    private val currentRoute: () -> Route,
    private val currentThread: () -> Thread?,
) {
    private val pending = MutableStateFlow<Pair<ThreadKey, String>?>(null)
    val target = pending.asStateFlow()

    fun select(
        route: Route.Media,
        index: Int,
    ) {
        if (currentRoute() != route) return
        val thread = currentThread()?.takeIf { it.key == route.thread } ?: return
        // Simply opening and closing a file preserves the reader's exact scroll offset.
        if (index == route.index && pending.value == null) return
        val postId = thread.postIdForFileIndex(index) ?: return
        pending.value = thread.key to postId
    }

    fun consume(target: Pair<ThreadKey, String>) {
        pending.compareAndSet(target, null)
    }

    fun clear() {
        pending.value = null
    }
}
