package com.orbin.feature.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.InputStream

class BackupInputTest {
    @Test
    fun `ordinary unicode backup is preserved`() {
        val json = "{\"title\":\"日本語 😀\"}"
        assertEquals(json, json.byteInputStream().readBackupText())
    }

    @Test
    fun `provider with no declared length is rejected at the actual byte budget`() {
        val input =
            object : InputStream() {
                override fun read(): Int = 'x'.code

                override fun read(
                    bytes: ByteArray,
                    offset: Int,
                    length: Int,
                ): Int {
                    bytes.fill('x'.code.toByte(), offset, offset + length)
                    return length
                }
            }
        assertThrows(IllegalArgumentException::class.java) { input.readBackupText() }
    }
}
