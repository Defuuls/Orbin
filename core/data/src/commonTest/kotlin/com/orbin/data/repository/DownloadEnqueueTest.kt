package com.orbin.data.repository

import com.orbin.core.model.DownloadStatus
import com.orbin.data.database.dao.DownloadDao
import com.orbin.data.database.entity.DownloadEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private const val URL = "https://i.example/clip.webm"
private const val NAME = "clip.webm"
private const val BOARD = "g"
private const val THREAD = 42L
private const val TITLE = "Clips"

class DownloadEnqueueTest {
    @Test
    fun aQueuedOrRunningRowIsReturnedAndAFailedRowIsRequeued() {
        val queued = entry(1, DownloadStatus.QUEUED)
        val running = entry(2, DownloadStatus.RUNNING)
        val failed = entry(3, DownloadStatus.FAILED)
        val completed = entry(4, DownloadStatus.COMPLETED)

        assertEquals(EnqueueDecision.InsertNew, decideEnqueue(emptyList(), completedFilePresent = true))
        assertEquals(EnqueueDecision.ReturnExisting(1), decideEnqueue(listOf(queued), true))
        assertEquals(EnqueueDecision.ReturnExisting(2), decideEnqueue(listOf(running), true))
        assertEquals(EnqueueDecision.Requeue(3), decideEnqueue(listOf(failed), true))
        assertEquals(EnqueueDecision.ReturnExisting(4), decideEnqueue(listOf(completed), completedFilePresent = true))
        assertEquals(EnqueueDecision.Requeue(4), decideEnqueue(listOf(completed), completedFilePresent = false))
    }

    @Test
    fun anActiveRowWinsOverAFailedOrCompletedDuplicate() {
        val failed = entry(1, DownloadStatus.FAILED)
        val completed = entry(2, DownloadStatus.COMPLETED)
        val queued = entry(3, DownloadStatus.QUEUED)

        assertEquals(
            EnqueueDecision.ReturnExisting(3),
            decideEnqueue(listOf(queued, failed, completed), completedFilePresent = true),
        )
        assertEquals(
            EnqueueDecision.ReturnExisting(2),
            decideEnqueue(listOf(failed, completed), completedFilePresent = true),
        )
        assertEquals(
            EnqueueDecision.Requeue(2),
            decideEnqueue(listOf(failed, completed), completedFilePresent = false),
        )
    }

    @Test
    fun enqueueingTheSameTargetTwiceDoesNotInsertASecondRow() =
        runTest {
            val dao = FakeDownloadDao()
            val queue = RecordingQueue()
            val repo = DownloadRepositoryImpl(dao, queue, Dispatchers.Unconfined)

            val first = repo.enqueue(URL, NAME, BOARD, THREAD, TITLE)
            val second = repo.enqueue(URL, NAME, BOARD, THREAD, TITLE)

            assertTrue(first > 0)
            assertEquals(first, second)
            assertEquals(1, dao.all().size)
            assertEquals(listOf(first), queue.enqueued)
        }

    @Test
    fun aFailedDownloadIsRequeuedOnTheSameRow() =
        runTest {
            val dao = FakeDownloadDao()
            val queue = RecordingQueue()
            val repo = DownloadRepositoryImpl(dao, queue, Dispatchers.Unconfined)

            val first = repo.enqueue(URL, NAME, BOARD, THREAD, TITLE)
            dao.updateStatus(first, DownloadStatus.FAILED.name)

            val second = repo.enqueue(URL, NAME, BOARD, THREAD, TITLE)

            assertEquals(first, second)
            assertEquals(DownloadStatus.QUEUED.name, dao.getById(first)?.status)
            assertEquals(listOf(first, first), queue.enqueued)
            assertEquals(1, dao.all().size)
        }

    @Test
    fun aCompletedDownloadIsSkippedWhenTheFileIsStillThereAndRequeuedWhenItIsGone() =
        runTest {
            val dao = FakeDownloadDao()
            val queue = RecordingQueue(fileExists = true)
            val repo = DownloadRepositoryImpl(dao, queue, Dispatchers.Unconfined)

            val first = repo.enqueue(URL, NAME, BOARD, THREAD, TITLE)
            dao.updateStatus(first, DownloadStatus.COMPLETED.name)

            assertEquals(first, repo.enqueue(URL, NAME, BOARD, THREAD, TITLE))
            assertEquals(listOf(first), queue.enqueued)

            queue.fileExists = false
            val again = repo.enqueue(URL, NAME, BOARD, THREAD, TITLE)
            assertEquals(first, again)
            assertEquals(DownloadStatus.QUEUED.name, dao.getById(first)?.status)
            assertEquals(listOf(first, first), queue.enqueued)
            assertEquals(1, dao.all().size)
        }

    @Test
    fun aDifferentFolderIsASeparateDownload() =
        runTest {
            val dao = FakeDownloadDao()
            val queue = RecordingQueue()
            val repo = DownloadRepositoryImpl(dao, queue, Dispatchers.Unconfined)

            val first = repo.enqueue(URL, NAME, BOARD, THREAD, TITLE)
            val second = repo.enqueue(URL, NAME, BOARD, 99L, "Other")

            assertTrue(first != second)
            assertEquals(2, dao.all().size)
            assertEquals(listOf(first, second), queue.enqueued)
        }
}

private fun entry(
    id: Long,
    status: DownloadStatus,
) = DownloadEntity(
    id = id,
    url = URL,
    fileName = NAME,
    status = status.name,
    createdAtMillis = id,
    relativeDir = "g/42 - Clips/",
)

private class RecordingQueue(
    var fileExists: Boolean = true,
) : DownloadPlatformQueue {
    val enqueued = mutableListOf<Long>()

    override suspend fun enqueue(id: Long) {
        enqueued += id
    }

    override fun writeTextFile(
        fileName: String,
        content: String,
    ): Boolean = false

    override fun downloadedFileExists(
        fileName: String,
        relativeDir: String,
    ): Boolean = fileExists
}

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

    override suspend fun findByTarget(
        url: String,
        fileName: String,
        relativeDir: String,
    ): List<DownloadEntity> =
        rows.value
            .filter { it.url == url && it.fileName == fileName && it.relativeDir == relativeDir }
            .sortedByDescending { it.createdAtMillis }

    override suspend fun delete(id: Long) = rows.update { list -> list.filterNot { it.id == id } }
}
