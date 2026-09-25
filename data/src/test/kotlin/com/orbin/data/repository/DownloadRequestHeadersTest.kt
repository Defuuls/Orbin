package com.orbin.data.repository

import com.google.common.truth.Truth.assertThat
import com.orbin.network.NetworkConfig
import org.junit.Test

class DownloadRequestHeadersTest {
    @Test
    fun `4cdn media receives orbin headers`() {
        val headers = downloadRequestHeaders(url = "https://i.4cdn.org/g/1234567890123.jpg?cache=1#preview")

        assertThat(headers)
            .containsExactly(
                "User-Agent",
                NetworkConfig.DEFAULT_USER_AGENT,
                "Accept",
                "image/avif,image/webp,image/*,video/*,audio/*,*/*;q=0.8",
                "Referer",
                "https://i.4cdn.org/",
            ).inOrder()
    }

    @Test
    fun `referer is omitted for invalid or non https urls`() {
        assertThat(downloadRequestHeaders("not a url")).doesNotContainKey("Referer")
        assertThat(downloadRequestHeaders("http://i.4cdn.org/g/123.jpg")).doesNotContainKey("Referer")
    }
}
