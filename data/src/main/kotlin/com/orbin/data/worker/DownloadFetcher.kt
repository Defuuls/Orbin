package com.orbin.data.worker

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/** Result of writing a remote file into a local partial file. */
internal sealed class DownloadFetchResult {
    data class Complete(
        val mimeType: String?,
    ) : DownloadFetchResult()

    data class Retry(
        val httpCode: Int?,
        val retryAfterSeconds: Long?,
    ) : DownloadFetchResult()

    data class Fail(
        val httpCode: Int?,
    ) : DownloadFetchResult()
}

/**
 * Streams a download into a local partial file, resuming with `Range` when that file already
 * has bytes. A 206 response is appended, a 200 starts the file over, and a finished file is left
 * for the caller to publish; the partial itself is never treated as a complete download.
 */
internal class DownloadFetcher(
    private val client: OkHttpClient,
) {
    suspend fun fetch(
        url: String,
        headers: Map<String, String>,
        dest: File,
    ): DownloadFetchResult {
        dest.parentFile?.mkdirs()
        return fetchFrom(url, headers, dest, allowRestart = true)
    }

    private suspend fun fetchFrom(
        url: String,
        headers: Map<String, String>,
        dest: File,
        allowRestart: Boolean,
    ): DownloadFetchResult {
        val existing = dest.takeIf { it.exists() }?.length() ?: 0L
        val request =
            Request
                .Builder()
                .url(url)
                .apply {
                    headers.forEach { (name, value) -> addHeader(name, value) }
                    val range = DownloadAttempt.rangeHeader(existing)
                    if (range != null) {
                        header(HEADER_RANGE, range)
                        // Transparent gzip would make Range offsets and the file length disagree.
                        header(HEADER_ACCEPT_ENCODING, ACCEPT_ENCODING_IDENTITY)
                    }
                }.build()
        val step =
            try {
                client.newCall(request).execute().use { response ->
                    handleResponse(response, dest, existing)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: IOException) {
                return DownloadFetchResult.Retry(httpCode = null, retryAfterSeconds = null)
            } catch (_: IllegalArgumentException) {
                return DownloadFetchResult.Fail(httpCode = null)
            }
        return when (step) {
            is Step.Done -> step.result
            is Step.Restart -> {
                dest.delete()
                if (allowRestart) {
                    fetchFrom(url, headers, dest, allowRestart = false)
                } else {
                    DownloadFetchResult.Retry(httpCode = step.httpCode, retryAfterSeconds = null)
                }
            }
        }
    }

    private suspend fun handleResponse(
        response: Response,
        dest: File,
        existing: Long,
    ): Step {
        val code = response.code
        val contentRange = DownloadAttempt.parseContentRange(response.header(HEADER_CONTENT_RANGE))
        return when (
            val mode =
                DownloadAttempt.bodyWriteMode(
                    statusCode = code,
                    existingBytes = existing,
                    contentRangeStart = contentRange?.start,
                    totalBytes = contentRange?.total,
                )
        ) {
            BodyWriteMode.ALREADY_COMPLETE -> Step.Done(DownloadFetchResult.Complete(mimeType = mimeTypeOf(response)))
            BodyWriteMode.RESTART -> Step.Restart(code)
            BodyWriteMode.UNUSABLE -> Step.Done(classifyError(response))
            BodyWriteMode.OVERWRITE,
            BodyWriteMode.APPEND,
            -> {
                val body = response.body
                try {
                    writeBody(dest, body, append = mode == BodyWriteMode.APPEND)
                    Step.Done(DownloadFetchResult.Complete(mimeType = mimeTypeOf(response, body)))
                } catch (e: CancellationException) {
                    throw e
                } catch (_: IOException) {
                    Step.Done(DownloadFetchResult.Retry(httpCode = null, retryAfterSeconds = null))
                }
            }
        }
    }

    private fun classifyError(response: Response): DownloadFetchResult {
        val code = response.code
        return if (DownloadAttempt.isRetryableStatus(code)) {
            DownloadFetchResult.Retry(
                httpCode = code,
                retryAfterSeconds = DownloadAttempt.retryAfterSeconds(code, response.header(HEADER_RETRY_AFTER)),
            )
        } else {
            DownloadFetchResult.Fail(httpCode = code)
        }
    }

    private suspend fun writeBody(
        dest: File,
        body: ResponseBody,
        append: Boolean,
    ) {
        val output: OutputStream = if (append) FileOutputStream(dest, true) else dest.outputStream()
        output.use { stream ->
            body.byteStream().use { input: InputStream ->
                copyCancellable(input, stream)
                stream.flush()
            }
        }
    }

    private suspend fun copyCancellable(
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

    private fun mimeTypeOf(
        response: Response,
        body: ResponseBody? = response.body,
    ): String? = body?.contentType()?.toString() ?: response.header(HEADER_CONTENT_TYPE)

    private sealed class Step {
        data class Done(
            val result: DownloadFetchResult,
        ) : Step()

        data class Restart(
            val httpCode: Int,
        ) : Step()
    }

    private companion object {
        const val HEADER_RANGE = "Range"
        const val HEADER_ACCEPT_ENCODING = "Accept-Encoding"
        const val ACCEPT_ENCODING_IDENTITY = "identity"
        const val HEADER_CONTENT_RANGE = "Content-Range"
        const val HEADER_RETRY_AFTER = "Retry-After"
        const val HEADER_CONTENT_TYPE = "Content-Type"
    }
}

/** App-private partial file for download [id]; published to MediaStore only after a complete fetch. */
internal fun downloadPartialFile(
    filesDir: File,
    id: Long,
): File = File(File(filesDir, PARTIAL_DIR), "$id$PARTIAL_SUFFIX")

private const val PARTIAL_DIR = "download-partials"
private const val PARTIAL_SUFFIX = ".part"
