package com.orbin.data.repository

import android.content.ContentValues
import android.content.Context
import android.os.Environment
import android.provider.MediaStore
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.orbin.data.worker.DownloadWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidDownloadQueue
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : DownloadPlatformQueue {
        private val workManager: WorkManager
            get() = WorkManager.getInstance(context)

        override fun enqueue(
            id: Long,
            url: String,
            fileName: String,
            relativeDir: String,
        ) {
            val inputData =
                Data
                    .Builder()
                    .putLong(DownloadWorker.KEY_ID, id)
                    .putString(DownloadWorker.KEY_URL, url)
                    .putString(DownloadWorker.KEY_FILE_NAME, fileName)
                    .putString(DownloadWorker.KEY_RELATIVE_DIR, relativeDir)
                    .build()

            val workRequest =
                OneTimeWorkRequestBuilder<DownloadWorker>()
                    .setInputData(inputData)
                    .build()

            workManager.enqueue(workRequest)
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

        private companion object {
            const val MIME_TEXT_PLAIN = "text/plain"
        }
    }
