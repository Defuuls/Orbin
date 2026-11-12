package com.orbin.core.reader

import com.orbin.core.model.BoardId
import com.orbin.core.model.MediaAttachment
import com.orbin.core.model.MediaType
import com.orbin.core.model.Post
import com.orbin.core.model.PostComment
import com.orbin.core.model.PostId
import com.orbin.core.model.PostNode
import com.orbin.core.model.PosterInfo
import com.orbin.core.model.ProviderId
import com.orbin.core.model.Thread
import com.orbin.core.model.ThreadId
import com.orbin.core.model.ThreadKey
import com.orbin.core.model.ThreadStats
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ThreadPresentationTest {
    @Test
    fun bothHostsReceiveMetadataAndQuoteDerivedRepliesFromTheSameMapper() =
        runTest {
            val op =
                post(1).copy(
                    poster = PosterInfo("Name", "!trip", "abc", "Mod", "GB", "United Kingdom"),
                    createdAtMillis = 1_000L,
                    attachments = persistentListOf(file("first")),
                )
            val reply = post(2).copy(repliesTo = persistentListOf(PostId(1), PostId(1), PostId(99)))
            val nested = post(3).copy(repliesTo = persistentListOf(PostId(2)))
            val thread = Thread(key, op, persistentListOf(reply, nested), ThreadStats(replyCount = 2))

            val index = thread.preparePresentation(nowMillis = 5_000L, fromSavedCopy = true)

            assertSame(op, index.rows.first().post)
            assertEquals("Name · !trip · ID: abc · Mod · United Kingdom", index.rowModels.first().author)
            assertTrue(
                index.rowModels
                    .first()
                    .time
                    .isNotBlank(),
            )
            assertEquals("first.jpg (2 KB)", index.rowModels.first().fileLabel)
            assertEquals(listOf(0, 0, 1), index.rowModels.map { it.depth })
            assertEquals(listOf(1, 1, 0), index.rowModels.map { it.replies })
            assertEquals(listOf("2"), index.rowModels.first().replyNumbers)
            assertEquals(listOf("3"), index.rowModels[1].replyNumbers)
            assertTrue(index.fromSavedCopy)
            assertEquals(5_000L, index.preparedAtMillis)
            assertEquals("site/g/1", index.rowModels.first().stableKey)
            val other = thread.copy(key = key.copy(provider = ProviderId("other"))).preparePresentation()
            assertNotEquals(index.rowModels.first().stableKey, other.rowModels.first().stableKey)
        }

    @Test
    fun duplicateProviderFileIdsStillOpenTheCorrectGlobalViewerPage() =
        runTest {
            val op = post(1).copy(attachments = persistentListOf(file("same"), file("same")))
            val reply = post(3).copy(attachments = persistentListOf(file("same")))
            val thread = Thread(key, op, persistentListOf(post(2), reply))

            val index = thread.preparePresentation()

            assertEquals(
                3,
                index.fileCells
                    .map { it.id }
                    .toSet()
                    .size,
            )
            assertEquals(listOf(0, 1, 2), index.fileCells.map { index.mediaIndex[it.id] })
            assertEquals(listOf(PostId(1), PostId(1), PostId(3)), (0..2).map(index::postIdForMediaIndex))
            assertEquals(0, index.firstMediaIndex("1"))
            assertEquals(2, index.firstMediaIndex("3"))
            assertNull(index.firstMediaIndex("2"))
            assertNull(index.firstMediaIndex("unknown"))
            listOf(Int.MIN_VALUE, -1, 3, Int.MAX_VALUE).forEach { assertNull(index.postIdForMediaIndex(it)) }
            index.fileCells.forEach { assertEquals("same", index.attachmentsById[it.id]?.id) }
        }

    @Test
    fun invalidOptionalValuesDoNotLeakNegativeLabelsOrHidePostText() =
        runTest {
            val op =
                post(1).copy(
                    createdAtMillis = -1,
                    attachments = persistentListOf(file("spoiler").copy(sizeBytes = -1, isSpoiler = true)),
                    comment = PostComment("", persistentListOf(PostNode.Text("Readable"))),
                )
            val index = Thread(key, op, stats = ThreadStats(replyCount = -5)).preparePresentation()
            val row = index.rowModels.single()

            assertEquals("", row.time)
            assertEquals("spoiler.jpg", row.fileLabel)
            assertEquals("Readable", row.body)
            assertFalse(row.spoiler)
            assertTrue(row.imageSpoiler)
            assertTrue(index.fileCells.single().spoiler)
            assertEquals("", index.lastUpdateText)
            assertEquals(0, index.replyCount)
        }

    @Test
    fun preparationDispatchesBeforeMappingAndReusesOnlyTheSameSnapshot() =
        runTest {
            val workerScheduler = testScheduler
            val worker = StandardTestDispatcher(workerScheduler)
            val thread = Thread(key, post(1))
            val pending = async(start = CoroutineStart.UNDISPATCHED) { thread.preparePresentation(dispatcher = worker) }
            assertFalse(pending.isCompleted, "preparation must dispatch off the caller before traversing posts")
            workerScheduler.runCurrent()
            val prepared = pending.await()
            assertEquals(thread, prepared.thread)

            val cache = ThreadPresentationCache()
            val immediate = UnconfinedTestDispatcher(testScheduler)
            val first = cache.prepare(thread, dispatcher = immediate)
            assertSame(first, cache.prepare(thread.copy(), dispatcher = immediate))
            val saved = cache.prepare(thread, fromSavedCopy = true, dispatcher = immediate)
            assertNotEquals(first.fromSavedCopy, saved.fromSavedCopy)
            assertSame(saved, cache.prepare(thread, fromSavedCopy = true, dispatcher = immediate))
        }

    @Test
    fun cancelledPreparationCannotReplaceThePublishedCacheRevision() =
        runTest {
            val cache = ThreadPresentationCache()
            val thread = Thread(key, post(1))
            val immediate = UnconfinedTestDispatcher(testScheduler)
            val first = cache.prepare(thread, dispatcher = immediate)
            val workerScheduler = testScheduler
            val pending =
                async(start = CoroutineStart.UNDISPATCHED) {
                    cache.prepare(
                        thread.copy(originalPost = post(2)),
                        dispatcher = StandardTestDispatcher(workerScheduler),
                    )
                }
            pending.cancel()
            workerScheduler.runCurrent()

            assertSame(first, cache.prepare(thread, dispatcher = immediate))
        }

    @Test
    fun visiblePreparationCachesPolicyAndFormattingForAnIdenticalLoad() =
        runTest {
            var walks = 0
            val nodes =
                object : ImmutableList<PostNode> by persistentListOf(PostNode.Text("Visible")) {
                    override fun iterator(): Iterator<PostNode> {
                        walks++
                        return persistentListOf(PostNode.Text("Visible")).iterator()
                    }
                }
            val thread = Thread(key, post(1).copy(comment = PostComment("", nodes)))
            val cache = ThreadPresentationCache()
            val worker = StandardTestDispatcher(testScheduler)
            val firstLoad = async { cache.prepareVisible(thread, dispatcher = worker) }
            testScheduler.runCurrent()
            val first = firstLoad.await()
            assertTrue(walks > 0)
            val initialWalks = walks
            val secondLoad = async { cache.prepareVisible(thread, dispatcher = worker) }
            testScheduler.runCurrent()

            assertSame(first, secondLoad.await())
            assertEquals(initialWalks, walks, "policy and rich-text walks must both be cached")
        }

    @Test
    fun onlyWellFormedHttpsLinksCanLeaveTheReader() {
        assertEquals("https://example.com/path", safeExternalLink(" https://example.com/path "))
        assertEquals("HTTPS://example.com", safeExternalLink("HTTPS://example.com"))
        listOf(
            "http://example.com",
            "https://",
            "https:///path",
            "https://example.com/a b",
            "https://example.com\\@evil.test",
            "javascript:alert(1)",
            "data:text/plain,hi",
            "file:///tmp/file",
            "intent://example",
            "www.example.com",
            "",
        ).forEach { assertNull(safeExternalLink(it), it) }
    }

    private fun post(id: Long) = Post(PostId(id), key.board, key.thread, isOriginalPost = id == 1L)

    private fun file(id: String) =
        MediaAttachment(
            id,
            "$id.jpg",
            "jpg",
            MediaType.IMAGE,
            "https://example.com/$id",
            "https://example.com/thumb/$id",
            sizeBytes = 2_048,
        )

    private companion object {
        val key = ThreadKey(ProviderId("site"), BoardId("g"), ThreadId(1))
    }
}
