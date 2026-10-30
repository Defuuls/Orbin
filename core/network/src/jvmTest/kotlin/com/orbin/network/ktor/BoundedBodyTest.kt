package com.orbin.network.ktor

import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.runBlocking
import kotlinx.io.IOException
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class BoundedBodyTest {
    @Test
    fun `accepts empty exact limit and ordinary binary data`() =
        runBlocking {
            for (bytes in listOf(byteArrayOf(), byteArrayOf(0, -1, 127, 1))) {
                assertArrayEquals(bytes, ByteReadChannel(bytes).readBoundedBytes(bytes.size))
            }
        }

    @Test
    fun `rejects actual overflow independently of headers`() {
        assertThrows(IOException::class.java) {
            runBlocking { ByteReadChannel(ByteArray(1025)).readBoundedBytes(1024) }
        }
    }
}
