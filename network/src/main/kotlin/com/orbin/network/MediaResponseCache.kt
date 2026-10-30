package com.orbin.network

import okio.Source

/** Persistent media bytes and metadata must be encrypted by the platform implementation. */
interface MediaResponseCache {
    fun open(
        key: String,
        nowMillis: Long,
    ): Entry?

    fun start(
        key: String,
        contentType: String,
        expiresAtMillis: Long,
        statusCode: Int = 200,
        contentRange: String? = null,
    ): Write

    fun clear()

    fun usageBytes(): Long

    data class Entry(
        val contentType: String,
        val byteCount: Long,
        val source: Source,
        val storedAtMillis: Long = 0,
        val statusCode: Int = 200,
        val contentRange: String? = null,
    )

    interface Write {
        fun append(bytes: ByteArray)

        fun commit()

        fun abort()
    }
}
