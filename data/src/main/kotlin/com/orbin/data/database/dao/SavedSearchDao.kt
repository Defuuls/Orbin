package com.orbin.data.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import com.orbin.data.database.entity.SavedSearchEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SavedSearchDao {
    @Query("SELECT * FROM saved_searches ORDER BY createdAtMillis DESC")
    fun observeAll(): Flow<List<SavedSearchEntity>>

    @Query("SELECT * FROM saved_searches WHERE id = :id")
    suspend fun getById(id: Long): SavedSearchEntity?

    @Upsert
    suspend fun save(entity: SavedSearchEntity): Long

    @Delete
    suspend fun delete(entity: SavedSearchEntity)

    @Query("DELETE FROM saved_searches WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM saved_searches")
    suspend fun clear()
}
