package com.orbin.data.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.orbin.data.database.entity.HistoryEntity
import com.orbin.data.database.entity.HistoryKeyRow
import kotlinx.coroutines.flow.Flow

@Dao
interface HistoryDao {
    @Query("SELECT * FROM history ORDER BY lastVisitedMillis DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<HistoryEntity>>

    @Query(
        "SELECT * FROM history WHERE provider = :provider AND board = :board AND thread = :thread",
    )
    suspend fun getEntry(
        provider: String,
        board: String,
        thread: Long,
    ): HistoryEntity?

    /** Every visited thread key, for "already read" styling in catalogs and feeds. */
    @Query("SELECT provider, board, thread FROM history")
    fun observeKeys(): Flow<List<HistoryKeyRow>>

    @Upsert
    suspend fun upsert(entry: HistoryEntity)

    @Transaction
    suspend fun recordEntry(entry: HistoryEntity) {
        val existing = getEntry(entry.provider, entry.board, entry.thread)
        val merged =
            if (existing != null) {
                entry.copy(
                    lastReadPostId = existing.lastReadPostId,
                    lastReadOffsetPx = existing.lastReadOffsetPx,
                )
            } else {
                entry
            }
        upsert(merged)
    }

    @Query(
        "UPDATE history SET lastReadPostId = :postId, lastReadOffsetPx = :offsetPx " +
            "WHERE provider = :provider AND board = :board AND thread = :thread",
    )
    suspend fun updateScrollPosition(
        provider: String,
        board: String,
        thread: Long,
        postId: Long,
        offsetPx: Int,
    )

    @Query("DELETE FROM history")
    suspend fun clear()
}
