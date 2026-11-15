package com.orbin.data.offline

import com.orbin.core.model.BoardId
import com.orbin.core.model.InlineStyle
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
import com.orbin.data.database.dao.OfflineThreadCopyDao
import com.orbin.data.database.dao.OfflineThreadCopyInfo
import com.orbin.data.database.entity.OfflineThreadCopyEntity
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class OfflineThreadCopiesTest {
    private val key = ThreadKey(ProviderId("fourchan"), BoardId("g"), ThreadId(100))

    @Test
    fun lastLoadedCopyRetainsRichThreadAndReversibleCoverMetadata() =
        runTest {
            val dao = FakeCopies().apply { owners += key }
            val copies = OfflineThreadCopies(dao, StandardTestDispatcher(testScheduler))
            val thread = richThread(key)
            copies.keep(thread)
            assertEquals(thread, copies.read(key))
        }

    @Test
    fun privateSessionOnlyVisitsNeverReachPersistentCopies() =
        runTest {
            val dao = FakeCopies()
            val copies = OfflineThreadCopies(dao, StandardTestDispatcher(testScheduler))
            copies.keep(richThread(key))
            assertEquals(0, dao.data.size)
            assertNull(copies.read(key))
        }

    @Test
    fun providerIdentitySeparatesCollidingBoardsAndPruningKeepsOnlyOwners() =
        runTest {
            val other = key.copy(provider = ProviderId("other"))
            val dao = FakeCopies().apply { owners += listOf(key, other) }
            val copies = OfflineThreadCopies(dao, StandardTestDispatcher(testScheduler))
            copies.keep(richThread(key))
            copies.keep(
                richThread(other).let { it.copy(originalPost = it.originalPost.copy(subject = "Other source")) },
            )
            assertEquals("Other source", copies.read(other)?.subject)
            assertEquals("Offline subject", copies.read(key)?.subject)
            copies.retain(setOf(other))
            assertNull(copies.read(key))
            assertEquals("Other source", copies.read(other)?.subject)
        }

    @Test
    fun oversizedReplacementLeavesPriorCopyIntactAndCountIsBounded() =
        runTest {
            val dao = FakeCopies().apply { owners += key }
            val copies =
                OfflineThreadCopies(dao, StandardTestDispatcher(testScheduler), maxCopies = 2, maxCopyBytes = 10_000)
            val initial = richThread(key)
            copies.keep(initial)
            copies.keep(
                initial.copy(
                    originalPost =
                        initial.originalPost.copy(
                            comment = PostComment("x".repeat(20_000), persistentListOf()),
                        ),
                ),
            )
            assertEquals(initial, copies.read(key))
            for (id in 101L..102L) {
                val next = key.copy(thread = ThreadId(id))
                dao.owners += next
                copies.keep(richThread(next))
            }
            assertEquals(2, dao.data.size)
        }

    @Test
    fun corruptOrMismatchedCopiesDoNotCrashTheOfflineReader() =
        runTest {
            val dao = FakeCopies().apply { owners += key }
            val copies = OfflineThreadCopies(dao, StandardTestDispatcher(testScheduler))
            copies.keep(richThread(key))
            val original = dao.data.getValue(key)
            dao.data[key] = original.copy(payload = "not-json")
            assertNull(copies.read(key))
            dao.data[key] = original.copy(payload = original.payload.replace("fourchan", "different"))
            assertNull(copies.read(key))
        }

    private fun richThread(key: ThreadKey): Thread =
        Thread(
            key,
            Post(
                PostId(100),
                key.board,
                key.thread,
                true,
                "Offline subject",
                PostComment(
                    "raw",
                    persistentListOf(
                        PostNode.Styled(InlineStyle.SPOILER, persistentListOf(PostNode.Text("hidden"))),
                        PostNode.LineBreak,
                        PostNode.Link("https://example.org", persistentListOf(PostNode.Text("link"))),
                        PostNode.QuoteLink(PostId(101), BoardId("x"), isOp = false, isDead = true),
                    ),
                ),
                PosterInfo("Name", "trip", "poster", "Mod", "GB", "United Kingdom"),
                1234,
                persistentListOf(
                    MediaAttachment(
                        "file",
                        "clip",
                        ".webm",
                        MediaType.VIDEO,
                        "https://example.org/clip.webm",
                        "https://example.org/thumb.jpg",
                        640,
                        480,
                        80,
                        60,
                        999,
                        true,
                        2345,
                        sourceIsSpoiler = false,
                        appCoverReasons = setOf("nsfw"),
                    ),
                ),
                backlinks = persistentListOf(PostId(101)),
            ),
            persistentListOf(
                Post(PostId(101), key.board, key.thread, false, repliesTo = persistentListOf(PostId(100))),
            ),
            ThreadStats(1, 1, 2, true, true, true, 5678),
        )
}

private class FakeCopies : OfflineThreadCopyDao {
    val owners = mutableSetOf<ThreadKey>()
    val data = linkedMapOf<ThreadKey, OfflineThreadCopyEntity>()

    private fun key(
        provider: String,
        board: String,
        thread: Long,
    ) = ThreadKey(ProviderId(provider), BoardId(board), ThreadId(thread))

    override suspend fun get(
        provider: String,
        board: String,
        thread: Long,
    ) = data[key(provider, board, thread)]

    override suspend fun entries() =
        data.values
            .sortedBy {
                it.updatedAtMillis
            }.map { OfflineThreadCopyInfo(it.provider, it.board, it.thread, it.updatedAtMillis, it.sizeBytes) }

    override suspend fun hasPersistentOwner(
        provider: String,
        board: String,
        thread: Long,
    ) = key(provider, board, thread) in owners

    override suspend fun upsert(copy: OfflineThreadCopyEntity) {
        data[key(copy.provider, copy.board, copy.thread)] =
            copy
    }

    override suspend fun delete(
        provider: String,
        board: String,
        thread: Long,
    ) {
        data.remove(key(provider, board, thread))
    }
}
