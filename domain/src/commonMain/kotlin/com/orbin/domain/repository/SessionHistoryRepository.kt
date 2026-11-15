package com.orbin.domain.repository

import com.orbin.core.model.AppSettings
import com.orbin.core.model.HistoryEntry
import com.orbin.core.model.PostId
import com.orbin.core.model.ThreadKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Session history never enters persistent storage, exports or synchronization. */
object HistorySession {
    internal val state = MutableStateFlow(SessionHistoryState())

    fun forgetSession() {
        state.update { SessionHistoryState(generation = it.generation + 1) }
        ThreadMemorySession.forgetSession()
    }
}

internal data class SessionHistoryState(
    val generation: Long = 0,
    val entries: List<HistoryEntry> = emptyList(),
)

/** Apply the same history policy in Android and iOS, including scroll writes after tracking is off. */
class SessionHistoryRepository(
    private val persistent: HistoryRepository,
    private val settings: Flow<AppSettings>,
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) : HistoryRepository {
    private val mutex = Mutex()

    init {
        scope.launch {
            settings.map { it.saveBrowsingHistory to it.historySessionOnly }.distinctUntilChanged().collect { policy ->
                mutex.withLock {
                    if (!policy.first || policy.second) persistent.clear()
                    if (!policy.first || !policy.second) HistorySession.forgetSession()
                }
            }
        }
    }

    override fun observeHistory(): Flow<List<HistoryEntry>> =
        combine(settings, persistent.observeHistory(), HistorySession.state) { prefs, saved, session ->
            when {
                !prefs.saveBrowsingHistory -> emptyList()
                prefs.historySessionOnly -> session.entries
                else -> saved
            }
        }

    override fun observeAllEntries(): Flow<List<HistoryEntry>> =
        combine(settings, persistent.observeAllEntries()) { prefs, saved ->
            if (prefs.saveBrowsingHistory && !prefs.historySessionOnly) saved else emptyList()
        }

    override fun observeVisitedKeys(): Flow<Set<ThreadKey>> =
        observeHistory().map { rows ->
            rows.mapTo(mutableSetOf()) {
                it.key
            }
        }

    override suspend fun getEntry(key: ThreadKey): HistoryEntry? {
        val prefs = settings.first()
        return when {
            !prefs.saveBrowsingHistory -> null
            prefs.historySessionOnly ->
                HistorySession.state.value.entries
                    .firstOrNull { it.key == key }
            else -> persistent.getEntry(key)
        }
    }

    override suspend fun record(entry: HistoryEntry) {
        mutex.withLock {
            val generation = HistorySession.state.value.generation
            val prefs = settings.first()
            if (!prefs.saveBrowsingHistory) return
            if (prefs.historySessionOnly) {
                val previous =
                    HistorySession.state.value.entries
                        .firstOrNull { it.key == entry.key }
                val updated =
                    if (previous == null) {
                        entry
                    } else {
                        entry.copy(
                            lastReadPostId = previous.lastReadPostId,
                            lastReadOffsetPx = previous.lastReadOffsetPx,
                        )
                    }
                HistorySession.state.update {
                    if (it.generation == generation) {
                        it.copy(
                            entries =
                                (listOf(updated) + it.entries.filterNot { row -> row.key == entry.key })
                                    .sortedByDescending { row -> row.lastVisitedMillis }
                                    .take(MAX_SESSION_HISTORY),
                        )
                    } else {
                        it
                    }
                }
            } else {
                persistent.record(entry)
            }
        }
    }

    override suspend fun updateScrollPosition(
        key: ThreadKey,
        postId: PostId,
        offsetPx: Int,
    ) {
        mutex.withLock {
            val generation = HistorySession.state.value.generation
            val prefs = settings.first()
            if (!prefs.saveBrowsingHistory) return
            if (prefs.historySessionOnly) {
                HistorySession.state.update { session ->
                    if (session.generation == generation) {
                        session.copy(
                            entries =
                                session.entries.map {
                                    if (it.key ==
                                        key
                                    ) {
                                        it.copy(lastReadPostId = postId, lastReadOffsetPx = offsetPx)
                                    } else {
                                        it
                                    }
                                },
                        )
                    } else {
                        session
                    }
                }
            } else {
                persistent.updateScrollPosition(key, postId, offsetPx)
            }
        }
    }

    override suspend fun remove(key: ThreadKey) {
        mutex.withLock {
            HistorySession.state.update { it.copy(entries = it.entries.filterNot { row -> row.key == key }) }
            persistent.remove(key)
        }
    }

    override suspend fun clear() {
        mutex.withLock {
            HistorySession.forgetSession()
            persistent.clear()
        }
    }

    override suspend fun forgetSession() {
        mutex.withLock { HistorySession.forgetSession() }
    }

    private companion object {
        const val MAX_SESSION_HISTORY = 200
    }
}
