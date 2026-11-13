package com.orbin.data.worker

import com.google.common.truth.Truth.assertThat
import com.orbin.data.database.dao.DownloadDao
import com.orbin.data.database.entity.DownloadEntity
import com.orbin.data.repository.DownloadPlatformQueue
import com.orbin.data.repository.DownloadRepositoryImpl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Host CI test: 100 queued downloads finish after 429, 503, and mid-body disconnects,
 * without a second enqueue of the same file or a partial body in the published folder.
 */
class DownloadReliabilityStressTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val server = MockWebServer()
    private lateinit var partialDir: File
    private lateinit var finalDir: File

    @Before
    fun setUp() {
        partialDir = tmp.newFolder("partials")
        finalDir = tmp.newFolder("final")
        server.dispatcher = StressDispatcher()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test(timeout = 60_000)
    fun aHundredDownloadsCompleteAfterDropsAndRateLimitsWithoutDuplicatesOrPartials() =
        runBlocking(Dispatchers.IO) {
            val dao = MemoryDownloadDao()
            val queue = RecordingQueue()
            val repo = DownloadRepositoryImpl(dao, queue, Dispatchers.Unconfined)

            repeat(ITEM_COUNT) { i ->
                val id = repo.enqueue(httpsUrl(i), fileName(i), BOARD, THREAD, TITLE)
                assertThat(id).isGreaterThan(0)
            }
            // A second pass of the same targets must not enqueue a second copy.
            repeat(ITEM_COUNT) { i ->
                val again = repo.enqueue(httpsUrl(i), fileName(i), BOARD, THREAD, TITLE)
                assertThat(again).isEqualTo(dao.findByTarget(httpsUrl(i), fileName(i), RELATIVE_DIR).single().id)
            }
            assertThat(dao.all()).hasSize(ITEM_COUNT)
            assertThat(queue.enqueued).hasSize(ITEM_COUNT)
            assertThat(queue.enqueued.toSet()).hasSize(ITEM_COUNT)

            val fetcher =
                DownloadFetcher(
                    OkHttpClient
                        .Builder()
                        .retryOnConnectionFailure(false)
                        .connectTimeout(2, TimeUnit.SECONDS)
                        .readTimeout(2, TimeUnit.SECONDS)
                        .build(),
                )
            val hostGate = DownloadHostGate()
            val host = checkNotNull(server.url("/").host)

            coroutineScope {
                dao
                    .all()
                    .map { record ->
                        async {
                            runDownload(
                                fetcher = fetcher,
                                hostGate = hostGate,
                                host = host,
                                index = indexOf(record.fileName),
                                partial = File(partialDir, "${record.id}.part"),
                                published = File(finalDir, record.fileName),
                            )
                        }
                    }.awaitAll()
            }

            val published = finalDir.listFiles()?.sortedBy { it.name } ?: emptyList()
            assertThat(published.map { it.name }).doesNotContain(".part")
            assertThat(published.none { it.name.endsWith(".part") || it.name.endsWith(".tmp") }).isTrue()
            assertThat(published).hasSize(ITEM_COUNT)
            published.forEach { file ->
                val index = indexOf(file.name)
                assertThat(file.readText()).isEqualTo(expectedBody(index))
            }
            assertThat(partialDir.listFiles()?.filter { it.exists() && it.length() > 0 }).isEmpty()
            assertThat(dao.all()).hasSize(ITEM_COUNT)
        }

    private suspend fun runDownload(
        fetcher: DownloadFetcher,
        hostGate: DownloadHostGate,
        host: String,
        index: Int,
        partial: File,
        published: File,
    ) {
        var attempt = 0
        while (true) {
            val result =
                hostGate.withPermit(host) {
                    fetcher.fetch(server.url("/clip/$index.webm").toString(), emptyMap(), partial)
                }
            when (result) {
                is DownloadFetchResult.Complete -> {
                    publish(partial, published)
                    return
                }
                is DownloadFetchResult.Retry -> {
                    check(DownloadAttempt.canRetry(attempt)) {
                        "gave up on clip $index after $attempt attempts (${result.httpCode})"
                    }
                    attempt++
                    val waitSeconds = result.retryAfterSeconds ?: 0L
                    if (waitSeconds > 0L) delay(waitSeconds * MILLIS_PER_SECOND)
                }
                is DownloadFetchResult.Fail ->
                    error("permanent failure for clip $index (HTTP ${result.httpCode})")
            }
        }
    }

    private fun publish(
        partial: File,
        published: File,
    ) {
        val staging = File(published.parentFile, published.name + ".tmp")
        partial.copyTo(staging, overwrite = true)
        check(staging.length() == partial.length())
        check(staging.renameTo(published) || (published.delete() && staging.renameTo(published))) {
            "could not publish ${published.name}"
        }
        partial.delete()
    }

    private class StressDispatcher : Dispatcher() {
        private val attempts = ConcurrentHashMap<String, AtomicInteger>()

        override fun dispatch(request: RecordedRequest): MockResponse {
            val path = request.path?.substringBefore('?') ?: return MockResponse().setResponseCode(404)
            val index =
                path.substringAfterLast('/').removeSuffix(".webm").toIntOrNull()
                    ?: return MockResponse().setResponseCode(404)
            val n = attempts.computeIfAbsent(path) { AtomicInteger() }.incrementAndGet()
            val body = expectedBody(index)
            return when {
                index < 20 && n == 1 ->
                    MockResponse().setResponseCode(429).setHeader("Retry-After", "0")
                index in 20 until 40 && n == 1 ->
                    MockResponse().setResponseCode(503).setHeader("Retry-After", "0")
                index in 40 until 60 && n == 1 ->
                    MockResponse()
                        .setResponseCode(200)
                        .setHeader("Content-Type", "video/webm")
                        .setBody(body)
                        .setSocketPolicy(SocketPolicy.DISCONNECT_DURING_RESPONSE_BODY)
                else -> rangedOrFull(request, body)
            }
        }

        private fun rangedOrFull(
            request: RecordedRequest,
            body: String,
        ): MockResponse {
            val start =
                request
                    .getHeader("Range")
                    ?.removePrefix("bytes=")
                    ?.removeSuffix("-")
                    ?.toLongOrNull()
            if (start != null && start > 0L) {
                if (start >= body.length) {
                    return MockResponse()
                        .setResponseCode(416)
                        .setHeader("Content-Range", "bytes */${body.length}")
                }
                val rest = body.substring(start.toInt())
                return MockResponse()
                    .setResponseCode(206)
                    .setHeader("Content-Type", "video/webm")
                    .setHeader("Content-Range", "bytes $start-${body.length - 1}/${body.length}")
                    .setBody(rest)
            }
            return MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "video/webm")
                .setBody(body)
        }
    }
}

