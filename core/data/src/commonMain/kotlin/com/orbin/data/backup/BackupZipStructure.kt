package com.orbin.data.backup

import okio.Buffer
import okio.FileSystem
import okio.Path
import okio.buffer
import okio.use

/** ZIP32 field positions from APPNOTE; names/counts are checked before any extraction. */
internal object BackupZipFormat {
    const val LOCAL_SIGNATURE = 0x04034b50
    const val CENTRAL_SIGNATURE = 0x02014b50
    const val END_SIGNATURE = 0x06054b50
    const val VERSION_20 = 20
    const val DOS_EPOCH_DAY = 33
    const val UTF8_FLAG = 0x0800
    const val ENCRYPTED_FLAG = 1
    const val DATA_DESCRIPTOR_FLAG = 8
    const val STORE = 0
    const val DEFLATE = 8
    const val LOCAL_BYTES = 30
    const val CENTRAL_BYTES = 46
    const val END_BYTES = 22
    const val MAX_COMMENT_BYTES = 65_535
    const val MAX_NAME_BYTES = 512
    const val OVERHEAD_BYTES = 16L * 1024 * 1024
    const val END_DISK = 4
    const val END_DIRECTORY_DISK = 6
    const val END_DISK_COUNT = 8
    const val END_TOTAL_COUNT = 10
    const val END_DIRECTORY_SIZE = 12
    const val END_DIRECTORY_OFFSET = 16
    const val END_COMMENT_SIZE = 20
    const val LOCAL_FLAGS = 6
    const val LOCAL_METHOD = 8
    const val LOCAL_CRC = 14
    const val LOCAL_COMPRESSED_SIZE = 18
    const val LOCAL_EXPANDED_SIZE = 22
    const val LOCAL_NAME_SIZE = 26
    const val LOCAL_EXTRA_SIZE = 28
    const val VERSION_FIELDS_BYTES = 4L
    const val TIMESTAMP_FIELDS_BYTES = 4L
    const val INTERNAL_ATTRIBUTES_BYTES = 2L
    const val UNIX_MODE_SHIFT = 16
    const val UNIX_FILE_TYPE_MASK = 0xf000
    const val UNIX_SYMLINK_TYPE = 0xa000
    const val BYTE_MASK = 0xff
    const val SHORT_MASK = 0xffff
    const val UINT_MASK = 0xffffffffL
    const val BYTE_BITS = 8
    const val WORD_BITS = 16
}

internal data class BackupZipRecord(
    val size: Long,
    val crc: Long,
)

