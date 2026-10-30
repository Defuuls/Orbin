package com.orbin.data.util

import okio.Buffer
import okio.IOException
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith

class BoundedFileReadTest {
    @Test
    fun actualBytesAreBoundedWithoutFileMetadata() {
        assertContentEquals(byteArrayOf(), Buffer().readBoundedBytes(0))
        val bytes = byteArrayOf(0, -1, 127, 1)
        assertContentEquals(bytes, Buffer().write(bytes).readBoundedBytes(bytes.size))
        assertFailsWith<IOException> { Buffer().write(bytes).readBoundedBytes(bytes.size - 1) }
        assertFailsWith<IOException> { Buffer().writeByte(1).readBoundedBytes(0) }
    }
}
