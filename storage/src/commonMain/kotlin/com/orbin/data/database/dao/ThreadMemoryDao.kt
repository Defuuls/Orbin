package com.orbin.data.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.orbin.data.database.entity.ThreadMemoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ThreadMemoryDao {
    @Query("SELECT * FROM thread_memory WHERE provider = :provider AND board = :board AND thread = :thread")
    fun observe(
        provider: String,
        board: String,
        thread: Long,
    ): Flow<ThreadMemoryEntity?>

    @Query("SELECT * FROM thread_memory WHERE provider = :provider AND board = :board AND thread = :thread")
    suspend fun get(
        provider: String,
        board: String,
        thread: Long,
    ): ThreadMemoryEntity?

    @Query("SELECT * FROM thread_memory ORDER BY updatedAtMillis DESC")
    fun observeAll(): Flow<List<ThreadMemoryEntity>>

    @Upsert
    suspend fun put(entity: ThreadMemoryEntity)

    @Query("DELETE FROM thread_memory WHERE provider = :provider AND board = :board AND thread = :thread")
    suspend fun forget(
        provider: String,
        board: String,
        thread: Long,
    )

    @Query("DELETE FROM thread_memory")
    suspend fun clear()
}