/** Reads the complete central directory so duplicate/aliased names cannot disappear in a ZIP map. */
internal class BackupZipReader(
    private val fileSystem: FileSystem,
    private val limits: BackupArchiveLimits,
) {
    fun readDirectory(archive: Path): Map<String, BackupZipRecord> {
        val end = readEndDirectory(archive)
        val input = Buffer().write(readAt(archive, end.offset, end.size.toInt()))
        val result = linkedMapOf<String, BackupZipRecord>()
        val names = mutableSetOf<String>()
        val ranges = mutableListOf<LongRange>()
        var total = 0L
        repeat(end.count) {
            val entry = readCentralEntry(input)
            require(names.add(entry.name.lowercase())) { "Duplicate ZIP entries" }
            total += entry.expanded
            require(total <= limits.maxTotalBytes) { "Expanded backup is too large" }
            ranges += validateLocalHeader(archive, entry, end.offset)
            if (entry.directory) {
                require(entry.expanded == 0L)
            } else {
                result[entry.name] = BackupZipRecord(entry.expanded, entry.crc)
            }
        }
        require(input.exhausted() && "manifest.json" in result && "backup.json" in result) { "Incomplete ZIP backup" }
        val sorted = ranges.sortedBy { it.first }
        require(sorted.zipWithNext().all { (left, right) -> left.last < right.first }) { "Overlapping ZIP entries" }
        return result
    }

    private fun readEndDirectory(archive: Path): EndDirectory {
        val size = fileSystem.metadata(archive).size ?: error("Could not read backup size")
        require(size in BackupZipFormat.END_BYTES..(limits.maxTotalBytes + BackupZipFormat.OVERHEAD_BYTES)) {
            "Backup archive is too large or invalid"
        }
        val tailSize = minOf(size, (BackupZipFormat.END_BYTES + BackupZipFormat.MAX_COMMENT_BYTES).toLong()).toInt()
        val tail = readAt(archive, size - tailSize, tailSize)
        val end =
            (tail.size - BackupZipFormat.END_BYTES downTo 0).firstOrNull { at ->
                tail.zipIntLe(at) == BackupZipFormat.END_SIGNATURE &&
                    at + BackupZipFormat.END_BYTES + tail.zipShortLe(at + BackupZipFormat.END_COMMENT_SIZE) == tail.size
            } ?: error("Not a ZIP backup")
        require(
            tail.zipShortLe(end + BackupZipFormat.END_DISK) == 0 &&
                tail.zipShortLe(end + BackupZipFormat.END_DIRECTORY_DISK) == 0,
        ) { "Split ZIP backups are not supported" }
        val count = tail.zipShortLe(end + BackupZipFormat.END_TOTAL_COUNT)
        require(count == tail.zipShortLe(end + BackupZipFormat.END_DISK_COUNT) && count in 2..limits.maxEntries) {
            "Invalid backup file count"
        }
        val directorySize = tail.zipUnsignedInt(end + BackupZipFormat.END_DIRECTORY_SIZE)
        val directoryOffset = tail.zipUnsignedInt(end + BackupZipFormat.END_DIRECTORY_OFFSET)
        val endsAtTail = directoryOffset + directorySize == size - tailSize + end
        require(directorySize <= limits.maxManifestBytes * 2L && endsAtTail) {
            "Invalid ZIP directory"
        }
        return EndDirectory(count, directoryOffset, directorySize)
    }

    private fun readCentralEntry(input: Buffer): CentralEntry {
        require(input.size >= BackupZipFormat.CENTRAL_BYTES && input.readIntLe() == BackupZipFormat.CENTRAL_SIGNATURE) {
            "Invalid ZIP entry"
        }
        input.skip(BackupZipFormat.VERSION_FIELDS_BYTES)
        val flags = input.readShortLe().toInt() and BackupZipFormat.SHORT_MASK
        val method = input.readShortLe().toInt() and BackupZipFormat.SHORT_MASK
        input.skip(BackupZipFormat.TIMESTAMP_FIELDS_BYTES)
        val crc = input.readIntLe().toLong() and BackupZipFormat.UINT_MASK
        val compressed = input.readIntLe().toLong() and BackupZipFormat.UINT_MASK
        val expanded = input.readIntLe().toLong() and BackupZipFormat.UINT_MASK
        val nameSize = input.readShortLe().toInt() and BackupZipFormat.SHORT_MASK
        val extraSize = input.readShortLe().toInt() and BackupZipFormat.SHORT_MASK
        val commentSize = input.readShortLe().toInt() and BackupZipFormat.SHORT_MASK
        require(input.readShortLe().toInt() == 0) { "Split ZIP entry" }
        input.skip(BackupZipFormat.INTERNAL_ATTRIBUTES_BYTES)
        val attributes = input.readIntLe()
        require(
            (attributes ushr BackupZipFormat.UNIX_MODE_SHIFT) and BackupZipFormat.UNIX_FILE_TYPE_MASK !=
                BackupZipFormat.UNIX_SYMLINK_TYPE,
        ) { "Symbolic links are not allowed in backups" }
        val offset = input.readIntLe().toLong() and BackupZipFormat.UINT_MASK
        require(nameSize in 1..BackupZipFormat.MAX_NAME_BYTES && input.size >= nameSize + extraSize + commentSize) {
            "Invalid ZIP name"
        }
        val name = input.readByteArray(nameSize.toLong()).decodeToString(throwOnInvalidSequence = true)
        input.skip((extraSize + commentSize).toLong())
        return CentralEntry(name, flags, method, crc, compressed, expanded, offset, nameSize).also(::validateEntry)
    }

    private fun validateEntry(entry: CentralEntry) {
        val readableMethod = entry.method in setOf(BackupZipFormat.STORE, BackupZipFormat.DEFLATE)
        require(entry.flags and BackupZipFormat.ENCRYPTED_FLAG == 0 && readableMethod) {
            "Encrypted or unsupported ZIP entry"
        }
        val metadata = entry.name == "manifest.json" || entry.name == "backup.json"
        require(entry.directory || metadata || validBackupPayloadName(entry.name)) { "Unsafe ZIP path" }
        val sizeConsistent = entry.method != BackupZipFormat.STORE || entry.compressed == entry.expanded
        require(entry.expanded <= limits.maxEntryBytes && sizeConsistent) { "Backup entry is too large" }
        if (entry.name == "manifest.json") {
            require(entry.expanded <= limits.maxManifestBytes) { "Backup manifest is too large" }
        }
        if (entry.name == "backup.json") {
            require(entry.expanded <= limits.maxDocumentBytes) { "Backup metadata is too large" }
        }
    }

    private fun validateLocalHeader(
        archive: Path,
        entry: CentralEntry,
        directoryOffset: Long,
    ): LongRange {
        val local = readAt(archive, entry.offset, BackupZipFormat.LOCAL_BYTES)
        require(
            local.zipIntLe(0) == BackupZipFormat.LOCAL_SIGNATURE &&
                local.zipShortLe(BackupZipFormat.LOCAL_FLAGS) == entry.flags &&
                local.zipShortLe(BackupZipFormat.LOCAL_METHOD) == entry.method,
        ) { "ZIP headers disagree" }
        if (entry.flags and BackupZipFormat.DATA_DESCRIPTOR_FLAG == 0) {
            require(
                local.zipUnsignedInt(BackupZipFormat.LOCAL_CRC) == entry.crc &&
                    local.zipUnsignedInt(BackupZipFormat.LOCAL_COMPRESSED_SIZE) == entry.compressed &&
                    local.zipUnsignedInt(BackupZipFormat.LOCAL_EXPANDED_SIZE) == entry.expanded,
            ) {
                "ZIP entry sizes or checksum disagree"
            }
        }
        val nameSize = local.zipShortLe(BackupZipFormat.LOCAL_NAME_SIZE)
        val extraSize = local.zipShortLe(BackupZipFormat.LOCAL_EXTRA_SIZE)
        val bodyOffset = entry.offset + BackupZipFormat.LOCAL_BYTES + nameSize + extraSize
        require(nameSize == entry.nameSize && bodyOffset + entry.compressed <= directoryOffset) {
            "Invalid ZIP entry bounds"
        }
        val localName =
            readAt(archive, entry.offset + BackupZipFormat.LOCAL_BYTES, nameSize)
                .decodeToString(throwOnInvalidSequence = true)
        require(localName == entry.name) { "ZIP entry names disagree" }
        return entry.offset until (bodyOffset + entry.compressed)
    }

    private fun readAt(
        path: Path,
        offset: Long,
        size: Int,
    ): ByteArray {
        require(offset >= 0 && size >= 0)
        return fileSystem.openReadOnly(path).use { handle ->
            require(offset + size <= handle.size()) { "Truncated ZIP backup" }
            handle.source(offset).buffer().use { it.readByteArray(size.toLong()) }
        }
    }

    private data class EndDirectory(
        val count: Int,
        val offset: Long,
        val size: Long,
    )

    private data class CentralEntry(
        val name: String,
        val flags: Int,
        val method: Int,
        val crc: Long,
        val compressed: Long,
        val expanded: Long,
        val offset: Long,
        val nameSize: Int,
    ) {
        val directory: Boolean get() = name == "media/" || name == "thumbnails/"
    }
}

internal fun validBackupPayloadName(name: String): Boolean =
    (name.startsWith("media/") || name.startsWith("thumbnails/")) &&
        com.orbin.domain.repository
            .validManagedMediaKey(name.substringAfter('/'))

private fun ByteArray.zipShortLe(offset: Int): Int =
    (this[offset].toInt() and BackupZipFormat.BYTE_MASK) or
        ((this[offset + 1].toInt() and BackupZipFormat.BYTE_MASK) shl BackupZipFormat.BYTE_BITS)

private fun ByteArray.zipIntLe(offset: Int): Int =
    zipShortLe(offset) or (zipShortLe(offset + 2) shl BackupZipFormat.WORD_BITS)

private fun ByteArray.zipUnsignedInt(offset: Int): Long = zipIntLe(offset).toLong() and BackupZipFormat.UINT_MASK
