package com.orbin.feature.gallery

import com.orbin.core.model.BoardId
import com.orbin.core.model.MediaAttachment
import com.orbin.core.model.MediaType
import com.orbin.core.model.ProviderId
import com.orbin.core.model.ThreadId
import com.orbin.core.model.ThreadKey
import com.orbin.domain.media.AllMediaItem
import com.orbin.uinext.components.MediaKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AllMediaCellPresentationTest {
    @Test
    fun matchingProviderFileIdsKeepSeparateSourceKeysAndActualAttachmentMetadata() {
        val attachment =
            MediaAttachment(
                id = "same-file",
                originalFileName = "actual.webm",
                extension = ".webm",
                type = MediaType.VIDEO,
                sourceUrl = "https://example.org/actual.webm",
                thumbnailUrl = "https://example.org/actual.jpg",
                width = 1920,
                height = 1080,
                sizeBytes = 2_097_152,
                isSpoiler = true,
            )
        val first =
            AllMediaItem(
                attachment,
                ThreadKey(ProviderId("fourchan"), BoardId("g"), ThreadId(7)),
                "Technology",
                "Actual thread",
            )
        val other = first.copy(key = first.key.copy(provider = ProviderId("bbwchan")))
        val cell = first.toCell()
        assertNotEquals(cell.id, other.toCell().id)
        assertEquals("fourchan/g/7/same-file", cell.id)
        assertEquals("fourchan /g/", cell.board)
        assertEquals("bbwchan /g/", other.toCell().board)
        assertEquals("WEBM 1920x1080 2 MB", cell.details)
        assertEquals(MediaKind.VIDEO, cell.kind)
        assertTrue(cell.spoiler)
    }

    @Test
    fun allAttachmentTypesReachMediaFilterCellsWithoutChangingTheirIdentity() {
        val attachment = MediaAttachment("file", "saved", "", MediaType.IMAGE, "https://example.org/file", "")
        val item =
            AllMediaItem(
                attachment,
                ThreadKey(ProviderId("fourchan"), BoardId("g"), ThreadId(7)),
                "Technology",
                "Thread",
            )
        listOf(
            MediaType.IMAGE to MediaKind.IMAGE,
            MediaType.ANIMATED_IMAGE to MediaKind.IMAGE,
            MediaType.VIDEO to MediaKind.VIDEO,
            MediaType.AUDIO to MediaKind.AUDIO,
            MediaType.UNKNOWN to MediaKind.UNKNOWN,
        ).forEach { (type, kind) ->
            val cell = item.copy(attachment = attachment.copy(type = type)).toCell()
            assertEquals(kind, cell.kind)
            assertEquals("fourchan/g/7/file", cell.id)
        }
    }
}
