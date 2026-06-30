package com.orbin.data.repository

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import com.orbin.core.common.dispatchers.Dispatcher
import com.orbin.core.common.dispatchers.OrbinDispatcher
import com.orbin.core.model.DownloadRecord
import com.orbin.core.model.DownloadStatus
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
        ): Long =
            withContext(ioDispatcher) {
                val uri = Uri.parse(url)
                // Defence in depth: only ever hand encrypted media URLs to the platform DownloadManager.
                if (uri.scheme?.lowercase() !in ALLOWED_SCHEMES) return@withContext SKIPPED_ID
                // The file name comes from the remote post; sanitise it so it can never escape the
                // Orbin downloads folder (path traversal) or carry separators/control characters.
                val safeName = sanitizeFileName(fileName)
                val customFolderUri = settingsRepository.settings.first().downloadFolderUri
                if (customFolderUri.isNotBlank()) {
                    return@withContext downloadToFolder(uri, safeName, customFolderUri)
                }

                val request =
                    DownloadManager
                        .Request(uri)
                        .setTitle(safeName)
                        .setDescription("Orbin download")
                        .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                        .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "Orbin/$safeName")
                        .setAllowedOverMetered(true)
                        .setAllowedOverRoaming(true)

                val id = downloadManager.enqueue(request)
                dao.upsert(
                    DownloadEntity(
                        id = id,
                        url = url,
                        fileName = safeName,
                        status = DownloadStatus.QUEUED.name,
                        createdAtMillis = System.currentTimeMillis(),
                    ),
                )
                id
            }

        private suspend fun downloadToFolder(
            uri: Uri,
            safeName: String,
            folderUri: String,
        ): Long {
            val id = -System.currentTimeMillis()
            dao.upsert(
                DownloadEntity(
                    id = id,
                    url = uri.toString(),
                    fileName = safeName,
                    status = DownloadStatus.RUNNING.name,
                    createdAtMillis = System.currentTimeMillis(),
                ),
            )

            val target =
                DocumentsContract.createDocument(
                    context.contentResolver,
                    folderUri.toParentDocumentUri(),
                    MIME_OCTET_STREAM,
                    safeName,
                ) ?: return id.also { dao.updateStatus(id, DownloadStatus.FAILED.name) }

            runCatching {
                okHttpClient
                    .newCall(Request.Builder().url(uri.toString()).build())
                    .execute()
                    .use { response ->
                        if (!response.isSuccessful) error("Download failed with HTTP ${response.code}")
                        val body = response.body ?: error("Download body was empty")
                        context.contentResolver.openOutputStream(target, WRITE_MODE)?.use { output ->
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
                    .replace(Regex("""[/\\:*?"<>|]"""), "_")
                    .replace("..", "_")
                    .trim(' ', '.')
                    .takeLast(MAX_FILENAME_LENGTH)
            return cleaned.ifBlank { "download" }
        }

        override suspend fun refreshStatuses() =
            withContext(ioDispatcher) {
                dao.all().forEach { entity ->
                    if (entity.id < 0L) return@forEach
                    val status = queryStatus(entity.id)
                    if (status.name != entity.status) dao.updateStatus(entity.id, status.name)
                }
            }

        override suspend fun clearHistory() = dao.clear()

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
            const val WRITE_MODE = "w"
            val ALLOWED_SCHEMES = setOf("https")
        }
    }
