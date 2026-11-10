package com.orbin.ios

import com.orbin.core.model.MediaAttachment
import com.orbin.core.model.MediaType
import kotlin.test.Test
import kotlin.test.assertEquals

class ThreadMediaTest {
    @Test
    fun sourceDimensionsReservePortraitAndLandscapeMediaBeforeDecoding() {
        assertEquals(0.5625f, file().copy(width = 1080, height = 1920).threadPreviewAspect())
        assertEquals(2f, file().copy(width = 1600, height = 800).threadPreviewAspect())
    }

    @Test
    fun thumbnailDimensionsWorkForProvidersWithoutSourceDimensions() {
        assertEquals(0.75f, file().copy(thumbnailWidth = 300, thumbnailHeight = 400).threadPreviewAspect())
        assertEquals(1f, file().threadPreviewAspect())
    }

    @Test
    fun extremeAttachmentsHaveBoundedFramesForUncroppedLetterboxing() {
        assertEquals(3f, file().copy(width = 12000, height = 100).threadPreviewAspect())
        assertEquals(0.5625f, file().copy(width = 100, height = 12000).threadPreviewAspect())
    }

    private fun file() =
        MediaAttachment(
            id = "attachment",
            originalFileName = "photo.jpg",
            extension = "jpg",
            type = MediaType.IMAGE,
            sourceUrl = "https://example.com/photo.jpg",
            thumbnailUrl = "https://example.com/thumbnail.jpg",
        )
}
