package com.orbin.data.backup

import com.orbin.core.model.BackupDocument
import com.orbin.core.model.MediaLibraryRecord
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import okio.FileSystem
import okio.Path
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class BackupArchiveCodecTest {
    private val fs = FileSystem.SYSTEM
    private val root = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "orbin-backup-codec-${Random.nextLong()}"
    private val record =
        MediaLibraryRecord(
            "item",
            "fourchan",
            fileName = "clip.webm",
            storageKey = "abcd",
            thumbnailStorageKey = "thumb",
        )

    @AfterTest
    fun cleanup() {
        fs.deleteRecursively(root, mustExist = false)
    }

    @Test
    fun zipRoundTripVerifiesMetadataMediaAndThumbnail() =
        runTest {
            val codec = BackupArchiveCodec(fs, StandardTestDispatcher(testScheduler))
            val media = file("media", "payload-body")
            val thumbnail = file("thumbnail", "small-image")
            val destination = root / "backup.zip"
            val document = BackupDocument(exportedAt = "2026-11-15", savedMedia = listOf(record))
            codec.write(destination, document, mapOf("media/abcd" to media, "thumbnails/thumb" to thumbnail))
            val staged = codec.stage(destination, root / "staged")
            assertEquals(document, staged.document)
            assertContentEquals(
                fs.read(media) {
                    readByteArray()
                },
                fs.read(staged.payloads.getValue("media/abcd")) { readByteArray() },
            )
            assertEquals("small-image", fs.read(staged.payloads.getValue("thumbnails/thumb")) { readUtf8() })
            staged.discard()
            assertFalse(fs.exists(root / "staged"))
            assertFalse(fs.exists(root / "backup.zip.part"))
        }

    @Test
    fun damagedPayloadFailsBeforeAnyStagedBackupCanBeUsed() =
        runTest {
            val codec = BackupArchiveCodec(fs, StandardTestDispatcher(testScheduler))
            val destination = root / "backup.zip"
            codec.write(
                destination,
                BackupDocument(savedMedia = listOf(record.copy(thumbnailStorageKey = null))),
                mapOf(
                    "media/abcd" to file("media", "payload-body"),
                ),
            )
            val bytes = fs.read(destination) { readByteArray() }
            val offset = bytes.indexOfBytes("payload-body".encodeToByteArray())
            check(offset >= 0)
            bytes[offset] = (bytes[offset].toInt() xor 1).toByte()
            fs.write(destination) { write(bytes) }
            assertFailsWith<IllegalArgumentException> { codec.stage(destination, root / "staged") }
            assertFalse(fs.exists(root / "staged"))
        }

    @Test
    fun traversalNamesAndLocalCentralHeaderDisagreementAreRejected() =
        runTest {
            val codec = BackupArchiveCodec(fs, StandardTestDispatcher(testScheduler))
            val destination = root / "backup.zip"
            codec.write(
                destination,
                BackupDocument(savedMedia = listOf(record.copy(thumbnailStorageKey = null))),
                mapOf(
                    "media/abcd" to file("media", "payload"),
                ),
            )
            val original = fs.read(destination) { readByteArray() }
            val traversal = original.copyOf()
            val name = "media/abcd".encodeToByteArray()
            var offset = traversal.indexOfBytes(name)
            while (offset >= 0) {
                "media/../x".encodeToByteArray().copyInto(traversal, offset)
                offset = traversal.indexOfBytes(name)
            }
            fs.write(destination) { write(traversal) }
            assertFailsWith<IllegalArgumentException> { codec.stage(destination, root / "traversal") }
            assertFalse(fs.exists(root / "traversal"))
            val disagreement = original.copyOf()
            disagreement[14] = (disagreement[14].toInt() xor 1).toByte()
            fs.write(destination) { write(disagreement) }
            assertFailsWith<IllegalArgumentException> { codec.stage(destination, root / "disagreement") }
        }

    @Test
    fun entryBoundsAndUnindexedMediaAreRejected() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val bounded = BackupArchiveCodec(fs, dispatcher, BackupArchiveLimits(maxEntryBytes = 4))
            val media = file("media", "too-big")
            val document = BackupDocument(savedMedia = listOf(record.copy(thumbnailStorageKey = null)))
            assertFailsWith<IllegalArgumentException> {
                bounded.write(
                    root / "bounded.zip",
                    document,
                    mapOf(
                        "media/abcd" to media,
                    ),
                )
            }
            assertFalse(fs.exists(root / "bounded.zip"))
            assertFailsWith<IllegalArgumentException> {
                BackupArchiveCodec(fs, dispatcher).write(
                    root / "unindexed.zip",
                    BackupDocument(),
                    mapOf(
                        "media/abcd" to media,
                    ),
                )
            }
        }

    @Test
    fun caseAliasedFileNamesCannotOverwriteOneAnother() =
        runTest {
            val codec = BackupArchiveCodec(fs, StandardTestDispatcher(testScheduler))
            val first = record.copy(thumbnailStorageKey = null)
            val second = first.copy(id = "second", storageKey = "ABCD")
            assertFailsWith<IllegalArgumentException> {
                codec.write(
                    root / "alias.zip",
                    BackupDocument(savedMedia = listOf(first, second)),
                    mapOf(
                        "media/abcd" to file("one", "a"),
                        "media/ABCD" to file("two", "b"),
                    ),
                )
            }
        }

    private fun file(
        name: String,
        text: String,
    ): Path =
        (root / name).also {
            fs.createDirectories(root)
            fs.write(it) { writeUtf8(text) }
        }
}

private fun ByteArray.indexOfBytes(needle: ByteArray): Int =
    (0..size - needle.size).firstOrNull { at -> needle.indices.all { this[at + it] == needle[it] } } ?: -1
