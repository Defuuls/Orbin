package com.orbin.domain.repository

import com.orbin.core.model.ThreadKey
import com.orbin.core.model.ThreadMemory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

interface ThreadMemoryRepository {
    fun observe(key: ThreadKey): Flow<ThreadMemory>

    fun observeAll(): Flow<List<ThreadMemory>>

    /** UI view, including ephemeral private-session updates; never export this flow. */
    fun observeCurrent(): Flow<List<ThreadMemory>> = observeAll()

    suspend fun get(key: ThreadKey): ThreadMemory

    /** Reads and changes one record atomically, so independent visibility and scroll updates survive. */
    suspend fun update(
        key: ThreadKey,
        transform: (ThreadMemory) -> ThreadMemory,
    )

    suspend fun forget(key: ThreadKey)

    suspend fun clear()
}

/** Also useful for previews and hosts without a database; production graphs install Room storage. */
class InMemoryThreadMemoryRepository : ThreadMemoryRepository {
    private val records = MutableStateFlow<Map<ThreadKey, ThreadMemory>>(emptyMap())
    private val mutex = Mutex()

    override fun observe(key: ThreadKey): Flow<ThreadMemory> = records.map { it[key] ?: ThreadMemory(key) }

    override fun observeAll(): Flow<List<ThreadMemory>> = records.map { it.values.toList() }

    override suspend fun get(key: ThreadKey): ThreadMemory = records.value[key] ?: ThreadMemory(key)

    override suspend fun update(
        key: ThreadKey,
        transform: (ThreadMemory) -> ThreadMemory,
    ) {
        mutex.withLock {
            val changed = transform(get(key))
            require(changed.key == key) { "A reading-memory update must preserve its thread identity" }
            records.update { it + (key to changed) }
        }
    }

    override suspend fun forget(key: ThreadKey) {
        mutex.withLock { records.update { it - key } }
    }

    override suspend fun clear() {
        mutex.withLock { records.value = emptyMap() }
    }
}
