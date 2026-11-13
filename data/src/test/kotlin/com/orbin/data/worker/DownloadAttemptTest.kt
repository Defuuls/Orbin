package com.orbin.data.worker

import com.google.common.truth.Truth.assertThat
import com.orbin.network.interceptor.RetryAfter
import org.junit.Test
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

class DownloadAttemptTest {
    @Test
    fun aFifthAttemptIsTheLastRetryAndASixthIsNotRetried() {
        assertThat(DownloadAttempt.canRetry(0)).isTrue()
        assertThat(DownloadAttempt.canRetry(DownloadAttempt.MAX_ATTEMPTS - 1)).isTrue()
        assertThat(DownloadAttempt.canRetry(DownloadAttempt.MAX_ATTEMPTS)).isFalse()
    }

    @Test
    fun ioTimeoutsRateLimitsAndServerErrorsAreRetryable() {
        for (code in listOf(408, 429, 500, 502, 503, 504, 599)) {
            assertThat(DownloadAttempt.isRetryableStatus(code)).isTrue()
            assertThat(DownloadAttempt.isPermanentClientError(code)).isFalse()
        }
    }

    @Test
    fun otherClientErrorsArePermanent() {
        for (code in listOf(400, 401, 403, 404, 410, 451)) {
            assertThat(DownloadAttempt.isRetryableStatus(code)).isFalse()
            assertThat(DownloadAttempt.isPermanentClientError(code)).isTrue()
        }
    }

    @Test
    fun aRangeHeaderIsSentOnlyWhenBytesAreAlreadyOnDisk() {
        assertThat(DownloadAttempt.rangeHeader(0)).isNull()
        assertThat(DownloadAttempt.rangeHeader(-1)).isNull()
        assertThat(DownloadAttempt.rangeHeader(1)).isEqualTo("bytes=1-")
        assertThat(DownloadAttempt.rangeHeader(12_345)).isEqualTo("bytes=12345-")
    }

    @Test
    fun a206AppendsAndA200Restarts() {
        assertThat(DownloadAttempt.bodyWriteMode(HTTP_PARTIAL_CONTENT, existingBytes = 10))
            .isEqualTo(BodyWriteMode.APPEND)
        assertThat(
            DownloadAttempt.bodyWriteMode(HTTP_PARTIAL_CONTENT, existingBytes = 10, contentRangeStart = 10),
        ).isEqualTo(BodyWriteMode.APPEND)
        assertThat(DownloadAttempt.bodyWriteMode(HTTP_OK, existingBytes = 10)).isEqualTo(BodyWriteMode.OVERWRITE)
        assertThat(DownloadAttempt.bodyWriteMode(HTTP_OK, existingBytes = 0)).isEqualTo(BodyWriteMode.OVERWRITE)
    }

    @Test
    fun a206ThatStartsAtZeroOverwritesAndAMismatchedStartRestarts() {
        assertThat(
            DownloadAttempt.bodyWriteMode(HTTP_PARTIAL_CONTENT, existingBytes = 10, contentRangeStart = 0),
        ).isEqualTo(BodyWriteMode.OVERWRITE)
        assertThat(
            DownloadAttempt.bodyWriteMode(HTTP_PARTIAL_CONTENT, existingBytes = 10, contentRangeStart = 4),
        ).isEqualTo(BodyWriteMode.RESTART)
    }

    @Test
    fun a416TreatsAMatchingPartialAsCompleteAndOtherwiseRestarts() {
        assertThat(
            DownloadAttempt.bodyWriteMode(
                HTTP_RANGE_NOT_SATISFIABLE,
                existingBytes = 100,
                totalBytes = 100,
            ),
        ).isEqualTo(BodyWriteMode.ALREADY_COMPLETE)
        assertThat(
            DownloadAttempt.bodyWriteMode(
                HTTP_RANGE_NOT_SATISFIABLE,
                existingBytes = 50,
                totalBytes = null,
            ),
        ).isEqualTo(BodyWriteMode.ALREADY_COMPLETE)
        assertThat(
            DownloadAttempt.bodyWriteMode(
                HTTP_RANGE_NOT_SATISFIABLE,
                existingBytes = 50,
                totalBytes = 40,
            ),
        ).isEqualTo(BodyWriteMode.RESTART)
        assertThat(
            DownloadAttempt.bodyWriteMode(HTTP_RANGE_NOT_SATISFIABLE, existingBytes = 0, totalBytes = 10),
        ).isEqualTo(BodyWriteMode.RESTART)
    }

    @Test
    fun otherStatusesAreUnusable() {
        assertThat(DownloadAttempt.bodyWriteMode(404, existingBytes = 0)).isEqualTo(BodyWriteMode.UNUSABLE)
        assertThat(DownloadAttempt.bodyWriteMode(429, existingBytes = 8)).isEqualTo(BodyWriteMode.UNUSABLE)
        assertThat(DownloadAttempt.bodyWriteMode(503, existingBytes = 8)).isEqualTo(BodyWriteMode.UNUSABLE)
    }

    @Test
    fun retryAfterIsHonouredFor429And503AndCapped() {
        assertThat(DownloadAttempt.retryAfterSeconds(429, "20")).isEqualTo(20L)
        assertThat(DownloadAttempt.retryAfterSeconds(503, "8")).isEqualTo(8L)
        assertThat(DownloadAttempt.retryAfterSeconds(429, null)).isEqualTo(RetryAfter.DEFAULT_SECONDS)
        assertThat(DownloadAttempt.retryAfterSeconds(429, "3600"))
            .isEqualTo(DownloadAttempt.MAX_RETRY_AFTER_SECONDS)
        assertThat(DownloadAttempt.retryAfterSeconds(408, "20")).isNull()
        assertThat(DownloadAttempt.retryAfterSeconds(500, "20")).isNull()
        assertThat(DownloadAttempt.retryAfterSeconds(null, "20")).isNull()
    }

    @Test
    fun anHttpDateRetryAfterIsParsed() {
        val now = 1_700_000_000_000L
        val at = ZonedDateTime.ofInstant(java.time.Instant.ofEpochMilli(now), ZoneOffset.UTC).plusSeconds(25)
        val header = DateTimeFormatter.RFC_1123_DATE_TIME.format(at)
        assertThat(DownloadAttempt.retryAfterSeconds(429, header, nowMs = now)).isEqualTo(25L)
    }

    @Test
    fun contentRangeParsesAPartialAndAnUnsatisfiableValue() {
        assertThat(DownloadAttempt.parseContentRange("bytes 100-199/500"))
            .isEqualTo(ContentRange(start = 100, total = 500))
        assertThat(DownloadAttempt.parseContentRange("bytes 100-199/*"))
            .isEqualTo(ContentRange(start = 100, total = null))
        assertThat(DownloadAttempt.parseContentRange("bytes */4096"))
            .isEqualTo(ContentRange(start = null, total = 4096))
        assertThat(DownloadAttempt.parseContentRange(null)).isNull()
        assertThat(DownloadAttempt.parseContentRange("garbage")).isNull()
    }
}
