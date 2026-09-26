package com.orbin.data.update

import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test

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
        assertThat(shouldCheckOnLaunch(true, 0L, last)).isTrue()
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
}
