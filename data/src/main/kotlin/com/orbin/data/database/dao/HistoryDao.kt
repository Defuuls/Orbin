package com.orbin.data.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.orbin.data.database.entity.HistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface HistoryDao {
    @Query("SELECT * FROM history ORDER BY lastVisitedMillis DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<HistoryEntity>>

    @Upsert
    suspend fun upsert(entry: HistoryEntity)

    @Query(
        "UPDATE history SET lastReadPostId = :postId " +
            "WHERE provider = :provider AND board = :board AND thread = :thread",
    )
    suspend fun updateLastRead(
        provider: String,
        board: String,
        thread: Long,
        postId: Long,
    )

    @Query("DELETE FROM history")
    suspend fun clear()
}
