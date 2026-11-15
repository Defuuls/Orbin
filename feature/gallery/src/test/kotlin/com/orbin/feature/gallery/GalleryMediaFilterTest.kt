package com.orbin.feature.gallery

import com.orbin.core.model.MediaAttachment
import com.orbin.core.model.MediaType
import com.orbin.uinext.MediaKindFilter
import kotlinx.collections.immutable.persistentListOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class GalleryMediaFilterTest {
    @Test
    fun imageFilteringKeepsCanonicalIndicesAndQualifiedKeysWhenProviderIdsRepeat() {
        val image = file("https://a.example/photo", MediaType.IMAGE)
        val video = file("https://b.example/video", MediaType.VIDEO)
        val audio = file("https://a.example/audio", MediaType.AUDIO)
        val gif = file("https://b.example/animation", MediaType.ANIMATED_IMAGE)
        val canonical = persistentListOf(image, video, audio, gif)
        val ready =
            GalleryContentState.Ready(
                media = persistentListOf(audio, image, video, gif),
                threadMedia = canonical,
                threadMediaIndices = persistentListOf(2, 0, 1, 3),
                pageKeys = persistentListOf("audio", "source-a:photo", "source-b:video", "source-b:gif"),
            )
        val images = ready.withMediaFilter(MediaKindFilter.IMAGES)
        assertEquals(listOf(image, gif), images.media)
        assertEquals(listOf(0, 3), images.threadMediaIndices)
        assertEquals(listOf("source-a:photo", "source-b:gif"), images.pageKeys)
        assertSame(canonical, images.threadMedia)
        assertEquals(3, images.threadMediaIndexAt(1))
        assertSame(ready, ready.withMediaFilter(MediaKindFilter.ALL))
        assertEquals(listOf(video), ready.withMediaFilter(MediaKindFilter.VIDEOS).media)
    }

    private fun file(
        url: String,
        type: MediaType,
    ) = MediaAttachment("reused-id", "file", "", type, url, "")
}
