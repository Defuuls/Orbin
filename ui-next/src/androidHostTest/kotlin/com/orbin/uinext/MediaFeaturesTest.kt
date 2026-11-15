package com.orbin.uinext

import com.orbin.uinext.components.MediaKind
import com.orbin.uinext.components.MediaKindFilter
import com.orbin.uinext.components.ReverseImageSearchEngine
import com.orbin.uinext.components.mediaFilterIndices
import com.orbin.uinext.components.mediaKindOf
import com.orbin.uinext.components.mediaSearchTarget
import com.orbin.uinext.components.next
import com.orbin.uinext.components.nextMediaPageAfterCompletion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URI
import java.net.URLDecoder

class MediaFeaturesTest {
    @Test
    fun filtersRetainCanonicalIndicesAndKeepAudioAndUnknownInAll() {
        val kinds = listOf(MediaKind.IMAGE, MediaKind.VIDEO, MediaKind.AUDIO, MediaKind.IMAGE, MediaKind.UNKNOWN)
        assertEquals(listOf(0, 1, 2, 3, 4), mediaFilterIndices(kinds, MediaKindFilter.ALL))
        assertEquals(listOf(0, 3), mediaFilterIndices(kinds, MediaKindFilter.IMAGES))
        assertEquals(listOf(1), mediaFilterIndices(kinds, MediaKindFilter.VIDEOS))
        assertEquals(MediaKind.IMAGE, mediaKindOf("ANIMATED_IMAGE"))
    }

    @Test
    fun allSearchEnginesEncodeTheWholeImageUrlAndUseHttps() {
        val source = "https://media.example/猫.png?token=a&name=b#fragment"
        ReverseImageSearchEngine.entries.forEach { engine ->
            val url = checkNotNull(engine.searchUrl(source))
            val uri = URI(url)
            assertEquals("https", uri.scheme)
            assertTrue(URLDecoder.decode(uri.rawQuery, "UTF-8").contains(source))
            assertNull(uri.rawFragment)
        }
        assertEquals(
            "https://media.example/preview.jpg",
            mediaSearchTarget("https://media.example/video.webm", "https://media.example/preview.jpg", MediaKind.VIDEO),
        )
        assertNull(mediaSearchTarget("https://media.example/audio.mp3", "", MediaKind.AUDIO))
    }

    @Test
    fun searchesRejectUnsafeUrlsAndNeverFallBackToVideoBytes() {
        listOf(
            "javascript:alert(1)",
            "http://media.example/a",
            "https://u:p@media.example/a",
            "https:///a",
            "https://a/b\n",
        ).forEach {
            assertNull(ReverseImageSearchEngine.GOOGLE_LENS.searchUrl(it))
        }
        assertNull(mediaSearchTarget("https://media.example/video.webm", "", MediaKind.VIDEO))
    }

    @Test
    fun completionAdvancesOnlyTheActiveSettledForegroundPageAndNeverPastTheEnd() {
        fun next(
            key: String = "a",
            enabled: Boolean = true,
            foreground: Boolean = true,
            scrolling: Boolean = false,
            index: Int = 0,
        ) = nextMediaPageAfterCompletion(key, "a", index, 2, enabled, foreground, scrolling)
        assertEquals(1, next())
        assertNull(next(key = "replaced"))
        assertNull(next(enabled = false))
        assertNull(next(foreground = false))
        assertNull(next(scrolling = true))
        assertNull(next(index = 1))
        assertNull(next(index = -1))
    }
}
