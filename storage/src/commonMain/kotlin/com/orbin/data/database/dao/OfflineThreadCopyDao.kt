package com.orbin.data.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.orbin.core.model.ThreadKey
import com.orbin.data.database.entity.OfflineThreadCopyEntity

/** Stored in Orbin's existing encrypted Android database and iOS protected support database. */
@Dao
interface OfflineThreadCopyDao {
    @Query(
        "SELECT * FROM offline_thread_copies WHERE provider = :provider AND board = :board AND thread = :thread LIMIT 1",
    )
    suspend fun get(
        provider: String,
        board: String,
        thread: Long,
    ): OfflineThreadCopyEntity?

    @Query(
        "SELECT provider, board, thread, updatedAtMillis, sizeBytes FROM offline_thread_copies ORDER BY updatedAtMillis ASC",
    )
    suspend fun entries(): List<OfflineThreadCopyInfo>

    @Query(
        "SELECT EXISTS(SELECT 1 FROM bookmarks WHERE provider = :provider AND board = :board AND thread = :thread) OR EXISTS(SELECT 1 FROM history WHERE provider = :provider AND board = :board AND thread = :thread)",
    )
    suspend fun hasPersistentOwner(
        provider: String,
        board: String,
        thread: Long,
    ): Boolean

    @Upsert
    suspend fun upsert(copy: OfflineThreadCopyEntity)

    @Query("DELETE FROM offline_thread_copies WHERE provider = :provider AND board = :board AND thread = :thread")
    suspend fun delete(
        provider: String,
        board: String,
        thread: Long,
    )

    /** Ownership and replacement share a transaction with pruning, avoiding clear-history races. */
    @Transaction
    suspend fun putBounded(
        copy: OfflineThreadCopyEntity,
        maxCopies: Int,
        maxBytes: Long,
    ) {
        if (!hasPersistentOwner(copy.provider, copy.board, copy.thread)) return
        upsert(copy)
        val all = entries()
        var count = all.size
        var bytes = all.sumOf { it.sizeBytes }
        for (entry in all) {
            if (count <= maxCopies && bytes <= maxBytes) break
            delete(entry.provider, entry.board, entry.thread)
            count--
            bytes -= entry.sizeBytes
        }
    }

    @Transaction
    suspend fun retain(keys: Set<ThreadKey>) {
        val retained = keys.mapTo(mutableSetOf()) { Triple(it.provider.value, it.board.value, it.thread.value) }
        entries().forEach { entry ->
            if (Triple(entry.provider, entry.board, entry.thread) !in
                retained
            ) {
                delete(entry.provider, entry.board, entry.thread)
            }
        }
    }
}

data class OfflineThreadCopyInfo(
    val provider: String,
    val board: String,
    val thread: Long,
    val updatedAtMillis: Long,
    val sizeBytes: Long,
)
