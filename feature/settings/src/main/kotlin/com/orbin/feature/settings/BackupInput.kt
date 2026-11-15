package com.orbin.feature.settings

import kotlinx.coroutines.ensureActive
import java.io.ByteArrayOutputStream
import java.io.InputStream

/** Bound the document provider's actual bytes before decoding or parsing the backup. */
internal fun InputStream.readBackupText(): String {
    val maxBytes = MAX_BACKUP_INPUT_BYTES
    val output = ByteArrayOutputStream()
    val chunk = ByteArray(BACKUP_READ_CHUNK_BYTES)
    var total = 0
    while (true) {
        val count = read(chunk, 0, minOf(chunk.size, maxBytes - total + 1))
        if (count == -1) break
        total += count
        require(total <= maxBytes) { "Backup file is too large to import." }
        output.write(chunk, 0, count)
    }
    return output.toByteArray().decodeToString(throwOnInvalidSequence = true)
}

private const val MAX_BACKUP_INPUT_BYTES = 5 * 1024 * 1024 * 4
private const val BACKUP_READ_CHUNK_BYTES = 8192

/** ZIPs may contain large videos; bound the streamed archive without buffering it in memory. */
internal suspend fun InputStream.copyBackupArchiveTo(output: java.io.OutputStream) {
    val chunk = ByteArray(ARCHIVE_CHUNK_BYTES)
    var total = 0L
    while (true) {
        kotlinx.coroutines.currentCoroutineContext().ensureActive()
        val count = read(chunk)
        if (count == -1) break
        total += count
        require(total <= MAX_ARCHIVE_INPUT_BYTES) { "Backup file is too large to import." }
        output.write(chunk, 0, count)
    }
}

private const val ARCHIVE_CHUNK_BYTES = 64 * 1024
private const val MAX_ARCHIVE_INPUT_BYTES = 2L * 1024 * 1024 * 1024 + 16 * 1024 * 1024
