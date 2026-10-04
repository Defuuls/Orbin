package com.orbin.data.repository

import com.orbin.core.model.DownloadOrganization
import com.orbin.core.model.DownloadRecord
import com.orbin.core.model.DownloadStatus
import com.orbin.core.model.PermanentContentFilter
import com.orbin.data.database.dao.DownloadDao
import com.orbin.data.database.entity.DownloadEntity
import com.orbin.domain.repository.DownloadRepository
import com.orbin.network.NetworkConfig
import io.ktor.http.Url
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.time.Clock

interface DownloadPlatformQueue {
    fun enqueue(
        id: Long,
        url: String,
        fileName: String,
        relativeDir: String,
    )

    fun writeTextFile(
        fileName: String,
        content: String,
    ): Boolean
}

class DownloadRepositoryImpl(
    private val dao: DownloadDao,
    private val queue: DownloadPlatformQueue? = null,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : DownloadRepository {
    private var lastId = 0L
    private val idMutex = Mutex()

    private suspend fun nextId(): Long =
        idMutex.withLock {
            val now = Clock.System.now().toEpochMilliseconds()
            lastId = maxOf(now, lastId + 1)
            lastId
        }

    override fun observeDownloads(): Flow<List<DownloadRecord>> =
        dao.observeAll().map { entities ->
            entities.map { it.toDomain() }
        }

    override suspend fun enqueue(
        url: String,
        fileName: String,
        boardId: String?,
        threadId: Long?,
        threadTitle: String?,
    ): Long =
        withContext(ioDispatcher) {
            val uri = runCatching { Url(url) }.getOrNull()
            if (uri?.protocol?.name?.lowercase() !in ALLOWED_SCHEMES) return@withContext SKIPPED_ID
            if (PermanentContentFilter.matchesAny(listOf(fileName, threadTitle))) {
                return@withContext SKIPPED_ID
            }
            val safeName = sanitizeFileName(fileName)
            val relativeDir =
                buildRelativeDir(DownloadOrganization.BY_BOARD_THEN_THREAD, boardId, threadId, threadTitle)

            val id = nextId()

            dao.upsert(
                DownloadEntity(
                    id = id,
                    url = url,
                    fileName = safeName,
                    status = DownloadStatus.QUEUED.name,
                    createdAtMillis = id,
                    relativeDir = relativeDir,
                ),
            )

            queue?.enqueue(id, url, safeName, relativeDir)
            id
        }

    override suspend fun refreshStatuses() = Unit

    override suspend fun clearHistory() = dao.clear()

    override suspend fun retry(id: Long): Long =
        withContext(ioDispatcher) {
            val entity = dao.getById(id) ?: return@withContext SKIPPED_ID
            val uri = runCatching { Url(entity.url) }.getOrNull()
            if (uri?.protocol?.name?.lowercase() !in ALLOWED_SCHEMES) return@withContext SKIPPED_ID

            dao.updateStatus(id, DownloadStatus.QUEUED.name)
            queue?.enqueue(id, entity.url, entity.fileName, entity.relativeDir)
            id
        }

    override suspend fun writeTextFile(
        fileName: String,
        content: String,
    ): Boolean =
        withContext(ioDispatcher) {
            queue?.writeTextFile(fileName, content) ?: false
        }

    private fun DownloadEntity.toDomain(): DownloadRecord =
        DownloadRecord(
            id = id,
            url = url,
            fileName = fileName,
            status = runCatching { DownloadStatus.valueOf(status) }.getOrDefault(DownloadStatus.QUEUED),
            createdAtMillis = createdAtMillis,
            downloadedBytes = 0L,
            totalBytes = null,
        )

    private companion object {
        const val SKIPPED_ID = -1L
        val ALLOWED_SCHEMES = setOf("https")
    }
}

fun downloadRequestHeaders(url: String): Map<String, String> =
    buildMap {
        put("User-Agent", NetworkConfig.DEFAULT_USER_AGENT)
        put("Accept", DOWNLOAD_ACCEPT)
        downloadOriginReferer(url)?.let { put("Referer", it) }
    }

private fun downloadOriginReferer(url: String): String? {
    val uri = runCatching { Url(url) }.getOrNull() ?: return null
    if (!uri.protocol.name.equals("https", ignoreCase = true) || uri.host.isBlank()) return null
    val portSuffix = if (uri.port == HTTPS_PORT || uri.port == DEFAULT_HTTPS_PORT) "" else ":${uri.port}"
    return "https://${uri.host}$portSuffix/"
}

private const val HTTPS_PORT = 443
private const val DEFAULT_HTTPS_PORT = -1
private const val DOWNLOAD_ACCEPT = "image/avif,image/webp,image/*,video/*,audio/*,*/*;q=0.8"
private const val MAX_PATH_SEGMENT_LENGTH = 80
private const val MAX_FILENAME_LENGTH = 200
private val UNSAFE_PATH_CHARS = Regex("""[/\\:*?"<>|]""")

fun sanitizeFileName(raw: String): String {
    val base = raw.substringAfterLast('/').substringAfterLast('\\')
    val cleaned =
        base
            .filterNot { it.isISOControl() }
            .replace(UNSAFE_PATH_CHARS, "_")
            .replace("..", "_")
            .trim(' ', '.')
            .takeLast(MAX_FILENAME_LENGTH)
    return cleaned.ifBlank { "download" }
}

fun sanitizePathSegment(raw: String): String {
    val cleaned =
        raw
            .filterNot { it.isISOControl() }
            .replace(UNSAFE_PATH_CHARS, "_")
            .replace("..", "_")
            .trim(' ', '.')
            .take(MAX_PATH_SEGMENT_LENGTH)
    return cleaned.ifBlank { "misc" }
}

fun buildRelativeDir(
    organization: DownloadOrganization,
    boardId: String?,
    threadId: Long?,
    threadTitle: String?,
): String {
    val boardSegment = boardId?.takeIf { it.isNotBlank() }?.let(::sanitizePathSegment)
    val threadSegment =
        threadId?.let { id ->
            val title = threadTitle?.trim().orEmpty()
            sanitizePathSegment(if (title.isNotBlank()) "$id - $title" else id.toString())
        }
    val segments =
        when (organization) {
            DownloadOrganization.FLAT -> emptyList()
            DownloadOrganization.BY_BOARD -> listOfNotNull(boardSegment)
            DownloadOrganization.BY_BOARD_THEN_THREAD -> listOfNotNull(boardSegment, threadSegment)
            DownloadOrganization.BY_THREAD -> listOfNotNull(threadSegment)
        }
    return if (segments.isEmpty()) "" else segments.joinToString("/", postfix = "/")
}
