package com.orbin.ios

/** Validate a native byte count before narrowing it for a Kotlin array allocation. */
internal fun checkedMediaByteCount(length: ULong): Int {
    require(length <= Int.MAX_VALUE.toULong()) { "Downloaded media is too large to read into memory" }
    return length.toInt()
}
