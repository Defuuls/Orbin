package com.orbin.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.orbin.data.database.entity.BoardEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BoardDao {
    @Query("SELECT * FROM boards WHERE provider = :provider ORDER BY sortIndex ASC")
    fun observeBoards(provider: String): Flow<List<BoardEntity>>

    /** When the provider's boards were last written, or null when nothing is cached for it. */
    @Query("SELECT MAX(cachedAtMillis) FROM boards WHERE provider = :provider")
    suspend fun cachedAtMillis(provider: String): Long?

    /**
     * Swaps in a fresh board list for one provider. A board removed upstream has to disappear
     * locally too, so this replaces the provider's rows rather than upserting over them — in one
     * transaction, so an observer never sees the empty window between delete and insert.
     */
    @Transaction
    suspend fun replaceBoards(
        provider: String,
        boards: List<BoardEntity>,
    ) {
        deleteBoards(provider)
        insertBoards(boards)
    }

    @Query("DELETE FROM boards WHERE provider = :provider")
    suspend fun deleteBoards(provider: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBoards(boards: List<BoardEntity>)
}
