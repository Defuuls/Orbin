package com.orbin.data.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.orbin.data.database.entity.DownloadEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadDao {
    @Query("SELECT * FROM downloads ORDER BY createdAtMillis DESC")
    fun observeAll(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads")
    suspend fun all(): List<DownloadEntity>

    @Upsert
    suspend fun upsert(entry: DownloadEntity)

    @Query("UPDATE downloads SET status = :status WHERE id = :id")
    suspend fun updateStatus(
        id: Long,
        status: String,
    )

    @Query("DELETE FROM downloads")
    suspend fun clear()

    @Query("SELECT * FROM downloads WHERE id = :id")
    suspend fun getById(id: Long): DownloadEntity?

    @Query("DELETE FROM downloads WHERE id = :id")
    suspend fun delete(id: Long)
}
