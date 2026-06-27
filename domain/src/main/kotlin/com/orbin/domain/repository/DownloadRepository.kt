package com.orbin.domain.repository

import com.orbin.core.model.DownloadRecord
import kotlinx.coroutines.flow.Flow

/**
 * Enqueues media downloads and exposes their history. Implemented in :data over the platform
 * download manager, which provides notifications, resume and retry natively.
 */
interface DownloadRepository {
    /** Observe download history, most recent first. */
    fun observeDownloads(): Flow<List<DownloadRecord>>

    /** Enqueue a download of [url] saved as [fileName]. Returns the platform download id. */
    suspend fun enqueue(
        url: String,
        fileName: String,
    ): Long

    /** Mark a recorded download with the latest known [com.orbin.core.model.DownloadStatus]. */
    suspend fun refreshStatuses()

    suspend fun clearHistory()
}
