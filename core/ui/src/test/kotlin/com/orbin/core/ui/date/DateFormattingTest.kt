package com.orbin.core.ui.date

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DateFormattingTest {
    private val now = 1_700_000_000_000L // fixed "now" so assertions are deterministic

    @Test
    fun `unknown timestamp returns null`() {
        assertThat(formatRelativeTime(0L, now)).isNull()
        assertThat(formatRelativeTime(-1L, now)).isNull()
    }

    @Test
    fun `sub-minute delta reads as just now`() {
        assertThat(formatRelativeTime(now - 5_000L, now)).isEqualTo("just now")
    }

    @Test
    fun `minutes delta reads in minutes`() {
        assertThat(formatRelativeTime(now - 5L * 60_000L, now)).isEqualTo("5m")
    }

    @Test
    fun `hours delta reads in hours`() {
        assertThat(formatRelativeTime(now - 3L * 60L * 60_000L, now)).isEqualTo("3h")
    }

    @Test
    fun `days delta reads in days`() {
        assertThat(formatRelativeTime(now - 2L * 24L * 60L * 60_000L, now)).isEqualTo("2d")
    }

    @Test
    fun `delta older than a week falls back to the absolute date`() {
        val thirtyDays = now - 30L * 24L * 60L * 60_000L
        assertThat(formatRelativeTime(thirtyDays, now)).isEqualTo(formatThreadDate(thirtyDays))
    }

    @Test
    fun `future timestamp from clock skew falls back to the absolute date`() {
        val future = now + 60L * 60_000L
        assertThat(formatRelativeTime(future, now)).isEqualTo(formatThreadDate(future))
    }
}
