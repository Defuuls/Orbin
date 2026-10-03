package com.orbin.data.repository

import com.orbin.core.model.DownloadOrganization
import com.orbin.network.NetworkConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DownloadRepositoryTest {
    @Test
    fun downloadRequestHeadersReceivesExpectedHeaders() {
        val headers = downloadRequestHeaders("https://i.4cdn.org/g/1234567890123.jpg?cache=1#preview")

        assertEquals(NetworkConfig.DEFAULT_USER_AGENT, headers["User-Agent"])
        assertEquals("image/avif,image/webp,image/*,video/*,audio/*,*/*;q=0.8", headers["Accept"])
        assertEquals("https://i.4cdn.org/", headers["Referer"])
    }

    @Test
    fun refererIsOmittedForNonHttps() {
        val invalidHeaders = downloadRequestHeaders("not a url")
        assertFalse(invalidHeaders.containsKey("Referer"))

        val httpHeaders = downloadRequestHeaders("http://i.4cdn.org/g/123.jpg")
        assertFalse(httpHeaders.containsKey("Referer"))
    }

    @Test
    fun sanitizeFileNameRemovesUnsafeCharacters() {
        assertEquals("test_file.png", sanitizeFileName("test/file.png"))
        assertEquals("download", sanitizeFileName("///"))
        assertEquals("image.jpg", sanitizeFileName("..\\..\\image.jpg"))
    }

    @Test
    fun sanitizePathSegmentCleansProperly() {
        assertEquals("g", sanitizePathSegment("g"))
        assertEquals("thread_name", sanitizePathSegment("thread/name"))
        assertEquals("misc", sanitizePathSegment("  ..  "))
    }

    @Test
    fun buildRelativeDirBuildsExpectedPaths() {
        assertEquals(
            "g/123 - Thread title/",
            buildRelativeDir(DownloadOrganization.BY_BOARD_THEN_THREAD, "g", 123L, "Thread title"),
        )
        assertEquals(
            "",
            buildRelativeDir(DownloadOrganization.FLAT, "g", 123L, "Thread title"),
        )
        assertEquals(
            "g/",
            buildRelativeDir(DownloadOrganization.BY_BOARD, "g", 123L, "Thread title"),
        )
        assertEquals(
            "123 - Thread title/",
            buildRelativeDir(DownloadOrganization.BY_THREAD, "g", 123L, "Thread title"),
        )
    }
}
