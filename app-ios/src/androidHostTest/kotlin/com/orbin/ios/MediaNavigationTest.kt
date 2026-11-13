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

    @Test
    fun aKnownSelectionIsAvailableBeforeTheThreadArrives() {
        val route = Route.Media(thread, 0, reply.id, reply.thumbnailUrl, initialAttachment = reply)
        val initial = route.viewerFiles(emptyList())
        assertEquals(listOf(reply), initial)
        assertEquals(0, route.startIndexIn(initial))
    }

    @Test
    fun threadExpansionRetainsTheSeedKeyWhenTheProviderUsesADifferentAttachmentId() {
        val route = Route.Media(thread, 0, reply.id, reply.thumbnailUrl, initialAttachment = reply)
        val initialKey = mediaViewerKeys(route.viewerFiles(emptyList())).single()
        val updated = reply.copy(id = "full-thread-reply", originalFileName = "updated.jpg")
        val expanded = route.viewerFiles(listOf(first, updated))
        assertEquals(2, expanded.size)
        assertEquals(1, route.startIndexIn(expanded))
        assertEquals(reply.sourceUrl, expanded[1].sourceUrl)
        assertEquals("updated.jpg", expanded[1].originalFileName)
        assertEquals(initialKey, mediaViewerKeys(expanded)[1])
    }

    @Test
    fun anUnavailableSeedSourceStaysSelectedInsteadOfAnUnrelatedMatchingIdOrThumbnail() {
        val route = Route.Media(thread, 0, reply.id, reply.thumbnailUrl, initialAttachment = reply)
        val unrelated = reply.copy(sourceUrl = "https://media.example/unrelated.jpg")
        val expanded = route.viewerFiles(listOf(first, unrelated))
        assertEquals(reply, expanded.first())
        assertEquals(0, route.startIndexIn(expanded))
        assertEquals(reply.sourceUrl, expanded[route.startIndexIn(expanded)!!].sourceUrl)
    }

    @Test
    fun aMismatchedOrUnsafeSeedCannotBypassExplicitSelection() {
        val mismatchedId = Route.Media(thread, 0, "different-id", reply.thumbnailUrl, initialAttachment = reply)
        val mismatchedThumbnail = Route.Media(thread, 0, reply.id, first.thumbnailUrl, initialAttachment = reply)
        val unsafe = reply.copy(sourceUrl = "javascript:alert(1)")
        val unsafeRoute = Route.Media(thread, 0, unsafe.id, unsafe.thumbnailUrl, initialAttachment = unsafe)
        val filtered = reply.copy(originalFileName = "gore.mp4")
        val filteredRoute = Route.Media(thread, 0, filtered.id, filtered.thumbnailUrl, initialAttachment = filtered)
        listOf(mismatchedId, mismatchedThumbnail, unsafeRoute, filteredRoute).forEach { route ->
            assertEquals(emptyList<MediaAttachment>(), route.viewerFiles(emptyList()))
            assertNull(route.startIndexIn(listOf(reply)))
        }
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
