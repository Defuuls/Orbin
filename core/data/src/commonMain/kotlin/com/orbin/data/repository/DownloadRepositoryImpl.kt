@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.orbin.data.repository

import android.content.ContentValues
import android.content.Context
import android.os.Environment
import android.provider.MediaStore
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.orbin.core.common.dispatchers.Dispatcher
import com.orbin.core.common.dispatchers.OrbinDispatcher
import com.orbin.core.model.DownloadOrganization
import com.orbin.core.model.DownloadRecord
import com.orbin.core.model.DownloadStatus
import com.orbin.core.model.PermanentContentFilter
import com.orbin.data.database.dao.DownloadDao
import com.orbin.data.database.entity.DownloadEntity
import com.orbin.data.worker.DownloadWorker
import com.orbin.domain.repository.DownloadRepository
import com.orbin.network.NetworkConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.net.URI
import me.tatarka.inject.annotations.Inject
import com.orbin.graph.AppScope

/**
 * Downloads media via WorkManager, saving into the public Downloads/Orbin directory.
 * A lightweight Room table keeps download history for the in-app downloads screen.
 */
@AppScope
class DownloadRepositoryImpl
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val dao: DownloadDao,
        @Dispatcher(OrbinDispatcher.IO) private val ioDispatcher: CoroutineDispatcher,
    ) : DownloadRepository {
        private val workManager: WorkManager
            get() = WorkManager.getInstance(context)

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
                val uri = URI(url)
                if (uri.scheme?.lowercase() !in ALLOWED_SCHEMES) return@withContext SKIPPED_ID
                if (PermanentContentFilter.matchesAny(listOf(fileName, threadTitle))) {
                    return@withContext SKIPPED_ID
                }
                val safeName = sanitizeFileName(fileName)
                val relativeDir =
                    buildRelativeDir(DownloadOrganization.BY_BOARD_THEN_THREAD, boardId, threadId, threadTitle)

                val id = System.currentTimeMillis()

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

                val inputData =
                    Data
                        .Builder()
                        .putLong(DownloadWorker.KEY_ID, id)
                        .putString(DownloadWorker.KEY_URL, url)
                        .putString(DownloadWorker.KEY_FILE_NAME, safeName)
                        .putString(DownloadWorker.KEY_RELATIVE_DIR, relativeDir)
                        .build()

                val workRequest =
                    OneTimeWorkRequestBuilder<DownloadWorker>()
                        .setInputData(inputData)
                        .build()

                workManager.enqueue(workRequest)

                id
            }

        /** Reduce a remote-supplied name to a safe basename: no separators, traversal or controls. */
        private fun sanitizeFileName(raw: String): String {
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

        override suspend fun refreshStatuses() = Unit // WorkManager handles statuses natively

        override suspend fun clearHistory() = dao.clear()

        override suspend fun retry(id: Long): Long =
            withContext(ioDispatcher) {
                val entity = dao.getById(id) ?: return@withContext SKIPPED_ID
                val uri = URI(entity.url)
                if (uri.scheme?.lowercase() !in ALLOWED_SCHEMES) return@withContext SKIPPED_ID

                dao.updateStatus(id, DownloadStatus.QUEUED.name)

                val inputData =
                    Data
                        .Builder()
                        .putLong(DownloadWorker.KEY_ID, id)
                        .putString(DownloadWorker.KEY_URL, entity.url)
                        .putString(DownloadWorker.KEY_FILE_NAME, entity.fileName)
                        .putString(DownloadWorker.KEY_RELATIVE_DIR, entity.relativeDir)
                        .build()

                val workRequest =
                    OneTimeWorkRequestBuilder<DownloadWorker>()
                        .setInputData(inputData)
                        .build()

                workManager.enqueue(workRequest)

                id
            }

        override suspend fun writeTextFile(
            fileName: String,
            content: String,
        ): Boolean =
            withContext(ioDispatcher) {
                writeTextToDefaultDownloads(fileName, content)
            }

        private fun writeTextToDefaultDownloads(
            fileName: String,
            content: String,
        ): Boolean {
            val safeName = sanitizeFileName(fileName)
            val values =
                ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, safeName)
                    put(MediaStore.Downloads.MIME_TYPE, MIME_TEXT_PLAIN)
                    put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/Orbin")
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
            val resolver = context.contentResolver
            val target = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return false
            return runCatching {
                resolver.openOutputStream(target)?.use { output ->
                    output.write(content.toByteArray())
                } ?: error("Unable to open default downloads folder")
                ContentValues()
                    .apply { put(MediaStore.Downloads.IS_PENDING, 0) }
                    .also { resolver.update(target, it, null, null) }
            }.onFailure {
                resolver.delete(target, null, null)
            }.isSuccess
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
            const val MAX_FILENAME_LENGTH = 200
            const val MIME_OCTET_STREAM = "application/octet-stream"
            const val MIME_TEXT_PLAIN = "text/plain"
            const val PROGRESS_POLL_MS = 1_000L
            const val COPY_BUFFER_SIZE = 32 * 1024
            val ALLOWED_SCHEMES = setOf("https")
        }
    }

/**
 * Headers required by imageboard CDNs for direct media requests.
 *
 * DownloadManager does not use Orbin's OkHttp interceptors, so without these it sends a different
 * request from the one that successfully displays the same file in-app. Keep the policy aligned
 * with HeadersInterceptor: Orbin's User-Agent, media Accept, and same-origin Referer.
 */
internal fun downloadRequestHeaders(url: String): Map<String, String> =
    buildMap {
        put("User-Agent", NetworkConfig.DEFAULT_USER_AGENT)
        put("Accept", DOWNLOAD_ACCEPT)
        downloadOriginReferer(url)?.let { put("Referer", it) }
    }

private fun downloadOriginReferer(url: String): String? {
    val uri = runCatching { URI(url) }.getOrNull() ?: return null
    if (!uri.scheme.equals("https", ignoreCase = true) || uri.host.isNullOrBlank()) return null
    return runCatching { URI("https", null, uri.host, uri.port, "/", null, null).toString() }.getOrNull()
}

private const val DOWNLOAD_ACCEPT = "image/avif,image/webp,image/*,video/*,audio/*,*/*;q=0.8"

private const val MAX_PATH_SEGMENT_LENGTH = 80

/**
 * Characters that cannot appear in a path component on the platforms Orbin writes to.
 *
 * Compiled once. Both sanitizers below build a name per downloaded file, so constructing this
 * inline meant recompiling the same pattern for every file in a batch.
 */
private val UNSAFE_PATH_CHARS = Regex("""[/\\:*?"<>|]""")

/** Same cleanup as filename sanitizing, but keeps the front of the name (an id/board is there). */
internal fun sanitizePathSegment(raw: String): String {
    val cleaned =
        raw
            .filterNot { it.isISOControl() }
            .replace(UNSAFE_PATH_CHARS, "_")
            .replace("..", "_")
            .trim(' ', '.')
            .take(MAX_PATH_SEGMENT_LENGTH)
    return cleaned.ifBlank { "misc" }
}

/**
 * Builds the subfolder path (empty when [organization] is [DownloadOrganization.FLAT], or when
 * the needed context wasn't supplied) a download should land in, relative to the downloads root.
 * Always ends with a trailing slash when non-empty.
 */
internal fun buildRelativeDir(
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
