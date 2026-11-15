package com.orbin.feature.thread

import com.orbin.core.model.ThreadKey
import com.orbin.core.model.ThreadMemory
import com.orbin.domain.repository.HistoryRepository
import com.orbin.domain.repository.ThreadMemoryRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Persists reading actions through the session-aware repositories and avoids duplicate anchors. */
internal class ThreadReadingActions(
    private val key: ThreadKey,
    private val scope: CoroutineScope,
    private val memory: ThreadMemoryRepository,
    private val history: HistoryRepository,
) {
    var lastPersistedPosition: ThreadScrollPosition? = null

    fun updateMemory(transform: (ThreadMemory) -> ThreadMemory) {
        scope.launch { memory.update(key, transform) }
    }

    suspend fun persistScrollPosition(position: ThreadScrollPosition) {
        if (position == lastPersistedPosition) return
        history.updateScrollPosition(key, position.postId, position.offsetPx)
        lastPersistedPosition = position
    }
}
