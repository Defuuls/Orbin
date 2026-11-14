package com.orbin.domain.repository

/** Lightweight management surface for the on-disk streaming video cache. */
interface VideoCacheRepository {
    suspend fun usageBytes(): Long

    suspend fun clear()

    suspend fun setMaxCacheBytes(bytes: Long)
}
