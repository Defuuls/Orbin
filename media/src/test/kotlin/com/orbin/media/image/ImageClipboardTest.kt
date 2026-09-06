package com.orbin.media.image

import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ImageClipboardTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

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

    @Test
    fun `purge drops files older than a day and caps remaining count`() {
        val dir = tempFolder.newFolder("clipboard_images")
        val now = 1_700_000_000_000L
        val day = 24L * 60L * 60L * 1000L

        fun touch(
            name: String,
            ageMs: Long,
        ): File =
            File(dir, name).also {
                it.writeText("x")
                it.setLastModified(now - ageMs)
            }

        touch("old.webp", day + 1)
        val keep = (0 until 10).map { touch("keep$it.webp", it * 1_000L) }

        purgeClipboardCache(dir, nowMillis = now)

        val remaining =
            dir
                .listFiles()
                ?.map { it.name }
                ?.toSet()
                .orEmpty()
        assertThat(remaining).doesNotContain("old.webp")
        assertThat(remaining).hasSize(8)
        assertThat(remaining).containsAtLeastElementsIn(keep.take(8).map { it.name })
    }
}
