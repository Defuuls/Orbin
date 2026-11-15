package com.orbin.domain.repository

import com.orbin.core.model.AppSettings
import com.orbin.core.model.ThreadKey
import com.orbin.core.model.ThreadMemory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** The lifecycle hook also clears this map when private history ends. */
object ThreadMemorySession {
    internal val state = MutableStateFlow(SessionMemoryState())

    fun forgetSession() {
        state.update { SessionMemoryState(generation = it.generation + 1) }
    }
}

internal data class SessionMemoryState(
    val generation: Long = 0,
    val records: Map<ThreadKey, ThreadMemory> = emptyMap(),
)

/** Private reading cannot leave an indirect visit trail through anchors, folds or seen post IDs. */
class SessionThreadMemoryRepository(
    private val persistent: ThreadMemoryRepository,
    private val settings: Flow<AppSettings>,
) : ThreadMemoryRepository {
    private val mutex = Mutex()

    override fun observe(key: ThreadKey): Flow<ThreadMemory> =
        combine(settings, persistent.observe(key), ThreadMemorySession.state) { prefs, saved, session ->
            if (prefs.historySessionOnly) session.records[key] ?: saved else saved
        }

    override fun observeAll(): Flow<List<ThreadMemory>> =
        combine(settings, persistent.observeAll()) { prefs, saved ->
            if (prefs.historySessionOnly) emptyList() else saved
        }

    override fun observeCurrent(): Flow<List<ThreadMemory>> =
        combine(settings, persistent.observeAll(), ThreadMemorySession.state) { prefs, saved, session ->
            if (prefs.historySessionOnly) (saved.associateBy { it.key } + session.records).values.toList() else saved
        }

    override suspend fun get(key: ThreadKey): ThreadMemory =
        if (settings.first().historySessionOnly) {
            ThreadMemorySession.state.value.records[key] ?: persistent.get(key)
        } else {
            persistent.get(key)
        }

    override suspend fun update(
        key: ThreadKey,
        transform: (ThreadMemory) -> ThreadMemory,
    ) {
        mutex.withLock {
            if (settings.first().historySessionOnly) {
                val session = ThreadMemorySession.state.value
                val changed = transform(session.records[key] ?: persistent.get(key))
                require(changed.key == key) { "A reading-memory update must preserve its thread identity" }
                ThreadMemorySession.state.update {
                    if (it.generation == session.generation) it.copy(records = it.records + (key to changed)) else it
                }
            } else {
                ThreadMemorySession.forgetSession()
                persistent.update(key, transform)
            }
        }
    }

    override suspend fun forget(key: ThreadKey) {
        mutex.withLock {
            ThreadMemorySession.state.update { it.copy(records = it.records - key) }
            if (!settings.first().historySessionOnly) persistent.forget(key)
        }
    }

    override suspend fun clear() {
        mutex.withLock {
            ThreadMemorySession.forgetSession()
            persistent.clear()
        }
    }
}
