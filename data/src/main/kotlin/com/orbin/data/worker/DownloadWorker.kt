package com.orbin.data.worker

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import co.touchlab.kermit.Logger
import com.orbin.core.model.DownloadStatus
import com.orbin.data.database.dao.DownloadDao
import com.orbin.data.repository.downloadRequestHeaders
import com.orbin.network.di.VideoOkHttp
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

@HiltWorker
class DownloadWorker
    @AssistedInject
    constructor(
        @Assisted private val context: Context,
        @Assisted workerParams: WorkerParameters,
        private val dao: DownloadDao,
        @VideoOkHttp private val okHttpClient: OkHttpClient,
        private val hostGate: DownloadHostGate,
    ) : CoroutineWorker(context, workerParams) {
        override suspend fun doWork(): Result =
            withContext(Dispatchers.IO) {
                val id = inputData.getLong(KEY_ID, -1L)
                if (id == -1L) return@withContext Result.failure()
                // No metadata fallback: old scheduler inputs must never become an authority.
                val download = dao.getById(id) ?: return@withContext Result.success()
                val url = download.url
                val fileName = download.fileName
                val relativeDir = download.relativeDir
                if (!url.startsWith("https://", ignoreCase = true) || fileName.isBlank()) {
                    return@withContext Result.failure()
                }
                val host = url.toHttpUrlOrNull()?.host
                if (host.isNullOrBlank()) {
                    dao.updateStatus(id, DownloadStatus.FAILED.name)
                    return@withContext Result.failure()
                }

                try {
                    dao.updateStatus(id, DownloadStatus.RUNNING.name)
                    val partial = downloadPartialFile(context.filesDir, id)
                    val fetched =
                        hostGate.withPermit(host) {
                            DownloadFetcher(okHttpClient).fetch(
                                url = url,
                                headers = downloadRequestHeaders(url),
                                dest = partial,
                            )
                        }
                    when (fetched) {
                        is DownloadFetchResult.Complete ->
                            publishToMediaStore(id, fileName, relativeDir, partial, fetched.mimeType)
                        is DownloadFetchResult.Retry ->
                            retryOrFail(id, fetched.retryAfterSeconds)
                        is DownloadFetchResult.Fail -> {
                            partial.delete()
                            dao.updateStatus(id, DownloadStatus.FAILED.name)
                            Result.failure()
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: IOException) {
                    Logger.e(tag = TAG) { "Download failed for id=$id (${e::class.simpleName})" }
                    retryOrFail(id, retryAfterSeconds = null)
                } catch (e: IllegalArgumentException) {
                    Logger.e(tag = TAG) { "Invalid download request for id=$id (${e::class.simpleName})" }
                    dao.updateStatus(id, DownloadStatus.FAILED.name)
                    Result.failure()
                } catch (e: SecurityException) {
                    Logger.e(tag = TAG) { "Missing storage permission for download id=$id (${e::class.simpleName})" }
                    dao.updateStatus(id, DownloadStatus.FAILED.name)
                    Result.failure()
                }
            }

        private suspend fun retryOrFail(
            id: Long,
            retryAfterSeconds: Long?,
        ): Result {
            if (!DownloadAttempt.canRetry(runAttemptCount)) {
                dao.updateStatus(id, DownloadStatus.FAILED.name)
                return Result.failure()
            }
            if (retryAfterSeconds != null && retryAfterSeconds > 0L) {
                delay(retryAfterSeconds * MILLIS_PER_SECOND)
            }
            return Result.retry()
        }

        private suspend fun publishToMediaStore(
            id: Long,
            fileName: String,
            relativeDir: String,
            partial: File,
            mimeType: String?,
        ): Result {
            if (!partial.exists()) {
                dao.updateStatus(id, DownloadStatus.FAILED.name)
                return Result.failure()
            }
            val path = "${Environment.DIRECTORY_DOWNLOADS}/Orbin/$relativeDir".trimEnd('/')
            val values =
                ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                    put(MediaStore.Downloads.MIME_TYPE, mimeType ?: MIME_OCTET_STREAM)
                    put(MediaStore.Downloads.RELATIVE_PATH, path)
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }

            val resolver = context.contentResolver
            val target = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            if (target == null) {
                return retryOrFail(id, retryAfterSeconds = null)
            }

            var success = false
            var cancelled = false
            try {
                success = copyFileToTarget(resolver, target, partial)
            } catch (e: CancellationException) {
                cancelled = true
                throw e
            } finally {
                withContext(NonCancellable) {
                    finalizeTarget(resolver, target, id, partial, success, cancelled)
                }
            }

            return if (success) {
                Result.success()
            } else {
                retryOrFail(id, retryAfterSeconds = null)
            }
        }

        private suspend fun copyFileToTarget(
            resolver: ContentResolver,
            target: Uri,
            source: File,
        ): Boolean {
            val outputStream = resolver.openOutputStream(target) ?: return false
            outputStream.use { output: OutputStream ->
                source.inputStream().use { input: InputStream ->
                    copyStreamCancellable(input, output)
                }
            }
            return true
        }

        private suspend fun copyStreamCancellable(
            input: InputStream,
            output: OutputStream,
        ) {
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                currentCoroutineContext().ensureActive()
                val read = input.read(buffer)
                if (read < 0) break
                output.write(buffer, 0, read)
            }
        }

        private suspend fun finalizeTarget(
            resolver: ContentResolver,
            target: Uri,
            id: Long,
            partial: File,
            success: Boolean,
            cancelled: Boolean,
        ) {
            if (success) {
                val updateValues =
                    ContentValues().apply {
                        put(MediaStore.Downloads.IS_PENDING, 0)
                    }
                resolver.update(target, updateValues, null, null)
                partial.delete()
                dao.updateStatus(id, DownloadStatus.COMPLETED.name)
            } else {
                resolver.delete(target, null, null)
                // Keep [partial] so a retry can publish it, or resume if the copy never finished.
                if (!cancelled && !DownloadAttempt.canRetry(runAttemptCount)) {
                    dao.updateStatus(id, DownloadStatus.FAILED.name)
                }
            }
        }

        companion object {
            private const val TAG = "DownloadWorker"
            const val KEY_ID = "id"
            private const val MIME_OCTET_STREAM = "application/octet-stream"
            private const val MILLIS_PER_SECOND = 1_000L
        }
    }
