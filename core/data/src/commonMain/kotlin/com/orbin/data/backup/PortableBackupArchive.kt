package com.orbin.data.backup

import com.orbin.core.model.BackupDocument
import com.orbin.core.model.MediaLibraryRecord
import com.orbin.core.model.decodeBackupDocument
import com.orbin.data.util.defaultFileSystem
import com.orbin.data.util.readBoundedBytes
import com.orbin.domain.repository.ManagedMediaFiles
import com.orbin.domain.repository.MediaLibraryRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okio.ByteString.Companion.encodeUtf8
import okio.FileSystem
import okio.Path
import okio.buffer
import okio.use
import kotlin.random.Random
import kotlin.time.Clock

/** Shared orchestration: verified archive bytes first, live data only after complete validation. */
class PortableBackupArchive(
    private val library: MediaLibraryRepository,
    private val files: ManagedMediaFiles,
    private val temporaryRoot: Path,
    private val fileSystem: FileSystem = defaultFileSystem,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val codec: BackupArchiveCodec = BackupArchiveCodec(fileSystem, dispatcher),
) {
    /** Only records whose actual saved files were exported are included in the ZIP. */
    suspend fun export(
        destination: Path,
        document: BackupDocument,
        authorize: suspend () -> Unit = {},
    ) = withContext(dispatcher) {
        val staging = newWorkDirectory()
        try {
            val payloads = linkedMapOf<String, Path>()
            val records = mutableListOf<MediaLibraryRecord>()
            for (record in library.observeAll().first()) {
                currentCoroutineContext().ensureActive()
                authorize()
                val media =
                    staging / (
                        "media-" +
                            record.id
                                .encodeUtf8()
                                .sha256()
                                .hex()
                    )
                if (!files.exportFile(record.storageKey, media.toString())) continue
                val key = "media/${record.storageKey}"
                payloads[key] = media
                var thumbnailKey: String? = null
                record.thumbnailStorageKey?.let { thumbnail ->
                    val path =
                        staging / (
                            "thumbnail-" +
                                record.id
                                    .encodeUtf8()
                                    .sha256()
                                    .hex()
                        )
                    if (files.exportFile(thumbnail, path.toString())) {
                        payloads["thumbnails/$thumbnail"] = path
                        thumbnailKey = thumbnail
                    }
                }
                records +=
                    record.copy(
                        byteCount = fileSystem.metadata(media).size ?: 0L,
                        thumbnailStorageKey = thumbnailKey,
                    )
            }
            authorize()
            codec.write(destination, document.copy(savedMedia = records), payloads)
        } finally {
            fileSystem.deleteRecursively(staging, mustExist = false)
        }
    }

    /** Legacy JSON remains readable; it cannot invent saved media without owned payload bytes. */
    suspend fun <T> restore(
        source: Path,
        acceptsProvider: (String) -> Boolean,
        restoreMetadata: suspend (BackupDocument) -> T,
        authorize: suspend () -> Unit = {},
    ): ArchiveRestoreResult<T> =
        withContext(dispatcher) {
            authorize()
            val isZip =
                fileSystem.source(source).buffer().use {
                    it.request(ZIP_SIGNATURE_BYTES) && it.readIntLe() == ZIP_SIGNATURE
                }
            if (!isZip) {
                val text =
                    fileSystem
                        .source(source)
                        .buffer()
                        .use { it.readBoundedBytes(MAX_LEGACY_BYTES) }
                        .decodeToString(throwOnInvalidSequence = true)
                require(text.length <= MAX_LEGACY_CHARS) { "Backup metadata is too large" }
                val document = json.decodeBackupDocument(text)
                require(
                    document.formatVersion in 1..BackupDocument.CURRENT_FORMAT_VERSION,
                ) { "Unsupported backup version" }
                authorize()
                return@withContext ArchiveRestoreResult(restoreMetadata(document.copy(savedMedia = emptyList())), 0)
            }
            val staging = temporaryRoot / uniqueName()
            val backup = codec.stage(source, staging)
            val importedKeys = mutableListOf<String>()
            val committedKeys = mutableSetOf<String>()
            var completed = false
            try {
                val addedRecords = importRecords(backup, acceptsProvider, authorize, importedKeys)
                authorize()
                val metadata = restoreMetadata(backup.document.copy(savedMedia = emptyList()))
                authorize()
                // Once the short atomic metadata insert commits, cancellation must not remove its bytes.
                val committed =
                    withContext(NonCancellable) {
                        library.addAllIfAbsent(addedRecords).also { records ->
                            records.forEach { record ->
                                committedKeys += record.storageKey
                                record.thumbnailStorageKey?.let { committedKeys += it }
                            }
                        }
                    }
                // A simultaneous sync may have added the same ID after the initial snapshot.
                withContext(NonCancellable) {
                    importedKeys.filter { it !in committedKeys }.forEach { key ->
                        runCatching { files.remove(key) }
                    }
                }
                ArchiveRestoreResult(metadata, committed.size).also { completed = true }
            } finally {
                if (!completed) {
                    // All failures, including cancellation, roll back only this operation's uncommitted bytes.
                    withContext(NonCancellable) {
                        for (key in importedKeys.filter { it !in committedKeys }) runCatching { files.remove(key) }
                    }
                }
                runCatching { backup.discard() }
            }
        }

    private suspend fun importRecords(
        backup: StagedBackup,
        acceptsProvider: (String) -> Boolean,
        authorize: suspend () -> Unit,
        importedKeys: MutableList<String>,
    ): List<MediaLibraryRecord> {
        val addedRecords = mutableListOf<MediaLibraryRecord>()
        val originals = library.observeAll().first().associateBy { it.id }
        for (record in backup.document.savedMedia) {
            currentCoroutineContext().ensureActive()
            authorize()
            if (!acceptsProvider(record.providerId) || record.id in originals) continue
            val storageKey = freshKey()
            files.importFile(storageKey, backup.payloads.getValue("media/${record.storageKey}").toString())
            importedKeys += storageKey
            val thumbnailKey =
                record.thumbnailStorageKey?.let { old ->
                    authorize()
                    val key = freshKey()
                    files.importFile(key, backup.payloads.getValue("thumbnails/$old").toString())
                    importedKeys += key
                    key
                }
            addedRecords +=
                record.copy(
                    storageKey = storageKey,
                    thumbnailStorageKey = thumbnailKey,
                    byteCount = files.size(storageKey) ?: record.byteCount,
                )
        }
        return addedRecords
    }

    private suspend fun freshKey(): String {
        var key: String
        do {
            key = "restored-${uniqueName().encodeUtf8().sha256().hex()}"
        } while (files.contains(key))
        return key
    }

    private fun newWorkDirectory(): Path = (temporaryRoot / uniqueName()).also { fileSystem.createDirectories(it) }

    private companion object {
        val json = Json { ignoreUnknownKeys = true }
        const val ZIP_SIGNATURE_BYTES = 4L
        const val RANDOM_NAME_RADIX = 16
        const val ZIP_SIGNATURE = 0x04034b50
        const val MAX_LEGACY_BYTES = 20 * 1024 * 1024
        const val MAX_LEGACY_CHARS = 5 * 1024 * 1024

        fun uniqueName(): String =
            "backup-${Clock.System.now().toEpochMilliseconds()}-${Random.nextLong().toString(RANDOM_NAME_RADIX)}"
    }
}

data class ArchiveRestoreResult<T>(
    val metadata: T,
    val mediaFiles: Int,
)
