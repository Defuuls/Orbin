package com.orbin.data.repository

import android.app.DownloadManager
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import com.orbin.core.common.dispatchers.Dispatcher
import com.orbin.core.common.dispatchers.OrbinDispatcher
import com.orbin.core.model.DownloadOrganization
import com.orbin.core.model.DownloadRecord
import com.orbin.core.model.DownloadStatus
import com.orbin.core.model.PermanentContentFilter
import com.orbin.data.database.dao.DownloadDao
import com.orbin.data.database.entity.DownloadEntity
import com.orbin.domain.repository.DownloadRepository
import com.orbin.domain.repository.SettingsRepository
import com.orbin.network.di.BaseOkHttp
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Downloads media via the platform [DownloadManager], which provides notifications, resume and
 * retry natively, saving into the public Downloads/Orbin directory. A lightweight Room table keeps
 * download history for the in-app downloads screen; statuses are refreshed on demand from the
 * platform manager.
 */
@Singleton
class DownloadRepositoryImpl
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val dao: DownloadDao,
        private val settingsRepository: SettingsRepository,
        @BaseOkHttp private val okHttpClient: OkHttpClient,
        @Dispatcher(OrbinDispatcher.IO) private val ioDispatcher: CoroutineDispatcher,
    ) : DownloadRepository {
        private val downloadManager: DownloadManager
            get() = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

        override fun observeDownloads(): Flow<List<DownloadRecord>> =
            dao.observeAll().map { list -> list.map { it.toDomain() } }

        override suspend fun enqueue(
            url: String,
            fileName: String,
            boardId: String?,
            threadId: Long?,
            threadTitle: String?,
        ): Long =
            withContext(ioDispatcher) {
                val uri = Uri.parse(url)
                // Defence in depth: only ever hand encrypted media URLs to the platform DownloadManager.
                if (uri.scheme?.lowercase() !in ALLOWED_SCHEMES) return@withContext SKIPPED_ID
                // Nothing the permanent filter catches is written to the user's storage, whether it
                // was reached one file at a time or through a bulk "download all media".
                if (PermanentContentFilter.matchesAny(listOf(fileName, threadTitle))) {
                    return@withContext SKIPPED_ID
                }
                // The file name comes from the remote post; sanitise it so it can never escape the
                // Orbin downloads folder (path traversal) or carry separators/control characters.
                val safeName = sanitizeFileName(fileName)
                val settings = settingsRepository.settings.first()
                val relativeDir = buildRelativeDir(settings.downloadOrganization, boardId, threadId, threadTitle)
                val customFolderUri = settings.downloadFolderUri
                if (customFolderUri.isNotBlank()) {
                    return@withContext downloadToFolder(uri, safeName, customFolderUri, relativeDir)
                }

                val request =
                    DownloadManager
                        .Request(uri)
                        .setTitle(safeName)
                        .setDescription("Orbin download")
                        .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                        .setDestinationInExternalPublicDir(
                            Environment.DIRECTORY_DOWNLOADS,
                            "Orbin/$relativeDir$safeName",
                        ).setAllowedOverMetered(true)
                        .setAllowedOverRoaming(true)

                val id = downloadManager.enqueue(request)
                dao.upsert(
                    DownloadEntity(
                        id = id,
                        url = url,
                        fileName = safeName,
                        status = DownloadStatus.QUEUED.name,
                        createdAtMillis = System.currentTimeMillis(),
                        relativeDir = relativeDir,
                    ),
                )
                id
            }

        private suspend fun downloadToFolder(
            uri: Uri,
            safeName: String,
            folderUri: String,
            relativeDir: String,
        ): Long {
            val id = -System.currentTimeMillis()
            dao.upsert(
                DownloadEntity(
                    id = id,
                    url = uri.toString(),
                    fileName = safeName,
                    status = DownloadStatus.RUNNING.name,
                    createdAtMillis = System.currentTimeMillis(),
                    relativeDir = relativeDir,
                ),
            )

            val parentDirUri =
                resolveTargetDirectory(folderUri.toParentDocumentUri(), relativeDir)
                    ?: return id.also { dao.updateStatus(id, DownloadStatus.FAILED.name) }
            val target =
                DocumentsContract.createDocument(
                    context.contentResolver,
                    parentDirUri,
                    MIME_OCTET_STREAM,
                    safeName,
                ) ?: return id.also { dao.updateStatus(id, DownloadStatus.FAILED.name) }

            runCatching {
                okHttpClient
                    .newCall(Request.Builder().url(uri.toString()).build())
                    .execute()
                    .use { response ->
                        if (!response.isSuccessful) error("Download failed with HTTP ${response.code}")
                        val body = response.body
                        context.contentResolver.openOutputStream(target)?.use { output ->
                            body.byteStream().use { input -> input.copyTo(output) }
                        } ?: error("Unable to open selected folder")
                    }
            }.onSuccess {
                dao.updateStatus(id, DownloadStatus.COMPLETED.name)
            }.onFailure {
                dao.updateStatus(id, DownloadStatus.FAILED.name)
            }
            return id
        }

        /**
         * Walks [relativeDir]'s segments under [rootUri], creating each subdirectory (via SAF) the
         * first time it's needed and reusing it on every later download into the same board/thread.
         * Returns null instead of falling back to an ancestor if any requested directory cannot be
         * resolved, so organization failures can never silently flatten a download into the root.
         */
        private fun resolveTargetDirectory(
            rootUri: Uri,
            relativeDir: String,
        ): Uri? {
            if (relativeDir.isBlank()) return rootUri
            var current = rootUri
            for (segment in relativeDir.trim('/').split('/')) {
                current = findOrCreateDirectory(current, segment) ?: return null
            }
            return current
        }

        private fun findOrCreateDirectory(
            parentUri: Uri,
            name: String,
        ): Uri? {
            val existingId = findChildDirectoryId(parentUri, name)
            if (existingId != null) return DocumentsContract.buildDocumentUriUsingTree(parentUri, existingId)
            return DocumentsContract.createDocument(
                context.contentResolver,
                parentUri,
                DocumentsContract.Document.MIME_TYPE_DIR,
                name,
            )
        }

        private fun findChildDirectoryId(
            parentUri: Uri,
            name: String,
        ): String? {
            val childrenUri =
                DocumentsContract.buildChildDocumentsUriUsingTree(parentUri, DocumentsContract.getDocumentId(parentUri))
            val projection =
                arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_MIME_TYPE,
                )
            return runCatching {
                context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
                    cursor.firstMatchingDirectoryId(name)
                }
            }.getOrNull()
        }

        private fun Cursor.firstMatchingDirectoryId(name: String): String? {
            while (moveToNext()) {
                val mime = getString(2)
                val displayName = getString(1)
                if (mime == DocumentsContract.Document.MIME_TYPE_DIR && displayName == name) return getString(0)
            }
            return null
        }

        private fun String.toParentDocumentUri(): Uri {
            val treeUri = Uri.parse(this)
            return DocumentsContract.buildDocumentUriUsingTree(treeUri, DocumentsContract.getTreeDocumentId(treeUri))
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

        override suspend fun refreshStatuses() =
            withContext(ioDispatcher) {
                dao.all().forEach { entity ->
                    val status = queryStatus(entity.id)
                    if (status.name != entity.status) dao.updateStatus(entity.id, status.name)
                }
            }

        override suspend fun clearHistory() = dao.clear()

        override suspend fun retry(id: Long): Long =
            withContext(ioDispatcher) {
                val entity = dao.getById(id) ?: return@withContext SKIPPED_ID
                val customFolderUri = settingsRepository.settings.first().downloadFolderUri
                val uri = Uri.parse(entity.url)
                if (uri.scheme?.lowercase() !in ALLOWED_SCHEMES) return@withContext SKIPPED_ID

                dao.updateStatus(id, DownloadStatus.QUEUED.name)
                if (customFolderUri.isNotBlank()) {
                    return@withContext downloadToFolder(uri, entity.fileName, customFolderUri, entity.relativeDir)
                }

                val request =
                    DownloadManager
                        .Request(uri)
                        .setTitle(entity.fileName)
                        .setDescription("Orbin download")
                        .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                        .setDestinationInExternalPublicDir(
                            Environment.DIRECTORY_DOWNLOADS,
                            "Orbin/${entity.relativeDir}${entity.fileName}",
                        ).setAllowedOverMetered(true)
                        .setAllowedOverRoaming(true)

                val newId = downloadManager.enqueue(request)
                if (newId != id) {
                    dao.delete(id)
                    dao.upsert(
                        DownloadEntity(
                            id = newId,
                            url = entity.url,
                            fileName = entity.fileName,
                            status = DownloadStatus.QUEUED.name,
                            createdAtMillis = entity.createdAtMillis,
                            relativeDir = entity.relativeDir,
                        ),
                    )
                }
                newId
            }

        override suspend fun writeTextFile(
            fileName: String,
            content: String,
        ): Boolean =
            withContext(ioDispatcher) {
                val folderUri = settingsRepository.settings.first().downloadFolderUri
                if (folderUri.isBlank()) {
                    return@withContext writeTextToDefaultDownloads(fileName, content)
                }
                val target =
                    DocumentsContract.createDocument(
                        context.contentResolver,
                        folderUri.toParentDocumentUri(),
                        MIME_TEXT_PLAIN,
                        sanitizeFileName(fileName),
                    ) ?: return@withContext false
                runCatching {
                    context.contentResolver.openOutputStream(target)?.use { output ->
                        output.write(content.toByteArray())
                    } ?: error("Unable to open selected folder")
                }.isSuccess
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

        private fun queryStatus(id: Long): DownloadStatus {
            downloadManager.query(DownloadManager.Query().setFilterById(id)).use { cursor ->
                if (!cursor.moveToFirst()) return DownloadStatus.FAILED
                val statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                return when (cursor.getInt(statusIndex)) {
                    DownloadManager.STATUS_SUCCESSFUL -> DownloadStatus.COMPLETED
                    DownloadManager.STATUS_RUNNING -> DownloadStatus.RUNNING
                    DownloadManager.STATUS_PAUSED, DownloadManager.STATUS_PENDING -> DownloadStatus.QUEUED
                    else -> DownloadStatus.FAILED
                }
            }
        }

        private fun DownloadEntity.toDomain(): DownloadRecord =
            DownloadRecord(
                id = id,
                url = url,
                fileName = fileName,
                status = runCatching { DownloadStatus.valueOf(status) }.getOrDefault(DownloadStatus.QUEUED),
                createdAtMillis = createdAtMillis,
            )

        private companion object {
            const val SKIPPED_ID = -1L
            const val MAX_FILENAME_LENGTH = 200
            const val MIME_OCTET_STREAM = "application/octet-stream"
            const val MIME_TEXT_PLAIN = "text/plain"
            val ALLOWED_SCHEMES = setOf("https")
        }
    }

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
