package com.orbin.domain.repository

import com.orbin.core.model.MediaLibraryRecord
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/** Metadata only; owned bytes and native sharing belong to the platform file bridge. */
interface MediaLibraryRepository {
    fun observeAll(): Flow<List<MediaLibraryRecord>>

    suspend fun get(id: String): MediaLibraryRecord?

    suspend fun put(record: MediaLibraryRecord)

    suspend fun putAll(records: List<MediaLibraryRecord>) {
        records.forEach { put(it) }
    }

    /** Used by import: an existing library identity must never be overwritten. */
    suspend fun addAllIfAbsent(records: List<MediaLibraryRecord>): List<MediaLibraryRecord> {
        val added = mutableListOf<MediaLibraryRecord>()
        records.forEach { record ->
            if (get(record.id) == null) {
                put(record)
                added += record
            }
        }
        return added
    }

    suspend fun remove(id: String)

    suspend fun clear()
}

class InMemoryMediaLibraryRepository : MediaLibraryRepository {
    private val records = MutableStateFlow<Map<String, MediaLibraryRecord>>(emptyMap())

    override fun observeAll(): Flow<List<MediaLibraryRecord>> =
        records.map {
            it.values.sortedByDescending { r -> r.savedAtMillis }
        }

    override suspend fun get(id: String): MediaLibraryRecord? = records.value[id]

    override suspend fun put(record: MediaLibraryRecord) {
        records.update { it + (record.id to record) }
    }

    override suspend fun putAll(records: List<MediaLibraryRecord>) {
        this.records.update { existing -> existing + records.associateBy { it.id } }
    }

    override suspend fun addAllIfAbsent(records: List<MediaLibraryRecord>): List<MediaLibraryRecord> {
        var added = emptyList<MediaLibraryRecord>()
        this.records.update { existing ->
            added = records.distinctBy { it.id }.filter { it.id !in existing }
            existing + added.associateBy { it.id }
        }
        return added
    }

    override suspend fun remove(id: String) {
        records.update { it - id }
    }

    override suspend fun clear() {
        records.value = emptyMap()
    }
}
