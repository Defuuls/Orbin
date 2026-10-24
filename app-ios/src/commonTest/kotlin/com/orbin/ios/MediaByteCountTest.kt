package com.orbin.ios

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class MediaByteCountTest {
    @Test
    fun representableLengthsArePreservedWithoutAllocating() {
        for (length in listOf(0, 1, 1024, Int.MAX_VALUE)) {
            assertEquals(length, checkedMediaByteCount(length.toULong()))
        }
    }

    @Test
    fun oversizedAndWrappedLengthsAreRejectedBeforeNarrowing() {
        for (length in listOf(Int.MAX_VALUE.toULong() + 1uL, 1uL shl 32, (1uL shl 32) + 1uL, ULong.MAX_VALUE)) {
            assertFailsWith<IllegalArgumentException> { checkedMediaByteCount(length) }
        }
    }
}
