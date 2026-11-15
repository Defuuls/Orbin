package com.orbin.core.reader

import com.orbin.core.model.AppSettings
import com.orbin.core.model.BoardId
import com.orbin.core.model.MediaAttachment
import com.orbin.core.model.MediaType
import com.orbin.core.model.Post
import com.orbin.core.model.PostComment
import com.orbin.core.model.PostId
import com.orbin.core.model.PostNode
import com.orbin.core.model.ProviderId
import com.orbin.core.model.Thread
import com.orbin.core.model.ThreadId
import com.orbin.core.model.ThreadKey
import com.orbin.core.model.ThreadMemory
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ThreadReaderProjectionTest {
    private val key = ThreadKey(ProviderId("fourchan"), BoardId("g"), ThreadId(1))

    private fun file(url: String) =
        MediaAttachment("reused", url, "jpg", MediaType.IMAGE, "https://example.test/$url.jpg", "")

    private fun post(
        id: Long,
        text: String = "Reply",
        parent: Long? = null,
    ) = Post(
        id = PostId(id),
        board = key.board,
        threadId = key.thread,
        isOriginalPost = id == 1L,
        comment = PostComment("", persistentListOf(PostNode.Text(text))),
        attachments = if (id == 1L) persistentListOf() else persistentListOf(file(id.toString())),
        repliesTo = parent?.let { persistentListOf(PostId(it)) } ?: persistentListOf(),
    )

    @Test
    fun aTemporaryUserRevealCannotRestorePostsRemovedByPermanentPolicy() =
        runTest {
            val thread = Thread(key, post(1, "Opening"), persistentListOf(post(2, "gore"), post(3)))
            val index = assertNotNull(ThreadPresentationCache().prepareVisible(thread))
            val rows = index.readerRows(nested = true, revealedPostIds = setOf(2))

            assertFalse(rows.any { it.id == "2" })
            assertEquals(listOf("1", "3"), rows.map { it.id })
            assertEquals(
                listOf(0),
                index.visibleMediaIndices(AppSettings(), ThreadMemory(key), revealedPostIds = setOf(2)),
            )
            assertEquals(PostId(3), index.postIdForMediaIndex(0))
        }

    @Test
    fun aLongConversationBuildsOneGraphPerSnapshotAcrossSeenFoldAndFilterUpdates() =
        runTest {
            val chain = (2L..402L).map { id -> post(id, parent = (id - 1).takeIf { it > 1 }) }.toImmutableList()
            val cache = ThreadPresentationCache()
            val thread = Thread(key, post(1, "Opening"), chain)
            val index = assertNotNull(cache.prepareVisible(thread))
            val initial = index.readerRows(nested = true)
            val graph = index.replyGraph
            assertEquals(402, initial.size)
            assertEquals(400, initial.last().depth)
            val seen = index.readerRows(nested = true, unseenPostIds = setOf(402))
            assertTrue(seen.last().unseen)
            assertSame(graph, index.replyGraph)
            val folded = index.readerRows(nested = true, foldedPaths = setOf(initial[1].branchPath))
            assertEquals(listOf("1", "2"), folded.map { it.id })
            val hidden = index.readerRows(nested = true, hiddenPostIds = setOf(2))
            assertEquals(listOf("1", "2"), hidden.map { it.id })
            assertEquals("Hidden by you", hidden.last().hiddenReason)
            assertEquals(402, index.readerRows(nested = false, foldedPaths = setOf(initial[1].branchPath)).size)
            assertSame(graph, index.replyGraph)
            assertSame(index, cache.prepareVisible(thread))
            val revised =
                assertNotNull(
                    cache.prepareVisible(thread.copy(replies = (chain + post(403, parent = 402)).toImmutableList())),
                )
            assertEquals(403, revised.readerRows(nested = true).size)
            assertNotSame(graph, revised.replyGraph)
        }

    @Test
    fun offlineProvenanceSurvivesPolicyPreparationAndClearsOnFreshNetworkContent() =
        runTest {
            val cache = ThreadPresentationCache()
            val offline = Thread(key, post(1, "Opening"), isOfflineCopy = true)
            val saved = assertNotNull(cache.prepareVisible(offline))
            assertTrue(saved.fromSavedCopy)
            assertTrue(offline.preparePresentation().fromSavedCopy)
            val refreshed = assertNotNull(cache.prepareVisible(offline.copy(isOfflineCopy = false)))
            assertFalse(refreshed.fromSavedCopy)
        }

    @Test
    fun hidingAndFoldingPreserveExactFileOwnershipAndExplicitReveal() =
        runTest {
            val index =
                Thread(
                    key,
                    post(1, "Opening"),
                    persistentListOf(post(2), post(3, parent = 2)),
                ).preparePresentation()
            val folded = ThreadMemory(key, foldedPaths = setOf("fourchan/g/1/2"))

            assertEquals(emptyList(), index.visibleMediaIndices(AppSettings(), folded))
            assertEquals(listOf(1), index.visibleMediaIndices(AppSettings(), folded, revealedPostIds = setOf(3)))
            assertEquals(PostId(3), index.postIdForMediaIndex(1))
            val hidden = index.readerRows(nested = true, hiddenPostIds = setOf(2))
            assertEquals("Hidden by you", hidden.single { it.id == "2" }.hiddenReason)
            assertFalse(hidden.any { it.id == "3" })
        }
}
