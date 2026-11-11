package com.orbin.core.reader

import com.orbin.core.model.BoardId
import com.orbin.core.model.CatalogThread
import com.orbin.core.model.MediaAttachment
import com.orbin.core.model.MediaType
import com.orbin.core.model.Post
import com.orbin.core.model.PostComment
import com.orbin.core.model.PostId
import com.orbin.core.model.PostNode
import com.orbin.core.model.ProviderId
import com.orbin.core.model.ThreadId
import com.orbin.core.model.ThreadKey
import com.orbin.core.model.ThreadStats
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class CatalogPresentationTest {
    @Test
    fun preparationDispatchesBeforeFormattingAndKeepsOriginalModelForActions() =
        runTest {
            val workerScheduler = testScheduler
            val thread = thread()
            val pending =
                async(start = CoroutineStart.UNDISPATCHED) {
                    prepareCatalogThread(
                        thread,
                        nowMillis = 121_000,
                        dispatcher = StandardTestDispatcher(workerScheduler),
                    )
                }

            assertFalse(pending.isCompleted)
            workerScheduler.runCurrent()
            val prepared = pending.await()
            assertSame(thread, prepared.thread)
            assertEquals("2m", prepared.row.activity)
            assertEquals("First line", prepared.row.subject)
            assertEquals("First line\nSecond line", prepared.row.excerpt)
            assertTrue(prepared.row.sticky)
            assertTrue(prepared.row.closed)
        }

    @Test
    fun preparedRowsKeepSitesSeparateAndLeaveReadStateForTheHost() {
        val thread = thread()
        val first = thread.catalogRow(nowMillis = 121_000)
        val other = thread.copy(key = thread.key.copy(provider = ProviderId("other"))).catalogRow(121_000)

        assertEquals("site/g/1", first.id)
        assertNotEquals(first.id, other.id)
        assertFalse(first.read)
        assertEquals(0, first.unread)
        assertEquals("First line", first.copy(read = true, unread = 4).subject)
    }

    @Test
    fun blankTitlesAndInvalidCountsHaveStableFallbacks() {
        val thread =
            thread().copy(
                originalPost = thread().originalPost.copy(subject = " ", comment = PostComment.Empty),
                stats = ThreadStats(replyCount = -4, imageCount = -1),
            )

        val row = thread.catalogRow(nowMillis = 0)

        assertEquals("No.1", row.subject)
        assertEquals(0, row.replies)
        assertEquals(0, row.media)
        assertFalse(row.hasPreview)
        assertEquals(0f, row.mediaAspectRatio)
    }

    @Test
    fun catalogSpoilerOwnershipFollowsOnlyTheDisplayedFirstAttachment() {
        val preview =
            MediaAttachment(
                id = "preview",
                originalFileName = "preview.jpg",
                extension = "jpg",
                type = MediaType.IMAGE,
                sourceUrl = "https://example.invalid/original.jpg",
                thumbnailUrl = "https://example.invalid/preview.jpg",
                isSpoiler = true,
            )
        val base = thread()
        val covered = base.copy(originalPost = base.originalPost.copy(attachments = persistentListOf(preview)))
        assertTrue(covered.catalogRow(0).imageSpoiler)
        val laterSpoiler =
            covered.copy(
                originalPost =
                    covered.originalPost.copy(
                        attachments = persistentListOf(preview.copy(isSpoiler = false), preview.copy(id = "second")),
                    ),
            )
        assertFalse(laterSpoiler.catalogRow(0).imageSpoiler)
        assertFalse(base.catalogRow(0).imageSpoiler)
    }

    private fun thread(): CatalogThread {
        val key = ThreadKey(ProviderId("site"), BoardId("g"), ThreadId(1))
        return CatalogThread(
            key,
            Post(
                PostId(1),
                key.board,
                key.thread,
                isOriginalPost = true,
                createdAtMillis = 1_000,
                comment = PostComment("", persistentListOf(PostNode.Text("First line\nSecond line"))),
            ),
            ThreadStats(replyCount = 2, isSticky = true, isClosed = true),
        )
    }
}
