package com.orbin.data.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import com.orbin.data.database.entity.BookmarkEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BookmarkDao {
    @Query("SELECT * FROM bookmarks ORDER BY createdAtMillis DESC")
    fun observeAll(): Flow<List<BookmarkEntity>>

    @Query(
        "SELECT * FROM bookmarks WHERE provider = :provider AND board = :board AND thread = :thread LIMIT 1",
    )
    fun observeOne(
        provider: String,
        board: String,
        thread: Long,
    ): Flow<BookmarkEntity?>

    @Upsert
    suspend fun upsert(bookmark: BookmarkEntity)

    @Delete
    suspend fun delete(bookmark: BookmarkEntity)

    @Query("DELETE FROM bookmarks WHERE provider = :provider AND board = :board AND thread = :thread")
    suspend fun deleteByKey(
        provider: String,
        board: String,
        thread: Long,
    )

    @Query(
        "UPDATE bookmarks SET isWatched = :watched WHERE provider = :provider AND board = :board AND thread = :thread",
    )
    suspend fun setWatched(
        provider: String,
        board: String,
        thread: Long,
        watched: Boolean,
    )

    @Query(
        "UPDATE bookmarks SET lastSeenReplyCount = latestReplyCount " +
            "WHERE provider = :provider AND board = :board AND thread = :thread",
    )
    suspend fun markRead(
        provider: String,
        board: String,
        thread: Long,
    )

    @Query(
        "UPDATE bookmarks SET latestReplyCount = :latest, isThreadDead = :dead " +
            "WHERE provider = :provider AND board = :board AND thread = :thread",
    )
    suspend fun updateLatest(
        provider: String,
        board: String,
        thread: Long,
        latest: Int,
        dead: Boolean,
    )

    @Query("SELECT * FROM bookmarks WHERE isWatched = 1")
    suspend fun watchedBookmarks(): List<BookmarkEntity>
}
