package com.orbin.data.database.entity

import androidx.room.Entity

@Entity(tableName = "thread_memory", primaryKeys = ["provider", "board", "thread"])
data class ThreadMemoryEntity(
    val provider: String,
    val board: String,
    val thread: Long,
    val updatedAtMillis: Long,
    val payload: String,
)

@Entity(tableName = "media_library", primaryKeys = ["id"])
data class MediaLibraryEntity(
    val id: String,
    val provider: String,
    val storageKey: String,
    val fileName: String,
    val savedAtMillis: Long,
    val payload: String,
)
