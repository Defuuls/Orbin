package com.orbin.domain.repository

/** Lightweight management surface for the on-disk streaming video cache. */
interface VideoCacheRepository {
    suspend fun usageBytes(): Long

    suspend fun clear()

    /**
     * Removes everything cached except the files behind a video that is open right now, so
     * leaving a locked app does not break the playback the reader returns to.
     */
    suspend fun clearExceptOpenVideos()

    suspend fun setMaxCacheBytes(bytes: Long)
}
