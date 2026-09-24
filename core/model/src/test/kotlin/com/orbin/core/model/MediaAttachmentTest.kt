package com.orbin.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MediaAttachmentTest {
    @Test
    fun `preview aspect ratio comes from the full media dimensions`() {
        val attachment = attachment(width = 1600, height = 900, thumbnailWidth = 250, thumbnailHeight = 250)

        assertThat(attachment.previewAspectRatio).isWithin(0.001f).of(16f / 9f)
    }

    @Test
    fun `preview aspect ratio falls back to the thumbnail dimensions`() {
        assertThat(attachment(thumbnailWidth = 125, thumbnailHeight = 250).previewAspectRatio)
            .isWithin(0.001f)
            .of(0.5f)
    }

    @Test
    fun `preview aspect ratio is zero when no dimensions are known`() {
        assertThat(attachment().previewAspectRatio).isEqualTo(0f)
    }

    private fun attachment(
        width: Int = 0,
        height: Int = 0,
        thumbnailWidth: Int = 0,
        thumbnailHeight: Int = 0,
    ) = MediaAttachment(
        id = "1",
        originalFileName = "a.png",
        extension = "png",
        type = MediaType.IMAGE,
        sourceUrl = "https://example.org/a.png",
        thumbnailUrl = "https://example.org/a_s.jpg",
        width = width,
        height = height,
        thumbnailWidth = thumbnailWidth,
        thumbnailHeight = thumbnailHeight,
    )
}
