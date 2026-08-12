package com.orbin.data.repository

import com.google.common.truth.Truth.assertThat
import com.orbin.core.model.BoardId
import com.orbin.core.model.HistoryEntry
import com.orbin.core.model.PostId
import com.orbin.core.model.ProviderId
import com.orbin.core.model.ThreadId
import com.orbin.core.model.ThreadKey
import com.orbin.data.database.dao.HistoryDao
import com.orbin.data.database.entity.HistoryEntity
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test

class HistoryRepositoryImplTest {
    private val key = ThreadKey(ProviderId("fourchan"), BoardId("g"), ThreadId(123))
    private val dao = mockk<HistoryDao>(relaxUnitFun = true)
    private val repository = HistoryRepositoryImpl(dao)

    @Test
    fun `record preserves an existing scroll position instead of resetting it`() =
        runTest {
            val existing = HistoryEntity(
                provider = "fourchan",
                board = "g",
                thread = 123,
                title = "old title",
                thumbnailUrl = null,
                lastVisitedMillis = 1,
                lastReadPostId = 999,
                lastReadOffsetPx = 480,
            )
            coEvery { dao.getEntry("fourchan", "g", 123) } returns existing
            coEvery { dao.upsert(any()) } returns Unit

            repository.record(
                HistoryEntry(
                    key = key,
                    title = "new title",
                    lastVisitedMillis = 2,
                    // A fresh load only ever knows the OP; the previous scroll anchor must survive.
                    lastReadPostId = PostId(456),
                ),
            )

            coVerify {
                dao.upsert(
                    HistoryEntity(
                        provider = "fourchan",
                        board = "g",
                        thread = 123,
                        title = "new title",
                        thumbnailUrl = null,
                        lastVisitedMillis = 2,
                        lastReadPostId = 999,
                        lastReadOffsetPx = 480,
                    ),
                )
            }
        }

    @Test
    fun `record on a first-ever visit stores the given scroll anchor unchanged`() =
        runTest {
            coEvery { dao.getEntry("fourchan", "g", 123) } returns null
            coEvery { dao.upsert(any()) } returns Unit

            repository.record(
                HistoryEntry(
                    key = key,
                    title = "new title",
                    lastVisitedMillis = 2,
                    lastReadPostId = PostId(456),
                ),
            )

            coVerify {
                dao.upsert(
                    HistoryEntity(
                        provider = "fourchan",
                        board = "g",
                        thread = 123,
                        title = "new title",
                        thumbnailUrl = null,
                        lastVisitedMillis = 2,
                        lastReadPostId = 456,
                        lastReadOffsetPx = 0,
                    ),
                )
            }
        }

    @Test
    fun `updateScrollPosition delegates to the dao`() =
        runTest {
            repository.updateScrollPosition(key, PostId(789), 120)

            coVerify { dao.updateScrollPosition("fourchan", "g", 123, 789, 120) }
        }

    @Test
    fun `getEntry maps the dao row to a domain entry`() =
        runTest {
            coEvery { dao.getEntry("fourchan", "g", 123) } returns
                HistoryEntity(
                    provider = "fourchan",
                    board = "g",
                    thread = 123,
                    title = "title",
                    thumbnailUrl = null,
                    lastVisitedMillis = 5,
                    lastReadPostId = 456,
                    lastReadOffsetPx = 10,
                )

            val entry = repository.getEntry(key)

            assertThat(entry)
                .isEqualTo(
                    HistoryEntry(
                        key = key,
                        title = "title",
                        lastVisitedMillis = 5,
                        lastReadPostId = PostId(456),
                        lastReadOffsetPx = 10,
                    ),
                )
        }
}
