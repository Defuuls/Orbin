package com.orbin.network.interceptor

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class RetryAfterTest {
    @Test
    fun `delta seconds are read as is`() {
        assertThat(RetryAfter.parseSeconds("30")).isEqualTo(30L)
        assertThat(RetryAfter.parseSeconds(" 7 ")).isEqualTo(7L)
        assertThat(RetryAfter.parseSeconds("0")).isEqualTo(0L)
    }

    @Test
    fun `a negative number is zero`() {
        assertThat(RetryAfter.parseSeconds("-5")).isEqualTo(0L)
    }

    @Test
    fun `an http date is the seconds from now`() {
        val now = 1_700_000_000_000L // Tue, 14 Nov 2023 22:13:20 GMT
        assertThat(RetryAfter.parseSeconds("Tue, 14 Nov 2023 22:14:20 GMT", nowMs = now)).isEqualTo(60L)
    }

    @Test
    fun `an http date in the past is zero`() {
        val now = 1_700_000_000_000L
        assertThat(RetryAfter.parseSeconds("Tue, 14 Nov 2023 22:00:00 GMT", nowMs = now)).isEqualTo(0L)
    }

    @Test
    fun `missing blank or garbage headers are unknown`() {
        assertThat(RetryAfter.parseSeconds(null)).isNull()
        assertThat(RetryAfter.parseSeconds("")).isNull()
        assertThat(RetryAfter.parseSeconds("   ")).isNull()
        assertThat(RetryAfter.parseSeconds("soon")).isNull()
        assertThat(RetryAfter.parseSeconds("1.5")).isNull()
    }
}
