package com.orbin.data.backup

import com.orbin.core.model.BackupDocument
import com.orbin.core.model.MediaLibraryRecord
import com.orbin.domain.repository.InMemoryMediaLibraryRepository
import com.orbin.domain.repository.ManagedMediaFiles
import com.orbin.domain.repository.MediaLibraryRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class PortableBackupArchiveTest {
    private val fs = FileSystem.SYSTEM
    private val root = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "orbin-portable-backup-${Random.nextLong()}"
    private val record =
        MediaLibraryRecord(
            "media-id",
            "fourchan",
            fileName = "clip.webm",
            storageKey = "original",
            thumbnailStorageKey = "thumb",
        )

    @AfterTest
    fun cleanup() {
        fs.deleteRecursively(root, mustExist = false)
    }

    @Test
    fun exportsOnlyOwnedBytesAndRestoresFreshKeysWithoutOverwritingExistingIds() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val library = InMemoryMediaLibraryRepository()
            library.put(record)
            library.put(record.copy(id = "missing", storageKey = "missing", thumbnailStorageKey = null))
            val files =
                FakeFiles().apply {
                    values["original"] = "video"
                    values["thumb"] = "preview"
                }
            val archive = PortableBackupArchive(library, files, root / "work", fs, dispatcher)
            val destination = root / "backup.zip"
            archive.export(destination, BackupDocument())
            val restoredLibrary = InMemoryMediaLibraryRepository()
            val restoredFiles = FakeFiles()
            var metadata: BackupDocument? = null
            val result =
                PortableBackupArchive(restoredLibrary, restoredFiles, root / "restore", fs, dispatcher).restore(
                    destination,
                    { it == "fourchan" },
                    {
                        metadata = it
                        "restored"
                    },
                )
            assertEquals("restored", result.metadata)
            assertEquals(1, result.mediaFiles)
            assertTrue(metadata!!.savedMedia.isEmpty())
            val restored = restoredLibrary.observeAll().first().single()
            assertTrue(restored.storageKey != record.storageKey)
            assertEquals("video", restoredFiles.values[restored.storageKey])
            assertEquals("preview", restoredFiles.values[restored.thumbnailStorageKey])
            // Importing a second time keeps the original destination record and its keys/bytes intact.
            val second =
                PortableBackupArchive(
                    restoredLibrary,
                    restoredFiles,
                    root / "again",
                    fs,
                    dispatcher,
                ).restore(destination, {
                    true
                }, { Unit })
            assertEquals(0, second.mediaFiles)
            assertEquals(restored, restoredLibrary.get(record.id))
            assertEquals(2, restoredFiles.values.size)
        }

    @Test
    fun legacyJsonStillRestoresMetadataButNeverFabricatesSavedMediaFiles() =
        runTest {
            val path =
                file(
                    "old.json",
                    Json.encodeToString(BackupDocument.serializer(), BackupDocument(savedMedia = listOf(record))),
                )
            val library = InMemoryMediaLibraryRepository()
            val files = FakeFiles()
            val archive =
                PortableBackupArchive(library, files, root / "work", fs, StandardTestDispatcher(testScheduler))
            val result =
                archive.restore(path, { true }, { document ->
                    assertTrue(document.savedMedia.isEmpty())
                    "legacy"
                })
            assertEquals("legacy", result.metadata)
            assertEquals(0, result.mediaFiles)
            assertTrue(library.observeAll().first().isEmpty())
            assertTrue(files.values.isEmpty())
        }

    @Test
    fun cancellationAndMetadataFailureRollbackOnlyTheNewFiles() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val sourceLibrary = InMemoryMediaLibraryRepository().apply { put(record) }
            val sourceFiles =
                FakeFiles().apply {
                    values["original"] = "video"
                    values["thumb"] = "preview"
                }
            val destination = root / "backup.zip"
            PortableBackupArchive(
                sourceLibrary,
                sourceFiles,
                root / "export",
                fs,
                dispatcher,
            ).export(destination, BackupDocument())
            val files = FakeFiles().apply { values["preexisting"] = "keep" }
            val library = InMemoryMediaLibraryRepository()
            val archive = PortableBackupArchive(library, files, root / "restore", fs, dispatcher)
            assertFailsWith<CancellationException> {
                archive.restore(destination, { true }, { throw CancellationException("locked") })
            }
            assertEquals(mapOf("preexisting" to "keep"), files.values)
            assertTrue(library.observeAll().first().isEmpty())
            assertFailsWith<IllegalArgumentException> {
                archive.restore(destination, { true }, { throw IllegalArgumentException("invalid metadata") })
            }
            assertEquals(mapOf("preexisting" to "keep"), files.values)
            assertTrue(library.observeAll().first().isEmpty())
        }

    @Test
    fun failedAuthorizationCannotMutateMetadataOrManagedFiles() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val destination = root / "backup.zip"
            val codec = BackupArchiveCodec(fs, dispatcher)
            codec.write(destination, BackupDocument(), emptyMap())
            val files = FakeFiles()
            var callbacks = 0
            val archive =
                PortableBackupArchive(InMemoryMediaLibraryRepository(), files, root / "restore", fs, dispatcher)
            assertFailsWith<IllegalStateException> {
                archive.restore(
                    destination,
                    { true },
                    { callbacks++ },
                    { error("locked") },
                )
            }
            assertEquals(0, callbacks)
            assertTrue(files.values.isEmpty())
        }

    @Test
    fun concurrentlyAddedIdentityKeepsItsBytesAndCleansSkippedImports() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val sourceLibrary = InMemoryMediaLibraryRepository().apply { put(record) }
            val sourceFiles =
                FakeFiles().apply {
                    values["original"] = "video"
                    values["thumb"] = "preview"
                }
            val destination = root / "backup.zip"
            PortableBackupArchive(
                sourceLibrary,
                sourceFiles,
                root / "export",
                fs,
                dispatcher,
            ).export(destination, BackupDocument())
            val destinationLibrary = InMemoryMediaLibraryRepository()
            val files = FakeFiles().apply { values["sync-file"] = "cloud copy" }
            val concurrent =
                object : MediaLibraryRepository by destinationLibrary {
                    override suspend fun addAllIfAbsent(records: List<MediaLibraryRecord>): List<MediaLibraryRecord> {
                        destinationLibrary.put(record.copy(storageKey = "sync-file", thumbnailStorageKey = null))
                        return destinationLibrary.addAllIfAbsent(records)
                    }
                }
            val result =
                PortableBackupArchive(concurrent, files, root / "restore", fs, dispatcher).restore(destination, {
                    true
                }, { Unit })
            assertEquals(0, result.mediaFiles)
            assertEquals(mapOf("sync-file" to "cloud copy"), files.values)
            assertEquals("sync-file", destinationLibrary.get(record.id)?.storageKey)
        }

    private fun file(
        name: String,
        text: String,
    ): Path =
        (root / name).also {
            fs.createDirectories(root)
            fs.write(it) { writeUtf8(text) }
        }

    private inner class FakeFiles : ManagedMediaFiles {
        val values = linkedMapOf<String, String>()

        override suspend fun contains(storageKey: String) = storageKey in values

        override suspend fun size(storageKey: String) = values[storageKey]?.encodeToByteArray()?.size?.toLong()

        override suspend fun importFile(
            storageKey: String,
            sourcePath: String,
        ) {
            values[storageKey] =
                fs.read(sourcePath.toPath()) { readUtf8() }
        }

        override suspend fun exportFile(
            storageKey: String,
            destinationPath: String,
        ): Boolean {
            val value = values[storageKey] ?: return false
            val path = destinationPath.toPath()
            fs.createDirectories(path.parent!!)
            fs.write(path) { writeUtf8(value) }
            return true
        }

        override suspend fun remove(storageKey: String) {
            values.remove(storageKey)
        }
    }
}
