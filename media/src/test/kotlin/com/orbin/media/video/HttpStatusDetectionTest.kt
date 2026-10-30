package com.orbin.media.video

import android.net.Uri
import androidx.media3.common.PlaybackException
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.HttpDataSource
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class HttpStatusDetectionTest {
    private fun refusal(
        code: Int,
        url: String = "https://i.4cdn.org/g/1.webm",
    ) = HttpDataSource.InvalidResponseCodeException(
        code,
        "message",
        null,
        emptyMap(),
        DataSpec(Uri.parse(url)),
        ByteArray(0),
    )

    @Test
    fun `a 429 response is recognised`() {
        assertThat(refusal(429).hasHttpStatus(429)).isTrue()
    }

    @Test
    fun `a 429 wrapped as the cause of a playback error is recognised`() {
        val error =
            PlaybackException(
                "Source error",
                refusal(429),
                PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
            )

        assertThat(error.hasHttpStatus(429)).isTrue()
        assertThat(error.mediaLoadMessage()).isEqualTo("Video rate limited. Try again later.")
    }

    @Test
    fun `other statuses are not a rate limit`() {
        assertThat(refusal(404).hasHttpStatus(429)).isFalse()
        assertThat(refusal(503).hasHttpStatus(429)).isFalse()
    }

    /** 4chan file names are millisecond timestamps, so "429" turns up in URLs all the time. */
    @Test
    fun `a 429 inside a url or message is not a rate limit`() {
        val error =
            PlaybackException(
                "Source error",
                refusal(404, url = "https://i.4cdn.org/g/1698765429123.webm"),
                PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
            )
        val plain = IOException("Unable to resolve host i.4cdn.org/g/1698765429123.webm")

        assertThat(error.hasHttpStatus(429)).isFalse()
        assertThat(error.mediaLoadMessage()).isEqualTo("Video unavailable")
        assertThat(plain.hasHttpStatus(429)).isFalse()
        assertThat(IOException("Response code: 429").hasHttpStatus(429)).isFalse()
    }
}
