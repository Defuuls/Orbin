package com.orbin.media.video

import com.google.common.truth.Truth.assertThat
import com.orbin.core.model.MediaAttachment
import com.orbin.core.model.MediaType
import org.junit.Test

class FeedAutoplayTest {
    @Test
    fun `a visible video autoplays when the setting is on`() {
        assertThat(canAutoplayInFeed(attachment(MediaType.VIDEO), autoplayEnabled = true)).isTrue()
    }

    @Test
    fun `nothing autoplays when the setting is off`() {
        assertThat(canAutoplayInFeed(attachment(MediaType.VIDEO), autoplayEnabled = false)).isFalse()
    }

    /**
     * The defect this function exists for: the full client's feed checked the type but not the
     * spoiler, so a spoilered video played itself in a scrolling feed — revealing the file without
     * the tap that a spoiler asks for.
     */
    @Test
    fun `a spoilered video never autoplays`() {
        val spoilered = attachment(MediaType.VIDEO, isSpoiler = true)
        assertThat(canAutoplayInFeed(spoilered, autoplayEnabled = true)).isFalse()
    }

    @Test
    fun `only video autoplays`() {
        val stillPlayable = listOf(MediaType.IMAGE, MediaType.ANIMATED_IMAGE, MediaType.AUDIO, MediaType.UNKNOWN)
        stillPlayable.forEach { type ->
            assertThat(canAutoplayInFeed(attachment(type), autoplayEnabled = true)).isFalse()
        }
    }

    private fun attachment(
        type: MediaType,
        isSpoiler: Boolean = false,
    ) = MediaAttachment(
        id = "1",
        originalFileName = "clip.webm",
        extension = "webm",
        type = type,
        sourceUrl = "https://example.test/clip.webm",
        thumbnailUrl = "https://example.test/clip.jpg",
        isSpoiler = isSpoiler,
    )
}
