package com.orbin.network.interceptor

import com.google.common.truth.Truth.assertThat
import com.orbin.network.MediaResponseCache
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import okio.Buffer
import okio.Source
import okio.Timeout
import okio.buffer
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.IOException
import java.util.zip.CRC32

/** Packetized origin reads exercise delivery and cache writes independently of network timing. */
class MediaResponseCacheStreamingTest {
    @Test
    fun `small origin packets are delivered intact and persisted in bounded batches`() {
        val payload = payload(1024 * 1024 + 17)
        val fixture = Fixture(payload)

        fixture.open().use { assertThat(it.body.bytes()).isEqualTo(payload) }

        val write = fixture.cache.writes.single()
        assertThat(write.appendSizes).containsExactlyElementsIn(List(16) { BATCH_BYTES } + 17).inOrder()
        assertThat(write.committed).isTrue()
        assertThat(write.aborted).isFalse()
        fixture.open().use { assertThat(it.body.bytes()).isEqualTo(payload) }
        assertThat(fixture.originRequests).isEqualTo(1)
    }

    @Test
    fun `player receives the first packets before a cache batch fills`() {
        val payload = payload(BATCH_BYTES * 2)
        val fixture = Fixture(payload)

        fixture.open().use { response ->
            val first = ByteArray(8 * 1024)
            response.body.source().readFully(first)
            assertThat(first).isEqualTo(payload.copyOf(first.size))
            assertThat(fixture.sources.single().position).isEqualTo(first.size)
            assertThat(
                fixture.cache.writes
                    .single()
                    .appendSizes,
            ).isEmpty()
            assertThat(
                fixture.cache.writes
                    .single()
                    .committed,
            ).isFalse()
        }
    }

    @Test
    fun `declared length commits the final remainder without an extra EOF read`() {
        val payload = payload(BATCH_BYTES + 17)
        val fixture = Fixture(payload)

        fixture.open().use { response ->
            val received = ByteArray(payload.size)
            response.body.source().readFully(received)
            assertThat(received).isEqualTo(payload)
            assertThat(fixture.sources.single().eofReads).isEqualTo(0)
            assertThat(
                fixture.cache.writes
                    .single()
                    .committed,
            ).isTrue()
        }

        assertThat(
            fixture.cache.writes
                .single()
                .appendSizes,
        ).containsExactly(BATCH_BYTES, 17).inOrder()
        fixture.open().use { assertThat(it.body.bytes()).isEqualTo(payload) }
        assertThat(fixture.originRequests).isEqualTo(1)
    }

    @Test
    fun `unknown body length flushes its remainder at EOF and can reopen from cache`() {
        val payload = payload(BATCH_BYTES + 17)
        val fixture = Fixture(payload, declaredLength = -1L)

        fixture.open().use { assertThat(it.body.bytes()).isEqualTo(payload) }

        assertThat(fixture.sources.single().eofReads).isEqualTo(1)
        assertThat(
            fixture.cache.writes
                .single()
                .appendSizes,
        ).containsExactly(BATCH_BYTES, 17).inOrder()
        assertThat(
            fixture.cache.writes
                .single()
                .committed,
        ).isTrue()
        fixture.open().use { assertThat(it.body.bytes()).isEqualTo(payload) }
        assertThat(fixture.originRequests).isEqualTo(1)
    }

    @Test
    fun `closing early aborts complete batches and discards the buffered remainder`() {
        val fixture = Fixture(payload(BATCH_BYTES * 2))

        fixture.open().use { response ->
            response.body.source().readFully(ByteArray(BATCH_BYTES + 4096))
            assertThat(
                fixture.cache.writes
                    .single()
                    .appendSizes,
            ).containsExactly(BATCH_BYTES)
            assertThat(
                fixture.cache.writes
                    .single()
                    .committed,
            ).isFalse()
        }

        assertThat(
            fixture.cache.writes
                .single()
                .aborted,
        ).isTrue()
        assertThat(
            fixture.cache.writes
                .single()
                .appendSizes,
        ).containsExactly(BATCH_BYTES)
        assertThat(fixture.cache.entryCount).isEqualTo(0)
    }

    @Test
    fun `origin failure preserves already delivered bytes and aborts the partial cache`() {
        val payload = payload(BATCH_BYTES * 2)
        val failureAt = BATCH_BYTES + 4096
        val fixture = Fixture(payload, failAt = failureAt)
        val received = Buffer()

        fixture.open().use { response ->
            assertThrows(IOException::class.java) {
                while (response.body.source().read(received, 1024) != -1L) {
                    // Bytes must reach the caller before the later origin failure.
                }
            }
        }

        assertThat(received.readByteArray()).isEqualTo(payload.copyOf(failureAt))
        assertThat(
            fixture.cache.writes
                .single()
                .appendSizes,
        ).containsExactly(BATCH_BYTES)
        assertThat(
            fixture.cache.writes
                .single()
                .aborted,
        ).isTrue()
        assertThat(
            fixture.cache.writes
                .single()
                .committed,
        ).isFalse()
        assertThat(fixture.cache.entryCount).isEqualTo(0)
    }

    @Test
    fun `truncated declared body never commits its buffered tail`() {
        val payload = payload(BATCH_BYTES + 17)
        val fixture = Fixture(payload, declaredLength = payload.size.toLong() + 100)
        val received = Buffer()

        fixture.open().use { response ->
            response.body.source().readAll(received)
        }

        assertThat(received.readByteArray()).isEqualTo(payload)
        assertThat(
            fixture.cache.writes
                .single()
                .appendSizes,
        ).containsExactly(BATCH_BYTES)
        assertThat(
            fixture.cache.writes
                .single()
                .aborted,
        ).isTrue()
        assertThat(
            fixture.cache.writes
                .single()
                .committed,
        ).isFalse()
        assertThat(fixture.cache.entryCount).isEqualTo(0)
    }