private const val ITEM_COUNT = 100
private const val BOARD = "g"
private const val THREAD = 42L
private const val TITLE = "Stress"
private const val RELATIVE_DIR = "g/42 - Stress/"
private const val BODY_PAD = 16 * 1024
private const val MILLIS_PER_SECOND = 1_000L

private fun fileName(index: Int) = "clip-$index.webm"

private fun httpsUrl(index: Int) = "https://i.example/clip/$index.webm"

private fun indexOf(name: String) = name.removePrefix("clip-").removeSuffix(".webm").toInt()

private fun expectedBody(index: Int) = "ORBIN-$index-" + "x".repeat(BODY_PAD)

private class RecordingQueue : DownloadPlatformQueue {
    val enqueued = mutableListOf<Long>()

    override suspend fun enqueue(id: Long) {
        synchronized(enqueued) { enqueued += id }
    }

    override fun writeTextFile(
        fileName: String,
        content: String,
    ): Boolean = false
}

private class MemoryDownloadDao : DownloadDao {
    private val rows = MutableStateFlow<List<DownloadEntity>>(emptyList())

    override fun observeAll(): Flow<List<DownloadEntity>> =
        rows.map { list -> list.sortedByDescending { it.createdAtMillis } }

    override suspend fun all(): List<DownloadEntity> = rows.value

    override suspend fun upsert(entry: DownloadEntity) =
        rows.update { list -> list.filterNot { it.id == entry.id } + entry }

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
