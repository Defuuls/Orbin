package com.orbin.ios

import com.orbin.network.ktor.MAX_BUFFERED_MEDIA_BYTES
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class MediaByteCountTest {
    @Test
    fun representableLengthsArePreservedWithoutAllocating() {
        for (length in listOf(0, 1, 1024, MAX_BUFFERED_MEDIA_BYTES)) {
            assertEquals(length, checkedMediaByteCount(length.toULong()))
        }
    }

    @Test
    fun oversizedAndWrappedLengthsAreRejectedBeforeNarrowing() {
        for (length in listOf(
            MAX_BUFFERED_MEDIA_BYTES.toULong() + 1uL,
            Int.MAX_VALUE.toULong() + 1uL,
            1uL shl 32,
            (1uL shl 32) + 1uL,
            ULong.MAX_VALUE,
        )) {
            assertFailsWith<IllegalArgumentException> { checkedMediaByteCount(length) }
        }
    }
}