    @Test
    fun `cache write failure leaves the full origin stream readable`() {
        val payload = payload(BATCH_BYTES * 2 + 17)
        val fixture = Fixture(payload, failWrites = true)

        fixture.open().use { assertThat(it.body.bytes()).isEqualTo(payload) }

        assertThat(
            fixture.cache.writes
                .single()
                .appendSizes,
        ).containsExactly(BATCH_BYTES)
        assertThat(
            fixture.cache.writes
                .single()
                .aborted,
        ).isTrue()
        assertThat(
            fixture.cache.writes
                .single()
                .committed,
        ).isFalse()
        assertThat(fixture.cache.entryCount).isEqualTo(0)
    }

    @Test
    fun `unknown length over budget stops caching while continuing to deliver the origin`() {
        val payload = payload(MAX_CACHE_BYTES + 17)
        val fixture = Fixture(payload, declaredLength = -1L, retainWrites = false)
        val checksum = CRC32()
        var count = 0L

        fixture.open().use { response ->
            val buffer = Buffer()
            while (response.body.source().read(buffer, BATCH_BYTES.toLong()) != -1L) {
                val bytes = buffer.readByteArray()
                count += bytes.size
                checksum.update(bytes)
            }
        }

        assertThat(count).isEqualTo(payload.size.toLong())
        assertThat(checksum.value).isEqualTo(CRC32().apply { update(payload) }.value)
        val write = fixture.cache.writes.single()
        assertThat(write.appendSizes.sum()).isEqualTo(MAX_CACHE_BYTES)
        assertThat(write.appendSizes.all { it == BATCH_BYTES }).isTrue()
        assertThat(write.aborted).isTrue()
        assertThat(write.committed).isFalse()
        assertThat(fixture.cache.entryCount).isEqualTo(0)
    }

    private class Fixture(
        private val payload: ByteArray,
        private val declaredLength: Long = payload.size.toLong(),
        private val failAt: Int? = null,
        failWrites: Boolean = false,
        retainWrites: Boolean = true,
    ) {
        val cache = RecordingCache(failWrites, retainWrites)
        val sources = mutableListOf<PacketSource>()
        var originRequests = 0
            private set
        private val request = Request.Builder().url("https://media.example/clip.webm").build()
        private val client =
            OkHttpClient
                .Builder()
                .addInterceptor(MediaResponseCacheInterceptor(cache, now = { 1000L }))
                .addInterceptor { chain ->
                    originRequests++
                    val packets = PacketSource(payload, failAt).also(sources::add)
                    Response
                        .Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body(
                            object : ResponseBody() {
                                private val input = packets.buffer()

                                override fun contentType() = "video/webm".toMediaType()

                                override fun contentLength() = declaredLength

                                override fun source() = input
                            },
                        ).build()
                }.build()

        fun open(): Response = client.newCall(request).execute()
    }

    /** Returns one network-sized packet per read, without waiting for the consumer's full request. */
    private class PacketSource(
        private val bytes: ByteArray,
        private val failAt: Int?,
    ) : Source {
        var position = 0
            private set
        var eofReads = 0
            private set

        override fun read(
            sink: Buffer,
            byteCount: Long,
        ): Long {
            if (byteCount == 0L) return 0L
            if (failAt != null && position >= failAt) throw IOException("Origin disconnected")
            if (position == bytes.size) {
                eofReads++
                return -1L
            }
            val count =
                minOf(
                    byteCount,
                    1024L,
                    (bytes.size - position).toLong(),
                    ((failAt ?: bytes.size) - position).toLong(),
                )
            sink.write(bytes, position, count.toInt())
            position += count.toInt()
            return count
        }

        override fun timeout(): Timeout = Timeout.NONE

        override fun close() = Unit
    }

    private class RecordingCache(
        private val failWrites: Boolean,
        private val retainWrites: Boolean,
    ) : MediaResponseCache {
        private val entries = mutableMapOf<String, ByteArray>()
        val writes = mutableListOf<RecordingWrite>()
        val entryCount: Int get() = entries.size

        override fun open(
            key: String,
            nowMillis: Long,
        ): MediaResponseCache.Entry? =
            entries[key]?.let { MediaResponseCache.Entry("video/webm", it.size.toLong(), Buffer().write(it)) }

        override fun start(
            key: String,
            contentType: String,
            expiresAtMillis: Long,
            statusCode: Int,
            contentRange: String?,
        ): MediaResponseCache.Write = RecordingWrite(failWrites, retainWrites) { entries[key] = it }.also(writes::add)

        override fun clear() = entries.clear()

        override fun usageBytes(): Long = entries.values.sumOf { it.size.toLong() }
    }

    private class RecordingWrite(
        private val failWrites: Boolean,
        private val retainWrites: Boolean,
        private val onCommit: (ByteArray) -> Unit,
    ) : MediaResponseCache.Write {
        private val bytes = Buffer()
        val appendSizes = mutableListOf<Int>()
        var committed = false
            private set
        var aborted = false
            private set

        override fun append(bytes: ByteArray) {
            appendSizes += bytes.size
            if (failWrites) throw IOException("Cache unavailable")
            if (retainWrites) this.bytes.write(bytes)
        }

        override fun commit() {
            committed = true
            onCommit(bytes.readByteArray())
        }

        override fun abort() {
            aborted = true
            bytes.clear()
        }
    }

    private fun payload(size: Int): ByteArray = ByteArray(size) { (it % 251).toByte() }

    private companion object {
        const val BATCH_BYTES = 64 * 1024
        const val MAX_CACHE_BYTES = 32 * 1024 * 1024
    }
}
