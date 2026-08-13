package com.orbin.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.orbin.data.database.entity.SavedPostEntity
import com.orbin.data.database.entity.SavedThreadEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SavedThreadDao {
    @Query("SELECT * FROM saved_threads ORDER BY savedAtMillis DESC")
    fun observeSavedThreads(): Flow<List<SavedThreadEntity>>

    @Query(
        "SELECT * FROM saved_threads WHERE provider = :provider AND board = :board AND thread = :thread LIMIT 1",
    )
    suspend fun getSavedThread(
        provider: String,
        board: String,
        thread: Long,
    ): SavedThreadEntity?

    @Query(
        "SELECT * FROM saved_posts WHERE provider = :provider AND board = :board AND thread = :thread " +
            "ORDER BY sortIndex ASC",
    )
    suspend fun getSavedPosts(
        provider: String,
        board: String,
        thread: Long,
    ): List<SavedPostEntity>

    /**
     * Saves a thread, replacing any earlier copy of it. Re-saving is how a reader captures replies
     * posted since last time, so the posts are swapped wholesale in one transaction rather than
     * merged — a half-replaced thread would read as a thread that lost its middle.
     */
    @Transaction
    suspend fun save(
        thread: SavedThreadEntity,
        posts: List<SavedPostEntity>,
    ) {
        deletePosts(thread.provider, thread.board, thread.thread)
        upsertThread(thread)
        insertPosts(posts)
    }

    @Transaction
    suspend fun delete(
        provider: String,
        board: String,
        thread: Long,
    ) {
        deletePosts(provider, board, thread)
        deleteThread(provider, board, thread)
    }

    @Upsert
    suspend fun upsertThread(thread: SavedThreadEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPosts(posts: List<SavedPostEntity>)

    @Query("DELETE FROM saved_posts WHERE provider = :provider AND board = :board AND thread = :thread")
    suspend fun deletePosts(
        provider: String,
        board: String,
        thread: Long,
    )

    @Query("DELETE FROM saved_threads WHERE provider = :provider AND board = :board AND thread = :thread")
    suspend fun deleteThread(
        provider: String,
        board: String,
        thread: Long,
    )
}
