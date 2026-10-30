package com.orbin.ios

import com.orbin.network.ktor.MAX_BUFFERED_MEDIA_BYTES

/** Validate a native byte count before narrowing it for a Kotlin array allocation. */
internal fun checkedMediaByteCount(length: ULong): Int {
    require(length <= MAX_BUFFERED_MEDIA_BYTES.toULong()) { "Downloaded media is too large to read into memory" }
    return length.toInt()
}
