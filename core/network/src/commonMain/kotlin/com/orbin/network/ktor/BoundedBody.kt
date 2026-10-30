package com.orbin.network.ktor

import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readBuffer
import kotlinx.io.IOException
import kotlinx.io.readByteArray

/** Read actual bytes, including a one-byte overflow probe; Content-Length is not trusted. */
suspend fun ByteReadChannel.readBoundedBytes(maxBytes: Int): ByteArray {
    require(maxBytes >= 0)
    val bytes = readBuffer(maxBytes.toLong() + 1).readByteArray()
    if (bytes.size > maxBytes) {
        cancel(null)
        throw IOException("Response exceeds the permitted size")
    }
    return bytes
}

/** Call from a streaming HttpStatement.execute block, before Ktor saves the response. */
suspend fun HttpResponse.readBoundedBytes(maxBytes: Int): ByteArray = bodyAsChannel().readBoundedBytes(maxBytes)

/** A single buffered media file; callers must also bound concurrent transfers. */
const val MAX_BUFFERED_MEDIA_BYTES: Int = 32 * 1024 * 1024
