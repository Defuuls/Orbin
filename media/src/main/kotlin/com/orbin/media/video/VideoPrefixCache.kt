package com.orbin.media.video

import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** One upcoming clip's bounded, process-only bytes; nothing is written as plaintext on disk. */
@Singleton
class VideoPrefixCache
    @Inject
    constructor() {
        internal data class Entry(
            val url: String,
            val bytes: ByteArray,
            val totalLength: Long,
            val headers: Map<String, List<String>>,
            val expiresAtNanos: Long,
        )

        private var entry: Entry? = null
        private var generation = 0L

        /** Invalidates earlier callbacks without discarding a prefix the visible player may use. */
        @Synchronized
        internal fun beginPrefetch(): Long = ++generation

        @Synchronized
        internal fun cancelPrefetch(expectedGeneration: Long) {
            if (generation == expectedGeneration) generation++
        }

        @Synchronized
        internal fun get(url: String): Entry? = entry?.takeIf { it.url == url && System.nanoTime() < it.expiresAtNanos }

        @Synchronized
        internal fun put(
            url: String,
            bytes: ByteArray,
            totalLength: Long,
            headers: Map<String, List<String>>,
            expectedGeneration: Long? = null,
        ): Boolean {
            if (expectedGeneration != null && expectedGeneration != generation) return false
            if (bytes.isEmpty() || bytes.size > MAX_PREFIX_BYTES) return false
            entry = Entry(url, bytes, totalLength, headers, System.nanoTime() + TimeUnit.SECONDS.toNanos(TTL_SECONDS))
            return true
        }

        @Synchronized
        fun clear() {
            generation++
            entry = null
        }

        companion object {
            const val MAX_PREFIX_BYTES = 1024 * 1024
            private const val TTL_SECONDS = 60L
        }
    }
