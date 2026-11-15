package com.orbin.feature.gallery

import com.orbin.core.model.AppSettings
import com.orbin.core.model.BoardId
import com.orbin.core.model.MediaAttachment
import com.orbin.core.model.MediaType
import com.orbin.core.model.Post
import com.orbin.core.model.PostId
import com.orbin.core.model.ProviderId
import com.orbin.core.model.Thread
import com.orbin.core.model.ThreadId
import com.orbin.core.model.ThreadKey
import com.orbin.core.model.ThreadLayout
import com.orbin.core.model.ThreadMemory
import kotlinx.collections.immutable.persistentListOf
import org.junit.Assert.assertEquals
import org.junit.Test

class GalleryMediaVisibilityTest {
    private val key = ThreadKey(ProviderId("fourchan"), BoardId("g"), ThreadId(1))
    private val first = MediaAttachment("reused", "one", "jpg", MediaType.IMAGE, "https://example.test/one.jpg", "")
    private val second = MediaAttachment("reused", "two", "jpg", MediaType.IMAGE, "https://example.test/two.jpg", "")
    private val thread =
        Thread(
            key,
            Post(PostId(1), key.board, key.thread, true),
            persistentListOf(
                Post(PostId(2), key.board, key.thread, false, attachments = persistentListOf(first)),
                Post(
                    PostId(3),
                    key.board,
                    key.thread,
                    false,
                    attachments = persistentListOf(second),
                    repliesTo = persistentListOf(PostId(2)),
                ),
            ),
        )
    private val ready =
        GalleryContentState.Ready(
            persistentListOf(first, second),
            persistentListOf(first, second),
            persistentListOf(0, 1),
            persistentListOf("post2:reused", "post3:reused"),
            sourceThread = thread,
        )

    @Test
    fun hidingKeepsCanonicalIndicesAndKeysWhenAttachmentIdsAreReused() {
        val filtered =
            ready.withPostVisibility(
                AppSettings(),
                ThreadMemory(key, hiddenPostIds = setOf(2)),
                explicitlyRequestedIndex = 1,
            )
        assertEquals(listOf(second), filtered.media)
        assertEquals(listOf(1), filtered.threadMediaIndices)
        assertEquals(listOf("post3:reused"), filtered.pageKeys)
        assertEquals(ready.threadMedia, filtered.threadMedia)
        assertEquals(
            "Hidden owners must never leak through download-rest",
            emptyList<MediaAttachment>(),
            galleryRemainingMedia(filtered, 0),
        )
    }

    @Test
    fun aFoldedBranchStaysOutOfTheGalleryButFlatFoldingKeepsItsOtherPosts() {
        val memory = ThreadMemory(key, foldedPaths = setOf("fourchan/g/1/2"))
        assertEquals(emptyList<MediaAttachment>(), ready.withPostVisibility(AppSettings(), memory, null).media)
        assertEquals(
            listOf(second),
            ready.withPostVisibility(AppSettings(threadLayout = ThreadLayout.FLAT), memory, null).media,
        )
    }

    @Test
    fun hidingOnAnotherSiteDoesNotHideTheSamePostNumbersHere() {
        val elsewhere = ThreadMemory(key.copy(provider = ProviderId("bbwchan")), hiddenPostIds = setOf(2, 3))
        assertEquals(ready.media, ready.withPostVisibility(AppSettings(), elsewhere, null).media)
    }
}
