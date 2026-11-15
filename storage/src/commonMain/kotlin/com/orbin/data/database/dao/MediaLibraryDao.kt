package com.orbin.data.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.orbin.data.database.entity.MediaLibraryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MediaLibraryDao {
    @Query("SELECT * FROM media_library ORDER BY savedAtMillis DESC, id ASC")
    fun observeAll(): Flow<List<MediaLibraryEntity>>

    @Query("SELECT * FROM media_library WHERE id = :id")
    suspend fun get(id: String): MediaLibraryEntity?

    @Upsert
    suspend fun put(entity: MediaLibraryEntity)

    @Upsert
    suspend fun upsertAll(entities: List<MediaLibraryEntity>)

    @Transaction
    suspend fun putAll(entities: List<MediaLibraryEntity>) {
        upsertAll(entities)
    }

    @Transaction
    suspend fun addAllIfAbsent(entities: List<MediaLibraryEntity>): List<MediaLibraryEntity> {
        val added = entities.distinctBy { it.id }.filter { get(it.id) == null }
        upsertAll(added)
        return added
    }

    @Query("DELETE FROM media_library WHERE id = :id")
    suspend fun remove(id: String)

    @Query("DELETE FROM media_library")
    suspend fun clear()
}
