package com.orbin.data.repository

import android.content.ContentValues
import android.content.Context
import android.os.Environment
import android.provider.MediaStore
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.orbin.core.model.DownloadStatus
import com.orbin.data.database.dao.DownloadDao
import com.orbin.data.worker.DownloadWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidDownloadQueue
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val dao: DownloadDao,
    ) : DownloadPlatformQueue {
        private val workManager: WorkManager
            get() = WorkManager.getInstance(context)

        private val migrationMutex = Mutex()

        override suspend fun enqueue(id: Long) =
            withContext(Dispatchers.IO) {
                prepare()
                migrationMutex.withLock { schedule(id) }
            }

        private fun schedule(id: Long) {
            val inputData = Data.Builder().putLong(DownloadWorker.KEY_ID, id).build()
            val workRequest =
                OneTimeWorkRequestBuilder<DownloadWorker>()
                    .setInputData(inputData)
                    .build()
            workManager.enqueueUniqueWork("download-$id", ExistingWorkPolicy.KEEP, workRequest).result.get()
        }

        /** Retire pre-upgrade WorkSpecs rather than retaining plaintext input as a fallback. */
        suspend fun prepare() =
            withContext(Dispatchers.IO) {
                migrationMutex.withLock {
                    val migration = context.getSharedPreferences("download-security", Context.MODE_PRIVATE)
                    if (migration.getBoolean("opaque-inputs", false)) return@withLock
                    val oldWork = workManager.getWorkInfosByTag(DownloadWorker::class.java.name).get()
                    oldWork.filterNot { it.state.isFinished }.forEach { workManager.cancelWorkById(it.id).result.get() }
                    workManager.pruneWork().result.get()
                    // Active encrypted records are the authority for re-creating interrupted work.
                    dao
                        .all()
                        .filter { it.status in setOf(DownloadStatus.QUEUED.name, DownloadStatus.RUNNING.name) }
                        .forEach { schedule(it.id) }
                    check(
                        migration.edit().putBoolean("opaque-inputs", true).commit(),
                    ) { "Could not record download migration" }
                }
            }

        override suspend fun cancelAll() =
            withContext(Dispatchers.IO) {
                prepare()
                migrationMutex.withLock {
                    workManager.cancelAllWorkByTag(DownloadWorker::class.java.name).result.get()
                    workManager.pruneWork().result.get()
                    Unit
                }
            }

        override fun writeTextFile(
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

        override fun saveFile(
            path: String,
            fileName: String,
            mimeType: String,
        ): Boolean {
            val values =
                ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, sanitizeFileName(fileName))
                    put(MediaStore.Downloads.MIME_TYPE, mimeType)
                    put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/Orbin")
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
            val resolver = context.contentResolver
            val target = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return false
            return runCatching {
                resolver.openOutputStream(target)?.use { output ->
                    File(path).inputStream().use { it.copyTo(output) }
                } ?: error("Unable to open default downloads folder")
                ContentValues()
                    .apply { put(MediaStore.Downloads.IS_PENDING, 0) }
                    .also { resolver.update(target, it, null, null) }
            }.onFailure {
                resolver.delete(target, null, null)
            }.isSuccess
        }

        private companion object {
            const val MIME_TEXT_PLAIN = "text/plain"
        }
    }
