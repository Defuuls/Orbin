package com.orbin.ios

import com.orbin.core.model.BoardId
import com.orbin.core.model.DownloadStatus
import com.orbin.core.model.MediaAttachment
import com.orbin.core.model.MediaType
import com.orbin.core.model.ProviderId
import com.orbin.core.model.ThreadId
import com.orbin.core.model.ThreadKey
import com.orbin.data.database.dao.DownloadDao
import com.orbin.data.database.entity.DownloadEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MediaDownloadsTest {
    private val thread = ThreadKey(ProviderId("site"), BoardId("g"), ThreadId(42))

    private class Saved(
        val bytes: ByteArray,
        val fileName: String,
        val folder: String,
        val target: SaveTarget,
    )

    @Test
    fun aSavedFileIsFetchedStoredAndListedAsSaved() =
        runTest {
            val dao = FakeDownloadDao()
            val saved = mutableListOf<Saved>()
            val downloads =
                MediaDownloads(
                    dao = dao,
                    store = { bytes, name, folder, target -> saved += Saved(bytes, name, folder, target) },
                    fetch = { _, onProgress ->
                        onProgress(3, 3)
                        byteArrayOf(1, 2, 3)
                    },
                    scope = backgroundScope,
                    now = { 1_000 },
                )

            assertTrue(downloads.save(file("cat.gif"), thread, "Cats"))

            val record = downloads.records.first { it.singleOrNull()?.status == DownloadStatus.COMPLETED }.single()
            assertEquals("cat.gif", record.fileName)
            assertEquals("https://i.example/cat.gif", record.url)
            val file = saved.single()
            assertEquals(listOf<Byte>(1, 2, 3), file.bytes.toList())
            assertEquals("g/42 - Cats/", file.folder)
            assertEquals(SaveTarget.PHOTO, file.target)
        }

    @Test
    fun aFailedSaveIsListedAndRetryTriesAgain() =
        runTest {
            var attempts = 0
            val downloads =
                MediaDownloads(
                    dao = FakeDownloadDao(),
                    store = { _, _, _, _ -> },
                    fetch = { _, _ ->
                        attempts++
                        if (attempts == 1) error("offline") else byteArrayOf(0)
                    },
                    scope = backgroundScope,
                )

            downloads.save(file("clip.webm"), thread, null)
            val failed = downloads.records.first { it.singleOrNull()?.status == DownloadStatus.FAILED }.single()

            downloads.retry(failed.id)
            downloads.records.first { it.singleOrNull()?.status == DownloadStatus.COMPLETED }
            assertEquals(2, attempts)
        }

    @Test
    fun whatThePermanentFilterCatchesOrIsNotHttpsIsNeverSaved() =
        runTest {
            val downloads =
                MediaDownloads(FakeDownloadDao(), { _, _, _, _ -> }, { _, _ -> byteArrayOf() }, backgroundScope)

            assertFalse(downloads.save(file("gore.jpg"), thread, null))
            assertFalse(downloads.save(file("cat.jpg"), thread, "bestgore compilation"))
            assertFalse(downloads.save(file("cat.jpg", url = "http://i.example/cat.jpg"), thread, null))
        }

    @Test
    fun aSaveLeftRunningWhenTheAppClosedIsOfferedForRetry() =
        runTest {
            val dao = FakeDownloadDao()
            dao.upsert(DownloadEntity(-5, "https://i.example/a.jpg", "a.jpg", DownloadStatus.RUNNING.name, 0))

            val downloads = MediaDownloads(dao, { _, _, _, _ -> }, { _, _ -> byteArrayOf() }, backgroundScope)

            downloads.records.first { it.singleOrNull()?.status == DownloadStatus.FAILED }
        }

    @Test
    fun photosTakeStillsGifsAndPlayableVideosAndFilesTakeTheRest() {
        assertEquals(SaveTarget.PHOTO, saveTargetOf("a.JPG"))
        assertEquals(SaveTarget.PHOTO, saveTargetOf("a.gif"))
        assertEquals(SaveTarget.VIDEO, saveTargetOf("a.mp4"))
        assertEquals(SaveTarget.FILE, saveTargetOf("a.webm"))
        assertEquals(SaveTarget.FILE, saveTargetOf("noextension"))
    }

    @Test
    fun aRemoteFileNameCannotLeaveItsFolder() {
        assertEquals("passwd.jpg", downloadFileName(file("../etc/passwd.jpg")))
        assertEquals("passwd.jpg", downloadFileName(file("a\\b\\passwd.jpg")))
        assertEquals("_hidden.jpg", downloadFileName(file("..hidden.jpg")))
        assertEquals("a_b.png", downloadFileName(file("a:b.png")))
        assertEquals("picture.png", downloadFileName(file("picture", extension = "png")))
        assertEquals("g/42/", downloadFolder("g", 42, "  "))
        assertEquals("g/42 - a_b/", downloadFolder("g", 42, "a/b"))
    }

    private fun file(
        name: String,
        url: String = "https://i.example/$name",
        extension: String = "",
    ) = MediaAttachment(
        id = "1",
        originalFileName = name,
        extension = extension,
        type = MediaType.IMAGE,
        sourceUrl = url,
        thumbnailUrl = url,
    )
}

/** The downloads table in memory. */
private class FakeDownloadDao : DownloadDao {
    private val rows = MutableStateFlow<List<DownloadEntity>>(emptyList())

    override fun observeAll(): Flow<List<DownloadEntity>> =
        rows.map { list ->
            list.sortedByDescending {
                it.createdAtMillis
            }
        }

    override suspend fun all(): List<DownloadEntity> = rows.value

    override suspend fun upsert(entry: DownloadEntity) =
        rows.update { list ->
            list.filterNot { it.id == entry.id } +
                entry
        }

    override suspend fun updateStatus(
        id: Long,
        status: String,
    ) = rows.update { list -> list.map { if (it.id == id) it.copy(status = status) else it } }

    override suspend fun clear() = rows.update { emptyList() }

    override suspend fun getById(id: Long): DownloadEntity? = rows.value.firstOrNull { it.id == id }

    override suspend fun delete(id: Long) = rows.update { list -> list.filterNot { it.id == id } }
}
