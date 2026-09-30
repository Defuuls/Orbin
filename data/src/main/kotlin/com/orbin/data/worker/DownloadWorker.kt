package com.orbin.data.worker

import android.content.ContentValues
import android.content.Context
import android.os.Environment
import android.provider.MediaStore
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.orbin.core.model.DownloadStatus
import com.orbin.data.database.dao.DownloadDao
import com.orbin.data.repository.downloadRequestHeaders
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.InputStream
import java.io.OutputStream

@HiltWorker
class DownloadWorker
    @AssistedInject
    constructor(
        @Assisted private val context: Context,
        @Assisted workerParams: WorkerParameters,
        private val dao: DownloadDao,
        private val okHttpClient: OkHttpClient,
    ) : CoroutineWorker(context, workerParams) {
        override suspend fun doWork(): Result =
            withContext(Dispatchers.IO) {
                val id = inputData.getLong(KEY_ID, -1L)
                val url = inputData.getString(KEY_URL)
                val fileName = inputData.getString(KEY_FILE_NAME)
                val relativeDir = inputData.getString(KEY_RELATIVE_DIR) ?: ""

                if (id == -1L || url.isNullOrBlank() || fileName.isNullOrBlank()) {
                    return@withContext Result.failure()
                }

                try {
                    dao.updateStatus(id, DownloadStatus.RUNNING.name)

                    val requestBuilder = Request.Builder().url(url)
                    downloadRequestHeaders(url).forEach { (name, value) ->
                        requestBuilder.addHeader(name, value)
                    }

                    val response = okHttpClient.newCall(requestBuilder.build()).execute()
                    if (!response.isSuccessful) {
                        dao.updateStatus(id, DownloadStatus.FAILED.name)
                        return@withContext Result.failure()
                    }

                    val body = response.body

                    val path = "${Environment.DIRECTORY_DOWNLOADS}/Orbin/$relativeDir".trimEnd('/')
                    val values =
                        ContentValues().apply {
                            put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                            put(
                                MediaStore.Downloads.MIME_TYPE,
                                body.contentType()?.toString() ?: "application/octet-stream",
                            )
                            put(MediaStore.Downloads.RELATIVE_PATH, path)
                            put(MediaStore.Downloads.IS_PENDING, 1)
                        }

                    val resolver = context.contentResolver
                    val target = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    if (target == null) {
                        dao.updateStatus(id, DownloadStatus.FAILED.name)
                        return@withContext Result.failure()
                    }

                    var success = false
                    try {
                        resolver.openOutputStream(target)?.use { output: OutputStream ->
                            body.byteStream().use { input: InputStream ->
                                input.copyTo(output)
                            }
                        }
                        success = true
                    } finally {
                        if (success) {
                            val updateValues =
                                ContentValues().apply {
                                    put(MediaStore.Downloads.IS_PENDING, 0)
                                }
                            resolver.update(target, updateValues, null, null)
                            dao.updateStatus(id, DownloadStatus.COMPLETED.name)
                        } else {
                            resolver.delete(target, null, null)
                            dao.updateStatus(id, DownloadStatus.FAILED.name)
                        }
                    }

                    if (success) Result.success() else Result.failure()
                } catch (e: Exception) {
                    dao.updateStatus(id, DownloadStatus.FAILED.name)
                    Result.failure()
                }
            }

        companion object {
            const val KEY_ID = "id"
            const val KEY_URL = "url"
            const val KEY_FILE_NAME = "fileName"
            const val KEY_RELATIVE_DIR = "relativeDir"
        }
    }
