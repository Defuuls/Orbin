package com.orbin.feature.board

import com.google.common.truth.Truth.assertThat
import com.orbin.provider.api.ProviderException
import com.orbin.uinext.ReaderFailureCause
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
        assertThat(IllegalStateException("HTTP 503").catalogFailure()).isEqualTo(CatalogFailure.SERVER)
    }

    @Test
    fun `anything else falls back to the general message`() {
        assertThat(IllegalStateException("parse").catalogFailure()).isEqualTo(CatalogFailure.OTHER)
    }

    @Test
    fun `typed provider failures retain their cause without parsing server messages`() {
        assertThat(ProviderException.NotFound("missing").catalogFailure()).isEqualTo(CatalogFailure.GONE)
        assertThat(ProviderException.RateLimited().catalogFailure()).isEqualTo(CatalogFailure.RATE_LIMITED)
        assertThat(
            ProviderException.Http(502, "upstream unavailable").catalogFailure(),
        ).isEqualTo(CatalogFailure.SERVER)
        assertThat(ProviderException.Network("connection failed").catalogFailure()).isEqualTo(CatalogFailure.OFFLINE)
        assertThat(
            ProviderException.Parse("post 404 cannot be decoded").catalogFailure(),
        ).isEqualTo(CatalogFailure.OTHER)
    }

    @Test
    fun `presentation categories name the underlying cause`() {
        assertThat(CatalogFailure.OFFLINE.readerCause).isEqualTo(ReaderFailureCause.NETWORK)
        assertThat(CatalogFailure.SLOW.readerCause).isEqualTo(ReaderFailureCause.NETWORK)
        assertThat(CatalogFailure.GONE.readerCause).isEqualTo(ReaderFailureCause.NOT_FOUND)
        assertThat(CatalogFailure.SERVER.readerCause).isEqualTo(ReaderFailureCause.SERVER)
        assertThat(CatalogFailure.RATE_LIMITED.readerCause).isEqualTo(ReaderFailureCause.SERVER)
        assertThat(CatalogFailure.OTHER.readerCause).isEqualTo(ReaderFailureCause.UNKNOWN)
    }

    @Test
    fun `cause cycles cannot stall the error state`() {
        val first = IllegalStateException("first")
        val second = IllegalStateException("second", first)
        first.initCause(second)

        assertThat(first.catalogFailure()).isEqualTo(CatalogFailure.OTHER)
    }
}
