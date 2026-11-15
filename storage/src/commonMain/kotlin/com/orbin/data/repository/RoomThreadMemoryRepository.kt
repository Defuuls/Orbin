package com.orbin.data.repository

import com.orbin.core.model.ThreadKey
import com.orbin.core.model.ThreadMemory
import com.orbin.data.database.dao.ThreadMemoryDao
import com.orbin.data.database.entity.ThreadMemoryEntity
import com.orbin.domain.repository.ThreadMemoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class RoomThreadMemoryRepository(
    private val dao: ThreadMemoryDao,
) : ThreadMemoryRepository {
    private val json = Json { ignoreUnknownKeys = true }
    private val mutex = Mutex()

    override fun observe(key: ThreadKey): Flow<ThreadMemory> =
        dao.observe(key.provider.value, key.board.value, key.thread.value).map { it.decode(key) }

    override fun observeAll(): Flow<List<ThreadMemory>> =
        dao.observeAll().map { records ->
            records.mapNotNull { record ->
                runCatching { json.decodeFromString<ThreadMemory>(record.payload) }.getOrNull()?.takeIf {
                    it.key.provider.value == record.provider &&
                        it.key.board.value == record.board &&
                        it.key.thread.value == record.thread
                }
            }
        }

    override suspend fun get(key: ThreadKey): ThreadMemory =
        dao.get(key.provider.value, key.board.value, key.thread.value).decode(key)

    override suspend fun update(
        key: ThreadKey,
        transform: (ThreadMemory) -> ThreadMemory,
    ) {
        mutex.withLock {
            val changed = transform(get(key))
            require(changed.key == key) { "A reading-memory update must preserve its thread identity" }
            dao.put(
                ThreadMemoryEntity(
                    key.provider.value,
                    key.board.value,
                    key.thread.value,
                    changed.updatedAtMillis,
                    json.encodeToString(changed),
                ),
            )
        }
    }

    override suspend fun forget(key: ThreadKey) {
        mutex.withLock { dao.forget(key.provider.value, key.board.value, key.thread.value) }
    }

    override suspend fun clear() {
        mutex.withLock { dao.clear() }
    }

    private fun ThreadMemoryEntity?.decode(key: ThreadKey): ThreadMemory =
        this?.let {
            runCatching {
                json.decodeFromString<ThreadMemory>(
                    it.payload,
                )
            }.getOrNull()?.takeIf { it.key == key }
        }
            ?: ThreadMemory(key)
}
