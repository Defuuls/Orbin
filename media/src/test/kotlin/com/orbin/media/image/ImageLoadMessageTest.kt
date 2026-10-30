package com.orbin.media.image

import coil3.network.HttpException
import coil3.network.NetworkResponse
import com.google.common.truth.Truth.assertThat
import com.orbin.media.hasHttpStatus
import com.orbin.media.httpStatusCode
import org.junit.Test
import java.io.IOException

class ImageLoadMessageTest {
    private fun refusal(code: Int) = HttpException(NetworkResponse(code = code))

    @Test
    fun `a Coil 429 is a rate limit`() {
        assertThat(refusal(429).hasHttpStatus(429)).isTrue()
        assertThat(refusal(429).imageLoadMessage()).isEqualTo("Image rate limited")
    }

    @Test
    fun `a Coil 429 wrapped as a cause is still a rate limit`() {
        val wrapped = IOException("decode failed", refusal(429))

        assertThat(wrapped.httpStatusCode()).isEqualTo(429)
        assertThat(wrapped.imageLoadMessage()).isEqualTo("Image rate limited")
    }

    @Test
    fun `other Coil statuses are not a rate limit`() {
        listOf(403, 404, 500, 503).forEach { code ->
            assertThat(refusal(code).httpStatusCode()).isEqualTo(code)
            assertThat(refusal(code).imageLoadMessage()).isEqualTo("Image unavailable")
        }
    }

    /** 4chan file names are millisecond timestamps, so "429" turns up in URLs all the time. */
    @Test
    fun `a 429 inside a url or message is not a rate limit`() {
        val unreachable = IOException("Unable to resolve host i.4cdn.org/g/1698765429123s.jpg")
        val refusedElsewhere = IOException("HTTP 429", refusal(404))

        assertThat(unreachable.httpStatusCode()).isNull()
        assertThat(unreachable.imageLoadMessage()).isEqualTo("Image unavailable")
        assertThat(refusedElsewhere.imageLoadMessage()).isEqualTo("Image unavailable")
    }

    @Test
    fun `the outermost status wins when causes disagree`() {
        val outer = HttpException(NetworkResponse(code = 503))
        outer.initCause(refusal(429))

        assertThat(outer.httpStatusCode()).isEqualTo(503)
    }
}
