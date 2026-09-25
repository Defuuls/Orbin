package com.orbin.feature.board

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class CatalogErrorsTest {
    @Test
    fun `no network reads as offline`() {
        assertThat(UnknownHostException("a.4cdn.org").catalogFailure()).isEqualTo(CatalogFailure.OFFLINE)
        assertThat(IOException("wrapped", UnknownHostException()).catalogFailure()).isEqualTo(CatalogFailure.OFFLINE)
    }

    @Test
    fun `a timeout reads as slow`() {
        assertThat(SocketTimeoutException().catalogFailure()).isEqualTo(CatalogFailure.SLOW)
    }

    @Test
    fun `http status codes read as what they mean`() {
        assertThat(IllegalStateException("HTTP 429").catalogFailure()).isEqualTo(CatalogFailure.RATE_LIMITED)
        assertThat(IllegalStateException("HTTP 404").catalogFailure()).isEqualTo(CatalogFailure.GONE)
    }

    @Test
    fun `anything else falls back to the general message`() {
        assertThat(IllegalStateException("parse").catalogFailure()).isEqualTo(CatalogFailure.OTHER)
    }
}
