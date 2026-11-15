package com.orbin.data.backup

import com.orbin.core.model.BackupArchiveEntry
import com.orbin.core.model.BackupArchiveManifest
import com.orbin.core.model.BackupDocument
import com.orbin.core.model.PermanentContentFilter
import com.orbin.core.model.decodeBackupDocument
import com.orbin.data.util.defaultFileSystem
import com.orbin.data.util.readBoundedBytes
import com.orbin.domain.repository.validManagedMediaKey
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okio.BufferedSink
import okio.ByteString.Companion.toByteString
import okio.FileSystem
import okio.HashingSource
import okio.Path
import okio.Path.Companion.toPath
import okio.Source
import okio.buffer
import okio.openZip
import okio.use

private const val DEFAULT_ENTRY_BYTES = 512L * 1024 * 1024
private const val DEFAULT_TOTAL_BYTES = 2L * 1024 * 1024 * 1024
private const val DEFAULT_DOCUMENT_BYTES = 20 * 1024 * 1024
private const val DEFAULT_MANIFEST_BYTES = 4 * 1024 * 1024

/** Independent byte/count limits apply before decompression and while streaming actual bytes. */
data class BackupArchiveLimits(
    val maxEntries: Int = 10_000,
    val maxEntryBytes: Long = DEFAULT_ENTRY_BYTES,
    val maxTotalBytes: Long = DEFAULT_TOTAL_BYTES,
    val maxDocumentBytes: Int = DEFAULT_DOCUMENT_BYTES,
    val maxManifestBytes: Int = DEFAULT_MANIFEST_BYTES,
)

/** All payloads have been staged and verified. Nothing in the live app has been mutated. */
class StagedBackup internal constructor(
    val document: BackupDocument,
    val payloads: Map<String, Path>,
    val directory: Path,
    private val fileSystem: FileSystem,
) {
    fun discard() = fileSystem.deleteRecursively(directory, mustExist = false)
}

