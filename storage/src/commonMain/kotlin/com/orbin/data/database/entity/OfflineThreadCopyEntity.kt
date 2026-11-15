package com.orbin.data.database.entity

import androidx.room.Entity

/** Automatic last-loaded snapshot, distinct from an explicitly saved thread. */
@Entity(tableName = "offline_thread_copies", primaryKeys = ["provider", "board", "thread"])
data class OfflineThreadCopyEntity(
    val provider: String,
    val board: String,
    val thread: Long,
    val payload: String,
    val updatedAtMillis: Long,
    val sizeBytes: Long,
)
