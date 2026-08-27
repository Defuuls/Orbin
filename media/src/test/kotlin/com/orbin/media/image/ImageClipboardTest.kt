package com.orbin.media.image

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ImageClipboardTest {
    @Test
    fun `content type decides the extension`() {
        assertThat(extensionFor("image/jpeg", "https://example.test/a")).isEqualTo("jpg")
        assertThat(extensionFor("image/png", "https://example.test/a")).isEqualTo("png")
        assertThat(extensionFor("image/webp", "https://example.test/a")).isEqualTo("webp")
    }

    /** Servers send parameters and inconsistent casing on this header; neither should matter. */
    @Test
    fun `content type is read past its parameters and casing`() {
        assertThat(extensionFor("IMAGE/JPEG; charset=binary", "https://example.test/a")).isEqualTo("jpg")
        assertThat(extensionFor(" image/png ", "https://example.test/a")).isEqualTo("png")
    }

    @Test
    fun `an unhelpful content type falls back to the url`() {
        assertThat(extensionFor(null, "https://example.test/file.PNG")).isEqualTo("png")
        assertThat(extensionFor("application/octet-stream", "https://example.test/file.gif"))
            .isEqualTo("gif")
    }

    /** A query string is not part of the filename, and imageboards append them freely. */
    @Test
    fun `a query string is stripped before the extension is read`() {
        assertThat(extensionFor(null, "https://example.test/file.webp?v=2")).isEqualTo("webp")
    }

    @Test
    fun `anything unrecognisable becomes img`() {
        assertThat(extensionFor(null, "https://example.test/file")).isEqualTo("img")
        assertThat(extensionFor(null, "https://example.test/file.thisisnotanextension"))
            .isEqualTo("img")
        assertThat(extensionFor(null, "https://example.test/archive.tar.gz")).isEqualTo("gz")
    }
}
