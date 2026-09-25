package com.orbin.domain.repository

/** Lightweight management surface for the on-disk image cache. */
interface ImageCacheRepository {
    suspend fun usageBytes(): Long

    suspend fun clear()
}
