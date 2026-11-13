package com.orbin.ios

import com.orbin.core.model.BoardId
import com.orbin.core.model.MediaAttachment
import com.orbin.core.model.MediaType
import com.orbin.core.model.ProviderId
import com.orbin.core.model.ThreadId
import com.orbin.core.model.ThreadKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MediaNavigationTest {
    private val thread = ThreadKey(ProviderId("source"), BoardId("g"), ThreadId(7))
    private val first = file("first", "https://media.example/first-thumb.jpg")
    private val reply = file("reply", "https://media.example/reply-thumb.jpg")
    private val files = listOf(first, reply)

    @Test
    fun anExactAttachmentWinsOverAStalePreview() {
        val route = Route.Media(thread, 0, attachmentId = reply.id, thumbnailUrl = first.thumbnailUrl)
        assertEquals(1, route.startIndexIn(files))
    }

    @Test
    fun aCatalogThumbnailCanResolveADifferentFullThreadId() {
        val route = Route.Media(thread, 0, attachmentId = "catalog-preview-id", thumbnailUrl = reply.thumbnailUrl)
        assertEquals(1, route.startIndexIn(files))
        assertEquals(1, Route.Media(thread, 0, thumbnailUrl = reply.thumbnailUrl).startIndexIn(files))
        assertNull(Route.Media(thread, 0, thumbnailUrl = "https://media.example/missing.jpg").startIndexIn(files))
    }

    @Test
    fun anUnavailableExplicitSelectionNeverFallsBackToTheFirstFile() {
        assertNull(Route.Media(thread, 0, attachmentId = "removed").startIndexIn(files))
        assertNull(Route.Media(thread, 0, "removed", "https://media.example/missing.jpg").startIndexIn(files))
    }

    @Test
    fun aSharedPlaceholderThumbnailCannotIdentifyOneOfSeveralFiles() {
        val duplicate = reply.copy(id = "other-reply")
        assertNull(Route.Media(thread, 0, "preview", reply.thumbnailUrl).startIndexIn(listOf(reply, duplicate)))
        assertNull(Route.Media(thread, 0, "preview", "").startIndexIn(listOf(reply.copy(thumbnailUrl = ""))))
    }

    @Test
    fun indexBasedThreadEntryRetainsItsClampingBehavior() {
        assertEquals(0, Route.Media(thread, -1).startIndexIn(files))
        assertEquals(1, Route.Media(thread, 50).startIndexIn(files))
        assertNull(Route.Media(thread, 0).startIndexIn(emptyList()))
    }

    private fun file(
        id: String,
        thumbnail: String,
    ) = MediaAttachment(
        id = id,
        originalFileName = "$id.jpg",
        extension = "jpg",
        type = MediaType.IMAGE,
        sourceUrl = "https://media.example/$id.jpg",
        thumbnailUrl = thumbnail,
    )
}
