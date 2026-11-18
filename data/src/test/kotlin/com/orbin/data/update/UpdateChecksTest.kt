package com.orbin.data.update

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.security.MessageDigest

/** The two decisions in the in-app updater that do not need Android to test. */
class UpdateChecksTest {
    @Test
    fun theHashIsReadFromASha256sumLine() {
        val hash = "88F1BD0F5EBB6BF84098FABB82B3F49986CB2BEF4573280FAEDFCA15B847B938"

        assertThat(parseSha256("$hash  orbin-v148-Nectarine.apk\n")).isEqualTo(hash.lowercase())
    }

    /** No readable hash means nothing to verify against, so the update must not proceed. */
    @Test
    fun anUnreadableChecksumFileIsRefused() {
        assertThrows(UpdateVerificationException::class.java) { parseSha256("") }
        assertThrows(UpdateVerificationException::class.java) { parseSha256("<html>Not Found</html>") }
        assertThrows(UpdateVerificationException::class.java) { parseSha256("abc123  orbin.apk") }
    }

    @Test
    fun theLaunchCheckRunsOncePerInterval() {
        val last = 1_000_000L

        assertThat(shouldCheckOnLaunch(true, last, last + LAUNCH_CHECK_INTERVAL_MS - 1)).isFalse()
        assertThat(shouldCheckOnLaunch(true, last, last + LAUNCH_CHECK_INTERVAL_MS)).isTrue()
    }

    /** Never checked (0) is a day's worth of milliseconds ago on any real clock. */
    @Test
    fun aFirstLaunchChecks() {
        assertThat(shouldCheckOnLaunch(true, 0L, System.currentTimeMillis())).isTrue()
    }

    @Test
    fun theLaunchCheckCanBeTurnedOff() {
        assertThat(shouldCheckOnLaunch(false, 0L, LAUNCH_CHECK_INTERVAL_MS * 2)).isFalse()
    }

    /** A clock set back must not silence the check until real time catches up. */
    @Test
    fun aLastCheckInTheFutureDoesNotSuppressTheCheck() {
        assertThat(shouldCheckOnLaunch(true, 5_000_000L, 1_000L)).isTrue()
    }

    @Test
    fun aDownloadIsHashedAsItIsWritten() =
        runTest {
            val bytes = ByteArray(200_000) { (it % 251).toByte() }
            val written = ByteArrayOutputStream()
            val digest = MessageDigest.getInstance("SHA-256")
            val progress = mutableListOf<Float?>()

            copyHashing(ByteArrayInputStream(bytes), written, digest, bytes.size.toLong(), MAX_APK_BYTES, progress::add)

            assertThat(written.toByteArray()).isEqualTo(bytes)
            assertThat(digest.digest()).isEqualTo(MessageDigest.getInstance("SHA-256").digest(bytes))
            assertThat(progress.last()).isEqualTo(1f)
        }

    @Test
    fun aDownloadExactlyAtTheLimitIsKept() =
        runTest {
            val written = ByteArrayOutputStream()

            copyHashing(ByteArrayInputStream(ByteArray(1_000)), written, sha256(), null, maxBytes = 1_000) {}

            assertThat(written.size()).isEqualTo(1_000)
        }

    /** The declared length is whatever the server says; only the bytes that arrive are trusted. */
    @Test
    fun aDownloadThatRunsPastTheLimitIsAbandonedWhateverLengthWasDeclared() =
        runTest {
            val written = ByteArrayOutputStream()
            val endless =
                object : InputStream() {
                    override fun read(): Int = 0

                    override fun read(
                        b: ByteArray,
                        off: Int,
                        len: Int,
                    ): Int = len
                }

            val failure =
                runCatching { copyHashing(endless, written, sha256(), total = 10L, maxBytes = 100_000) {} }
                    .exceptionOrNull()

            assertThat(failure).isInstanceOf(UpdateVerificationException::class.java)
            // Nothing past the limit reached the file.
            assertThat(written.size().toLong()).isAtMost(100_000L)
        }

    @Test
    fun theLimitLeavesRoomForARealRelease() {
        assertThat(MAX_APK_BYTES).isAtLeast(100L * 1024 * 1024)
    }

    private fun sha256(): MessageDigest = MessageDigest.getInstance("SHA-256")
}
