package com.orbin.ios

import kotlin.test.Test
import kotlin.test.assertContentEquals

class DownloadedMediaBytesTest {
    @Test
    fun nativeCopyPreservesEmptyAndOrdinaryData() {
        for (bytes in listOf(byteArrayOf(), byteArrayOf(0, 1, -1, 127))) {
            assertContentEquals(bytes, bytes.toNSData().toDownloadedMediaBytes())
        }
    }
}
