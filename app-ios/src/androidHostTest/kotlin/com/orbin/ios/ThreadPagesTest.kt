package com.orbin.ios

import com.orbin.core.model.BoardId
import com.orbin.core.model.Post
import com.orbin.core.model.PostId
import com.orbin.core.model.ProviderId
import com.orbin.core.model.Thread
import com.orbin.core.model.ThreadId
import com.orbin.core.model.ThreadKey
import com.orbin.domain.repository.ThreadArchive
import com.orbin.domain.repository.ThreadArchiveRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class ThreadPagesTest {
    private val key = ThreadKey(ProviderId("4chan"), BoardId("g"), ThreadId(1))
    private val thread = Thread(key, Post(PostId(1), key.board, key.thread, isOriginalPost = true))

    @Test
    fun aSavedPageGoesToTheFolderAndThenTheShareSheet() =
        runTest {
            val shared = mutableListOf<String>()
            val pages =
                ThreadPages(
                    archive =
                        FakeArchive {
                            _,
                            folder,
                            ->
                            ThreadArchive("$folder/orbin-g-1.zip", "orbin-g-1.zip", 0, 0)
                        },
                    folder = "/docs/Orbin/Threads",
                    onSaved = { shared += it.path },
                )
            pages.save(thread)
            assertEquals(listOf("/docs/Orbin/Threads/orbin-g-1.zip"), shared)
            assertFalse(pages.saving.value)
        }

    @Test
    fun aFailedSaveSharesNothingAndCanBeTriedAgain() =
        runTest {
            val pages = ThreadPages(FakeArchive { _, _ -> error("disk full") }, "/docs", onSaved = { error("shared") })
            assertNull(pages.save(thread))
            assertFalse(pages.saving.value)
        }

    private class FakeArchive(
        val result: (Thread, String?) -> ThreadArchive,
    ) : ThreadArchiveRepository {
        override suspend fun archive(
            thread: Thread,
            directory: String?,
            onProgress: (done: Int, total: Int) -> Unit,
        ): ThreadArchive = result(thread, directory)
    }
}