/** A portable ZIP with a versioned SHA-256 manifest; media never enters a whole-library byte array. */
class BackupArchiveCodec(
    private val fileSystem: FileSystem = defaultFileSystem,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val limits: BackupArchiveLimits = BackupArchiveLimits(),
) {
    /** Writes an atomic STORE ZIP. Readers also accept bounded DEFLATE ZIPs from compatible tools. */
    suspend fun write(
        destination: Path,
        document: BackupDocument,
        payloads: Map<String, Path>,
    ) = withContext(dispatcher) {
        validateMediaReferences(document, payloads.keys)
        val entries = prepareEntries(document, payloads)
        val partial = destination.parent!! / "${destination.name}.part"
        fileSystem.createDirectories(destination.parent!!)
        try {
            fileSystem.sink(partial).buffer().use { writeEntries(it, entries) }
            fileSystem.atomicMove(partial, destination)
        } finally {
            fileSystem.delete(partial, mustExist = false)
        }
    }

    private suspend fun prepareEntries(
        document: BackupDocument,
        payloads: Map<String, Path>,
    ): List<WriteEntry> {
        require(payloads.size + 2 <= limits.maxEntries) { "Too many files in the backup" }
        val documentBytes = json.encodeToString(BackupDocument.serializer(), document).encodeToByteArray()
        require(documentBytes.size <= limits.maxDocumentBytes) { "Backup metadata is too large" }
        val entries = mutableListOf(WriteEntry("backup.json", documentBytes, null, fingerprint(documentBytes)))
        payloads.entries.sortedBy { it.key }.forEach { (name, source) ->
            require(validBackupPayloadName(name)) { "Invalid backup file name" }
            entries += WriteEntry(name, null, source, fingerprint(fileSystem.source(source), limits.maxEntryBytes))
        }
        require(entries.sumOf { it.fingerprint.size } <= limits.maxTotalBytes) { "Backup is too large" }
        val records = entries.map { BackupArchiveEntry(it.name, it.fingerprint.size, it.fingerprint.sha256) }
        val manifest = BackupArchiveManifest(entries = records)
        val bytes = json.encodeToString(BackupArchiveManifest.serializer(), manifest).encodeToByteArray()
        require(bytes.size <= limits.maxManifestBytes) { "Backup manifest is too large" }
        entries += WriteEntry("manifest.json", bytes, null, fingerprint(bytes))
        require(entries.sumOf { it.fingerprint.size } <= limits.maxTotalBytes) { "Backup is too large" }
        require(entries.map { it.name.lowercase() }.toSet().size == entries.size) { "Duplicate backup file names" }
        return entries
    }

    private suspend fun writeEntries(
        output: BufferedSink,
        entries: List<WriteEntry>,
    ) {
        var offset = 0L
        val directory = mutableListOf<Pair<WriteEntry, Long>>()
        for (entry in entries) {
            currentCoroutineContext().ensureActive()
            directory += entry to offset
            offset += writeLocalHeader(output, entry)
            if (entry.bytes != null) {
                output.write(entry.bytes)
            } else {
                val source = fileSystem.source(checkNotNull(entry.source))
                require(fingerprint(source, limits.maxEntryBytes, output) == entry.fingerprint) {
                    "A saved file changed while backing up; try again"
                }
            }
            offset += entry.fingerprint.size
        }
        val directoryOffset = offset
        for ((entry, localOffset) in directory) offset += writeCentralHeader(output, entry, localOffset)
        writeEndHeader(output, entries.size, directoryOffset, offset)
    }

    private fun writeLocalHeader(
        output: BufferedSink,
        entry: WriteEntry,
    ): Long {
        val name = entry.name.encodeToByteArray()
        output
            .writeIntLe(BackupZipFormat.LOCAL_SIGNATURE)
            .writeShortLe(BackupZipFormat.VERSION_20)
            .writeShortLe(BackupZipFormat.UTF8_FLAG)
            .writeShortLe(BackupZipFormat.STORE)
            .writeShortLe(0)
            .writeShortLe(BackupZipFormat.DOS_EPOCH_DAY)
            .writeIntLe(entry.fingerprint.crc.toInt())
            .writeIntLe(entry.fingerprint.size.toInt())
            .writeIntLe(entry.fingerprint.size.toInt())
            .writeShortLe(name.size)
            .writeShortLe(0)
            .write(name)
        return BackupZipFormat.LOCAL_BYTES + name.size.toLong()
    }

    private fun writeCentralHeader(
        output: BufferedSink,
        entry: WriteEntry,
        localOffset: Long,
    ): Long {
        val name = entry.name.encodeToByteArray()
        output
            .writeIntLe(BackupZipFormat.CENTRAL_SIGNATURE)
            .writeShortLe(BackupZipFormat.VERSION_20)
            .writeShortLe(BackupZipFormat.VERSION_20)
            .writeShortLe(BackupZipFormat.UTF8_FLAG)
            .writeShortLe(BackupZipFormat.STORE)
            .writeShortLe(0)
            .writeShortLe(BackupZipFormat.DOS_EPOCH_DAY)
            .writeIntLe(entry.fingerprint.crc.toInt())
            .writeIntLe(entry.fingerprint.size.toInt())
            .writeIntLe(entry.fingerprint.size.toInt())
            .writeShortLe(name.size)
            .writeShortLe(0)
            .writeShortLe(0)
            .writeShortLe(0)
            .writeShortLe(0)
            .writeIntLe(0)
            .writeIntLe(localOffset.toInt())
            .write(name)
        return BackupZipFormat.CENTRAL_BYTES + name.size.toLong()
    }

    private fun writeEndHeader(
        output: BufferedSink,
        count: Int,
        directoryOffset: Long,
        offset: Long,
    ) {
        output
            .writeIntLe(BackupZipFormat.END_SIGNATURE)
            .writeShortLe(0)
            .writeShortLe(0)
            .writeShortLe(count)
            .writeShortLe(count)
            .writeIntLe((offset - directoryOffset).toInt())
            .writeIntLe(directoryOffset.toInt())
            .writeShortLe(0)
    }

    /** Validates every name/header/size/CRC/hash and the document before returning staged paths. */
    suspend fun stage(
        archive: Path,
        directory: Path,
    ): StagedBackup =
        withContext(dispatcher) {
            require(!fileSystem.exists(directory)) { "Backup staging directory already exists" }
            val records = BackupZipReader(fileSystem, limits).readDirectory(archive)
            val zip = fileSystem.openZip(archive)
            var completed = false
            try {
                val expected = readManifest(zip, records)
                fileSystem.createDirectories(directory)
                val staged = stageEntries(zip, expected, records, directory)
                val document = decodeStagedDocument(staged)
                validateMediaReferences(document, staged.keys)
                StagedBackup(document, staged, directory, fileSystem).also { completed = true }
            } finally {
                if (!completed) fileSystem.deleteRecursively(directory, mustExist = false)
                zip.close()
            }
        }

    private fun readManifest(
        zip: FileSystem,
        records: Map<String, BackupZipRecord>,
    ): Map<String, BackupArchiveEntry> {
        val bytes = zip.source("/manifest.json".toPath()).buffer().use { it.readBoundedBytes(limits.maxManifestBytes) }
        val actual = fingerprint(bytes)
        val record = records.getValue("manifest.json")
        require(actual.size == record.size && actual.crc == record.crc) { "Damaged backup manifest" }
        val text = bytes.decodeToString(throwOnInvalidSequence = true)
        val manifest = json.decodeFromString(BackupArchiveManifest.serializer(), text)
        require(manifest.formatVersion == MANIFEST_VERSION) { "This backup format is not supported" }
        val uniquePaths =
            manifest.entries
                .map { it.path }
                .toSet()
                .size == manifest.entries.size
        require(manifest.entries.size <= limits.maxEntries && uniquePaths) {
            "Duplicate backup manifest entries"
        }
        val expected = manifest.entries.associateBy { it.path }
        require(expected.keys == records.keys - "manifest.json") { "Backup file list does not match its manifest" }
        expected.forEach { (name, entry) ->
            require(entry.size == records.getValue(name).size && entry.sha256.matches(SHA256)) {
                "Invalid backup integrity metadata"
            }
        }
        return expected
    }

    private suspend fun stageEntries(
        zip: FileSystem,
        expected: Map<String, BackupArchiveEntry>,
        records: Map<String, BackupZipRecord>,
        directory: Path,
    ): MutableMap<String, Path> {
        val staged = linkedMapOf<String, Path>()
        for ((name, entry) in expected) {
            currentCoroutineContext().ensureActive()
            val destination = directory / name
            fileSystem.createDirectories(destination.parent!!)
            val actual =
                fileSystem.sink(destination).buffer().use { output ->
                    fingerprint(zip.source(("/" + name).toPath()), minOf(entry.size, limits.maxEntryBytes), output)
                }
            val wanted = Fingerprint(entry.size, records.getValue(name).crc, entry.sha256)
            require(actual == wanted) { "Backup integrity check failed" }
            staged[name] = destination
        }
        return staged
    }

    private fun decodeStagedDocument(staged: MutableMap<String, Path>): BackupDocument {
        val documentPath = staged.remove("backup.json") ?: error("Missing backup metadata")
        val contents =
            fileSystem
                .source(documentPath)
                .buffer()
                .use { it.readBoundedBytes(limits.maxDocumentBytes) }
                .decodeToString(throwOnInvalidSequence = true)
        require(contents.length <= MAX_DOCUMENT_CHARS) { "Backup metadata is too large" }
        return json.decodeBackupDocument(contents).also { document ->
            require(document.formatVersion in 1..BackupDocument.CURRENT_FORMAT_VERSION) {
                "This backup was made by a newer Orbin version"
            }
        }
    }

    private suspend fun fingerprint(
        source: Source,
        limit: Long,
        destination: BufferedSink? = null,
    ): Fingerprint {
        val hashing = HashingSource.sha256(source)
        val crc = Crc32()
        var size = 0L
        hashing.buffer().use { input ->
            val bytes = ByteArray(CHUNK_SIZE)
            while (true) {
                currentCoroutineContext().ensureActive()
                val count = input.read(bytes)
                if (count == -1) break
                size += count
                require(size <= limit) { "A saved file is too large to back up" }
                crc.update(bytes, count)
                destination?.write(bytes, 0, count)
            }
        }
        return Fingerprint(size, crc.value, hashing.hash.hex())
    }

    private fun fingerprint(bytes: ByteArray): Fingerprint =
        Fingerprint(
            bytes.size.toLong(),
            Crc32().apply { update(bytes, bytes.size) }.value,
            bytes.toByteString().sha256().hex(),
        )

    private fun validateMediaReferences(
        document: BackupDocument,
        paths: Set<String>,
    ) {
        val wanted = mutableSetOf<String>()
        require(
            document.savedMedia
                .map { it.id }
                .toSet()
                .size == document.savedMedia.size,
        ) {
            "Duplicate saved media records"
        }
        document.savedMedia.forEach { item ->
            val validId = item.id.isNotBlank() && item.id.length <= MAX_MEDIA_ID_LENGTH
            val validIdentity = validId && item.providerId.isNotBlank()
            require(validIdentity && validManagedMediaKey(item.storageKey)) { "Invalid saved media record" }
            val validFile = item.fileName.isNotBlank() && !PermanentContentFilter.matches(item.fileName)
            require(item.byteCount >= 0 && validFile) {
                "Invalid saved media metadata"
            }
            wanted += "media/${item.storageKey}"
            item.thumbnailStorageKey?.let { key ->
                require(validManagedMediaKey(key)) { "Invalid saved thumbnail key" }
                wanted += "thumbnails/$key"
            }
        }
        require(paths == wanted) { "Saved media does not match the backup files" }
    }

    private data class Fingerprint(
        val size: Long,
        val crc: Long,
        val sha256: String,
    )

    private data class WriteEntry(
        val name: String,
        val bytes: ByteArray?,
        val source: Path?,
        val fingerprint: Fingerprint,
    )

    private companion object {
        const val MANIFEST_VERSION = 1
        const val MAX_MEDIA_ID_LENGTH = 512
        const val CHUNK_SIZE = 64 * 1024
        const val MAX_DOCUMENT_CHARS = 5 * 1024 * 1024
        val SHA256 = Regex("^[0-9a-f]{64}$")
        val json =
            Json {
                ignoreUnknownKeys = true
                encodeDefaults = true
            }
    }
}

private class Crc32 {
    private var crc = -1
    val value: Long get() = (crc xor -1).toLong() and BackupZipFormat.UINT_MASK

    fun update(
        bytes: ByteArray,
        count: Int,
    ) {
        for (index in 0 until count) {
            crc = crc xor (bytes[index].toInt() and BackupZipFormat.BYTE_MASK)
            crc = (crc ushr BackupZipFormat.BYTE_BITS) xor CRC_TABLE[crc and BackupZipFormat.BYTE_MASK]
        }
    }
}

private val CRC_TABLE =
    IntArray(256) { index ->
        var value = index
        repeat(8) { value = (value ushr 1) xor (if (value and 1 == 1) 0xedb88320.toInt() else 0) }
        value
    }
