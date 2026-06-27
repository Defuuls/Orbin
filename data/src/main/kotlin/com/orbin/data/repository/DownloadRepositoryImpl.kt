package com.orbin.data.repository

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import com.orbin.core.common.dispatchers.Dispatcher
import com.orbin.core.common.dispatchers.OrbinDispatcher
import com.orbin.core.model.DownloadRecord
import com.orbin.core.model.DownloadStatus
import com.orbin.data.database.dao.DownloadDao
import com.orbin.data.database.entity.DownloadEntity
import com.orbin.domain.repository.DownloadRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
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
                val request =
                    DownloadManager
                        .Request(Uri.parse(url))
                        .setTitle(fileName)
                        .setDescription("Orbin download")
                        .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                        .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "Orbin/$fileName")
                        .setAllowedOverMetered(true)
                        .setAllowedOverRoaming(true)

                val id = downloadManager.enqueue(request)
                dao.upsert(
                    DownloadEntity(
                        id = id,
                        url = url,
                        fileName = fileName,
                        status = DownloadStatus.QUEUED.name,
                        createdAtMillis = System.currentTimeMillis(),
                    ),
                )
                id
            }

        override suspend fun refreshStatuses() =
            withContext(ioDispatcher) {
                dao.all().forEach { entity ->
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
    }
