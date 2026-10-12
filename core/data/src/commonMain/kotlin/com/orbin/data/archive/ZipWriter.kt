package com.orbin.data.archive

import okio.Buffer
import okio.BufferedSink

/**
 * The smallest zip writer that every unzip tool reads: stored (uncompressed) entries, UTF-8 names.
 * Media is already compressed, so storing it costs nothing; the page and stylesheet are small.
 * Each entry is held in memory only while it is written.
 */
internal class ZipWriter(
    private val sink: BufferedSink,
) {
    private val central = Buffer()
    private var offset = 0L
    private var count = 0

    fun add(
        name: String,
        bytes: ByteArray,
    ) {
        val nameBytes = name.encodeToByteArray()
        val crc = crc32(bytes)
        sink.writeIntLe(LOCAL_HEADER)
        sink.writeShortLe(VERSION)
        sink.writeShortLe(UTF8_FLAG)
        sink.writeShortLe(STORED)
        sink.writeShortLe(0) // time
        sink.writeShortLe(DOS_DATE_1980)
        sink.writeIntLe(crc)
        sink.writeIntLe(bytes.size)
        sink.writeIntLe(bytes.size)
        sink.writeShortLe(nameBytes.size)
        sink.writeShortLe(0)
        sink.write(nameBytes)
        sink.write(bytes)

        central.writeIntLe(CENTRAL_HEADER)
        central.writeShortLe(VERSION)
        central.writeShortLe(VERSION)
        central.writeShortLe(UTF8_FLAG)
        central.writeShortLe(STORED)
        central.writeShortLe(0)
        central.writeShortLe(DOS_DATE_1980)
        central.writeIntLe(crc)
        central.writeIntLe(bytes.size)
        central.writeIntLe(bytes.size)
        central.writeShortLe(nameBytes.size)
        central.writeShortLe(0) // extra
        central.writeShortLe(0) // comment
        central.writeShortLe(0) // disk
        central.writeShortLe(0) // internal attributes
        central.writeIntLe(0) // external attributes
        central.writeIntLe(offset.toInt())
        central.write(nameBytes)

        offset += LOCAL_HEADER_SIZE + nameBytes.size + bytes.size
        count++
    }

    fun finish() {
        val centralSize = central.size
        sink.writeAll(central)
        sink.writeIntLe(END_OF_CENTRAL)
        sink.writeShortLe(0)
        sink.writeShortLe(0)
        sink.writeShortLe(count)
        sink.writeShortLe(count)
        sink.writeIntLe(centralSize.toInt())
        sink.writeIntLe(offset.toInt())
        sink.writeShortLe(0)
        sink.flush()
    }

    private companion object {
        const val LOCAL_HEADER = 0x04034b50
        const val CENTRAL_HEADER = 0x02014b50
        const val END_OF_CENTRAL = 0x06054b50
        const val VERSION = 20
        const val UTF8_FLAG = 0x0800
        const val STORED = 0
        const val DOS_DATE_1980 = 0x21 // 1 January 1980, the earliest a zip can say
        const val LOCAL_HEADER_SIZE = 30
    }
}

private const val CRC_POLYNOMIAL = 0xEDB88320.toInt()
private const val BYTE_VALUES = 256
private const val BITS = 8
private const val BYTE_MASK = 0xFF

private val CRC_TABLE =
    IntArray(BYTE_VALUES) { n ->
        var c = n
        repeat(BITS) { c = if (c and 1 != 0) (c ushr 1) xor CRC_POLYNOMIAL else c ushr 1 }
        c
    }

/** The CRC-32 a zip entry carries. */
internal fun crc32(bytes: ByteArray): Int {
    var crc = -1
    for (byte in bytes) crc = CRC_TABLE[(crc xor byte.toInt()) and BYTE_MASK] xor (crc ushr BITS)
    return crc.inv()
}
