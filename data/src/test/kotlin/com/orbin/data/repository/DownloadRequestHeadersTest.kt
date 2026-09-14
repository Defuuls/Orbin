package com.orbin.data.repository

import com.google.common.truth.Truth.assertThat
import com.orbin.network.NetworkConfig
import org.junit.Test

class DownloadRequestHeadersTest {
    @Test
    fun `4cdn media receives configured headers`() {
        val headers =
            downloadRequestHeaders(
                url = "https://i.4cdn.org/g/1234567890123.jpg?cache=1#preview",
                configuredUserAgent = "Custom Agent",
            )

        assertThat(headers)
            .containsExactly(
                "User-Agent",
                "Custom Agent",
                "Accept",
                "image/avif,image/webp,image/*,video/*,audio/*,*/*;q=0.8",
                "Referer",
                "https://i.4cdn.org/",
            ).inOrder()
    }

    @Test
    fun `blank configured user agent uses safe default`() {
        val headers = downloadRequestHeaders("https://i.4cdn.org/g/123.jpg", "")

        assertThat(headers["User-Agent"]).isEqualTo(NetworkConfig.DEFAULT_USER_AGENT)
    }

    @Test
    fun `referer is omitted for invalid or non https urls`() {
        assertThat(downloadRequestHeaders("not a url", "Agent")).doesNotContainKey("Referer")
        assertThat(downloadRequestHeaders("http://i.4cdn.org/g/123.jpg", "Agent")).doesNotContainKey("Referer")
    }
}
