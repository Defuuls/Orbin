package com.orbin.media.video

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.datasource.ByteArrayDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream

/** Reads real Media3 DataSource bytes, checking both emitted bytes and the network range boundary. */
@Suppress("UnsafeOptInUsageError")
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class VideoPrefixDataSourceTest {
    private val url = "https://media.example/clip.webm"
    private val bytes = ByteArray(16) { it.toByte() }
    private val cache = VideoPrefixCache()
    private val upstream = RecordingSource(bytes)

    @Test
    fun `prefetched bytes join the remaining stream without gaps or duplication`() {
        cache.put(url, bytes.copyOf(4), bytes.size.toLong(), emptyMap())
        val source = VideoPrefixDataSource(upstream, cache)
        assertThat(source.open(DataSpec.Builder().setUri(url).build())).isEqualTo(16L)
        assertThat(upstream.requests).isEmpty()
        assertThat(source.readAll()).isEqualTo(bytes)
        assertThat(upstream.requests.single().position).isEqualTo(4L)
        source.close()
    }

    @Test
    fun `seek across the prefix boundary keeps the requested length`() {
        cache.put(url, bytes.copyOf(4), bytes.size.toLong(), emptyMap())
        val source = VideoPrefixDataSource(upstream, cache)
        source.open(
            DataSpec
                .Builder()
                .setUri(url)
                .setPosition(2)
                .setLength(5)
                .build(),
        )
        assertThat(source.readAll()).isEqualTo(bytes.copyOfRange(2, 7))
        assertThat(upstream.requests.single().position).isEqualTo(4L)
        assertThat(upstream.requests.single().length).isEqualTo(3L)
        source.close()
    }

    @Test
    fun `a complete short clip requires no second network request`() {
        cache.put(url, bytes, bytes.size.toLong(), emptyMap())
        val source = VideoPrefixDataSource(upstream, cache)
        source.open(DataSpec.Builder().setUri(url).build())
        assertThat(source.readAll()).isEqualTo(bytes)
        assertThat(upstream.requests).isEmpty()
        source.close()
    }

    @Test
    fun `another URL or a seek beyond the prefix uses the upstream directly`() {
        cache.put(url, bytes.copyOf(4), bytes.size.toLong(), emptyMap())
        val source = VideoPrefixDataSource(upstream, cache)
        source.open(
            DataSpec
                .Builder()
                .setUri(url)
                .setPosition(8)
                .build(),
        )
        assertThat(source.readAll()).isEqualTo(bytes.copyOfRange(8, bytes.size))
        source.close()
        source.open(DataSpec.Builder().setUri("https://media.example/other.webm").build())
        assertThat(source.readAll()).isEqualTo(bytes)
        assertThat(upstream.requests.map { it.position }).containsExactly(8L, 0L).inOrder()
        source.close()
    }

    @Test
    fun `cache clears and refuses prefixes above its memory bound`() {
        cache.put(url, ByteArray(VideoPrefixCache.MAX_PREFIX_BYTES + 1), -1, emptyMap())
        assertThat(cache.get(url)).isNull()
        cache.put(url, bytes, bytes.size.toLong(), emptyMap())
        cache.clear()
        assertThat(cache.get(url)).isNull()
    }

    @Test
    fun `clearing cache atomically rejects a late callback from the old generation`() {
        val token = cache.beginPrefetch()
        cache.clear()
        assertThat(cache.put(url, bytes, bytes.size.toLong(), emptyMap(), token)).isFalse()
        assertThat(cache.get(url)).isNull()
    }

    @Test
    fun `new upcoming generation cannot be overwritten by the previous callback`() {
        val old = cache.beginPrefetch()
        val current = cache.beginPrefetch()
        val nextUrl = "https://media.example/next.webm"
        assertThat(cache.put(nextUrl, bytes, bytes.size.toLong(), emptyMap(), current)).isTrue()
        assertThat(cache.put(url, bytes, bytes.size.toLong(), emptyMap(), old)).isFalse()
        assertThat(cache.get(nextUrl)?.bytes).isEqualTo(bytes)
        assertThat(cache.get(url)).isNull()
    }

    @Test
    fun `cancelling an old job cannot invalidate a newer callback`() {
        val old = cache.beginPrefetch()
        val current = cache.beginPrefetch()
        cache.cancelPrefetch(old)
        assertThat(cache.put(url, bytes, bytes.size.toLong(), emptyMap(), current)).isTrue()
        cache.cancelPrefetch(current)
        assertThat(cache.put(url, bytes, bytes.size.toLong(), emptyMap(), current)).isFalse()
    }

    private fun DataSource.readAll(): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(3)
        while (true) {
            val count = read(buffer, 0, buffer.size)
            if (count == C.RESULT_END_OF_INPUT) return output.toByteArray()
            output.write(buffer, 0, count)
        }
    }

    private class RecordingSource(
        bytes: ByteArray,
    ) : DataSource {
        private val delegate = ByteArrayDataSource(bytes)
        val requests = mutableListOf<DataSpec>()

        override fun addTransferListener(transferListener: TransferListener) =
            delegate.addTransferListener(transferListener)

        override fun open(dataSpec: DataSpec): Long {
            requests += dataSpec
            return delegate.open(dataSpec)
        }

        override fun read(
            buffer: ByteArray,
            offset: Int,
            length: Int,
        ): Int = delegate.read(buffer, offset, length)

        override fun getUri(): Uri? = delegate.uri

        override fun close() = delegate.close()
    }
}
