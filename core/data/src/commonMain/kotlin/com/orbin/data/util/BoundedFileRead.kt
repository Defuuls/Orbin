package com.orbin.data.util

import okio.Buffer
import okio.BufferedSource
import okio.IOException

/** Bound actual input, not potentially stale or untrusted file metadata. */
fun BufferedSource.readBoundedBytes(maxBytes: Int): ByteArray {
    require(maxBytes >= 0)
    val buffer = Buffer()
    while (buffer.size <= maxBytes) {
        val count = read(buffer, minOf(FILE_READ_CHUNK_BYTES, maxBytes.toLong() + 1 - buffer.size))
        if (count == -1L) return buffer.readByteArray()
        if (buffer.size > maxBytes) throw IOException("File exceeds the permitted size")
    }
    throw IOException("File exceeds the permitted size")
}

/** Four UTF-8 bytes per character preserves the existing five-megacharacter import budget. */
const val MAX_BACKUP_INPUT_BYTES: Int = 5 * 1024 * 1024 * 4

private const val FILE_READ_CHUNK_BYTES = 8192L
