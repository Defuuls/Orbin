package com.orbin.media.video

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener

/**
 * Serves a prefetched prefix before lazily opening the remaining network range. Seeking outside
 * that prefix delegates directly to the normal, policy-protected video transport.
 */
@Suppress("UnsafeOptInUsageError")
internal class VideoPrefixDataSource(
    private val upstream: DataSource,
    private val cache: VideoPrefixCache,
) : DataSource {
    private var request: DataSpec? = null
    private var prefix: VideoPrefixCache.Entry? = null
    private var readBytes = 0L
    private var remaining = C.LENGTH_UNSET.toLong()
    private var upstreamOpened = false

    override fun addTransferListener(transferListener: TransferListener) =
        upstream.addTransferListener(transferListener)

    override fun open(dataSpec: DataSpec): Long {
        request = dataSpec
        readBytes = 0L
        prefix =
            if (dataSpec.httpMethod == DataSpec.HTTP_METHOD_GET) {
                cache.get(dataSpec.uri.toString())?.takeIf { dataSpec.position < it.bytes.size }
            } else {
                null
            }
        val cached = prefix
        if (cached == null) {
            upstreamOpened = true
            remaining = upstream.open(dataSpec)
        } else {
            remaining =
                when {
                    cached.totalLength >= 0L -> {
                        val available = (cached.totalLength - dataSpec.position).coerceAtLeast(0L)
                        if (dataSpec.length >= 0L) minOf(dataSpec.length, available) else available
                    }
                    dataSpec.length != C.LENGTH_UNSET.toLong() -> dataSpec.length
                    else -> C.LENGTH_UNSET.toLong()
                }
        }
        return remaining
    }

    override fun read(
        buffer: ByteArray,
        offset: Int,
        length: Int,
    ): Int {
        if (length == 0) return 0
        if (remaining == 0L) return C.RESULT_END_OF_INPUT
        val spec = checkNotNull(request) { "DataSource is not open" }
        val cached = prefix
        val position = spec.position + readBytes
        val available = cached?.let { (it.bytes.size.toLong() - position).coerceAtLeast(0L) } ?: 0L
        val count =
            if (available > 0L && cached != null) {
                val size = minOf(length.toLong(), available, if (remaining >= 0L) remaining else Long.MAX_VALUE).toInt()
                cached.bytes.copyInto(buffer, offset, position.toInt(), position.toInt() + size)
                size
            } else {
                if (!upstreamOpened) {
                    upstreamOpened = true
                    val resolved = upstream.open(spec.subrange(readBytes))
                    if (remaining < 0L) remaining = resolved
                }
                if (remaining == 0L) return C.RESULT_END_OF_INPUT
                upstream.read(
                    buffer,
                    offset,
                    if (remaining >=
                        0L
                    ) {
                        minOf(length.toLong(), remaining).toInt()
                    } else {
                        length
                    },
                )
            }
        if (count > 0) {
            readBytes += count
            if (remaining >= 0L) remaining -= count
        }
        return count
    }

    override fun getUri(): Uri? = if (upstreamOpened) upstream.uri else request?.uri

    override fun getResponseHeaders(): Map<String, List<String>> =
        if (upstreamOpened) upstream.responseHeaders else prefix?.headers.orEmpty()

    override fun close() {
        try {
            if (upstreamOpened) upstream.close()
        } finally {
            upstreamOpened = false
            request = null
            prefix = null
            readBytes = 0L
            remaining = C.LENGTH_UNSET.toLong()
        }
    }

    class Factory(
        private val upstream: DataSource.Factory,
        private val cache: VideoPrefixCache,
    ) : DataSource.Factory {
        override fun createDataSource(): DataSource = VideoPrefixDataSource(upstream.createDataSource(), cache)
    }
}
