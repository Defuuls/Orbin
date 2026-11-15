package com.orbin.data.repository

import com.orbin.core.model.MediaLibraryRecord
import com.orbin.core.model.PermanentContentFilter
import com.orbin.data.database.dao.MediaLibraryDao
import com.orbin.data.database.entity.MediaLibraryEntity
import com.orbin.domain.repository.MediaLibraryRepository
import com.orbin.domain.repository.validManagedMediaKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class RoomMediaLibraryRepository(
    private val dao: MediaLibraryDao,
) : MediaLibraryRepository {
    private val json = Json { ignoreUnknownKeys = true }

    override fun observeAll(): Flow<List<MediaLibraryRecord>> =
        dao.observeAll().map { records -> records.mapNotNull { it.decode() } }

    override suspend fun get(id: String): MediaLibraryRecord? = dao.get(id)?.decode()

    override suspend fun put(record: MediaLibraryRecord) {
        dao.put(record.entity())
    }

    override suspend fun putAll(records: List<MediaLibraryRecord>) {
        dao.putAll(records.map { it.entity() })
    }

    override suspend fun addAllIfAbsent(records: List<MediaLibraryRecord>): List<MediaLibraryRecord> =
        dao.addAllIfAbsent(records.map { it.entity() }).mapNotNull { it.decode() }

    private fun MediaLibraryRecord.entity(): MediaLibraryEntity {
        val record = this
        require(record.id.isNotBlank() && record.providerId.isNotBlank()) { "Saved media needs its source identity" }
        require(record.byteCount >= 0) { "Saved media size must not be negative" }
        require(
            validManagedMediaKey(record.storageKey) &&
                (record.thumbnailStorageKey?.let(::validManagedMediaKey) != false),
        ) {
            "Saved media files must use relative owned-file keys"
        }
        require(!PermanentContentFilter.matches(record.fileName)) { "This media is unavailable" }
        return MediaLibraryEntity(
            record.id,
            record.providerId,
            record.storageKey,
            record.fileName,
            record.savedAtMillis,
            json.encodeToString(record),
        )
    }

    override suspend fun remove(id: String) {
        dao.remove(id)
    }

    override suspend fun clear() {
        dao.clear()
    }

    private fun MediaLibraryEntity.decode(): MediaLibraryRecord? =
        runCatching { json.decodeFromString<MediaLibraryRecord>(payload) }
            .getOrNull()
            ?.takeIf {
                it.id == id &&
                    it.providerId == provider &&
                    validManagedMediaKey(it.storageKey) &&
                    (it.thumbnailStorageKey?.let(::validManagedMediaKey) != false) &&
                    !PermanentContentFilter.matches(it.fileName)
            }
}
