package com.orbin.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GeneratedMediaCoversTest {
    @Test
    fun removingOneCoverPreservesOtherCoverAndRestoresOriginalWhenBothAreOff() {
        val original = attachment(false)
        val covered = original.withGeneratedCover(APP_COVER_NSFW, true).withGeneratedCover(APP_COVER_VIOLENCE, true)
        assertTrue(covered.isSpoiler)
        assertEquals(false, covered.sourceIsSpoiler)
        val violenceOnly = covered.withGeneratedCover(APP_COVER_NSFW, false)
        assertTrue(violenceOnly.isSpoiler)
        assertEquals(setOf(APP_COVER_VIOLENCE), violenceOnly.appCoverReasons)
        assertEquals(original, violenceOnly.withGeneratedCover(APP_COVER_VIOLENCE, false))
    }

    @Test
    fun removingGeneratedCoversNeverRevealsAnOriginalProviderSpoiler() {
        val original = attachment(true)
        val covered = original.withGeneratedCover(APP_COVER_NSFW, true)
        assertEquals(original, covered.withGeneratedCover(APP_COVER_NSFW, false))
        assertTrue(covered.withGeneratedCover(APP_COVER_NSFW, false).isSpoiler)
        assertFalse(attachment(false).withGeneratedCover(APP_COVER_NSFW, false).isSpoiler)
    }

    private fun attachment(spoiler: Boolean) =
        MediaAttachment(
            id = "1",
            originalFileName = "image.jpg",
            extension = "jpg",
            type = MediaType.IMAGE,
            sourceUrl = "https://example.org/image.jpg",
            thumbnailUrl = "https://example.org/thumb.jpg",
            isSpoiler = spoiler,
        )
}
