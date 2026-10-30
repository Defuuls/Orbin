package com.orbin.feature.settings

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
