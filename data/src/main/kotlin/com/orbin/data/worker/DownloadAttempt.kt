package com.orbin.data.worker

import com.orbin.network.interceptor.RetryAfter

/**
 * Decisions a media download makes about retrying and about resuming a partial file.
 * Kept free of Android and OkHttp so host unit tests can cover the rules directly.
 */
internal object DownloadAttempt {
    /** WorkManager [androidx.work.ListenableWorker.getRunAttemptCount] is 0 on the first run. */
    const val MAX_ATTEMPTS = 5

    const val MAX_CONCURRENT_PER_HOST = 3

    const val MAX_RETRY_AFTER_SECONDS = 60L

    /** Whether this run can still ask WorkManager to try again after a transient failure. */
    fun canRetry(runAttemptCount: Int): Boolean = runAttemptCount < MAX_ATTEMPTS

    fun isRetryableStatus(code: Int): Boolean =
        code == HTTP_REQUEST_TIMEOUT ||
            code == RetryAfter.HTTP_TOO_MANY_REQUESTS ||
            code in HTTP_SERVER_ERROR_MIN..HTTP_SERVER_ERROR_MAX

    fun isPermanentClientError(code: Int): Boolean =
        code in HTTP_CLIENT_ERROR_MIN..HTTP_CLIENT_ERROR_MAX && !isRetryableStatus(code)

    /** `Range: bytes={existing}-` when a partial file already has bytes, otherwise no Range header. */
    fun rangeHeader(existingBytes: Long): String? = if (existingBytes > 0L) "bytes=$existingBytes-" else null

    /**
     * How to apply a response body to a partial file of [existingBytes]. [contentRangeStart] and
     * [totalBytes] come from `Content-Range` when the server sent one.
     */
    fun bodyWriteMode(
        statusCode: Int,
        existingBytes: Long,
        contentRangeStart: Long? = null,
        totalBytes: Long? = null,
    ): BodyWriteMode =
        when (statusCode) {
            HTTP_PARTIAL_CONTENT ->
                when (contentRangeStart) {
                    null, existingBytes -> BodyWriteMode.APPEND
                    0L -> BodyWriteMode.OVERWRITE
                    else -> BodyWriteMode.RESTART
                }
            HTTP_OK -> BodyWriteMode.OVERWRITE
            HTTP_RANGE_NOT_SATISFIABLE ->
                when {
                    totalBytes != null && existingBytes == totalBytes && existingBytes > 0L ->
                        BodyWriteMode.ALREADY_COMPLETE
                    existingBytes > 0L && totalBytes == null -> BodyWriteMode.ALREADY_COMPLETE
                    else -> BodyWriteMode.RESTART
                }
            else -> BodyWriteMode.UNUSABLE
        }

    /**
     * Seconds to wait before a WorkManager retry for 429 and 503, honouring `Retry-After` and
     * falling back to [RetryAfter.DEFAULT_SECONDS]. Other retryable failures rely on backoff alone.
     */
    fun retryAfterSeconds(
        statusCode: Int?,
        retryAfterHeader: String?,
        nowMs: Long = System.currentTimeMillis(),
    ): Long? {
        if (statusCode != RetryAfter.HTTP_TOO_MANY_REQUESTS && statusCode != HTTP_SERVICE_UNAVAILABLE) {
            return null
        }
        val parsed = RetryAfter.parseSeconds(retryAfterHeader, nowMs) ?: RetryAfter.DEFAULT_SECONDS
        return parsed.coerceIn(0L, MAX_RETRY_AFTER_SECONDS)
    }

    /**
     * Start byte and optional total size from a `Content-Range` value such as `bytes 100-199/500`
     * or, on 416, the unsatisfiable form that reports only the total. Either part may be missing.
     */
    fun parseContentRange(header: String?): ContentRange? {
        val value = header?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val partial = PARTIAL_CONTENT_RANGE.matchEntire(value)
        if (partial != null) {
            val start = partial.groupValues[CONTENT_RANGE_START_GROUP].toLong()
            val total = partial.groupValues[CONTENT_RANGE_TOTAL_GROUP].toLongOrNull()
            return ContentRange(start, total)
        }
        val unsatisfiable = UNSATISFIABLE_CONTENT_RANGE.matchEntire(value) ?: return null
        return ContentRange(start = null, total = unsatisfiable.groupValues[UNSATISFIABLE_TOTAL_GROUP].toLong())
    }
}

internal enum class BodyWriteMode {
    /** Discard any partial file and write the body from the start. */
    OVERWRITE,

    /** Append the body to the bytes already on disk. */
    APPEND,

    /** The partial file is already the complete object; do not write. */
    ALREADY_COMPLETE,

    /** The partial file cannot be reused; delete it and fetch from the start. */
    RESTART,

    /** The status is not a successful or resumable download response. */
    UNUSABLE,
}

internal data class ContentRange(
    val start: Long?,
    val total: Long?,
)

internal const val HTTP_OK = 200
internal const val HTTP_PARTIAL_CONTENT = 206
internal const val HTTP_REQUEST_TIMEOUT = 408
internal const val HTTP_RANGE_NOT_SATISFIABLE = 416
internal const val HTTP_SERVICE_UNAVAILABLE = 503
internal const val HTTP_CLIENT_ERROR_MIN = 400
internal const val HTTP_CLIENT_ERROR_MAX = 499
internal const val HTTP_SERVER_ERROR_MIN = 500
internal const val HTTP_SERVER_ERROR_MAX = 599

private val PARTIAL_CONTENT_RANGE = Regex("""bytes\s+(\d+)-(\d+)/(\d+|\*)""")
private val UNSATISFIABLE_CONTENT_RANGE = Regex("""bytes\s+\*/(\d+)""")
private const val CONTENT_RANGE_START_GROUP = 1
private const val CONTENT_RANGE_TOTAL_GROUP = 3
private const val UNSATISFIABLE_TOTAL_GROUP = 1
