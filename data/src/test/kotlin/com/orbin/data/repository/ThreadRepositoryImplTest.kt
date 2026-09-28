package com.orbin.data.repository

import com.google.common.truth.Truth.assertThat
import com.orbin.core.common.result.OrbinResult
import com.orbin.core.model.BoardId
import com.orbin.core.model.Post
import com.orbin.core.model.PostId
import com.orbin.core.model.ProviderId
import com.orbin.core.model.Thread
import com.orbin.core.model.ThreadId
import com.orbin.core.model.ThreadKey
import com.orbin.core.model.ThreadStats
import com.orbin.domain.usecase.BuildReplyGraphUseCase
import com.orbin.provider.api.ImageBoardProvider
import com.orbin.provider.api.ProviderRegistry
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.io.IOException

/**
 * Reopening a thread shows the copy from the last visit at once and then the thread as it is now:
 * the cache alone once hid every reply posted in the half hour it kept a thread.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ThreadRepositoryImplTest {
    private val providerId = ProviderId("fourchan")
    private val key = ThreadKey(providerId, BoardId("g"), ThreadId(7))
    private val provider = mockk<ImageBoardProvider>()
    private val registry =
        mockk<ProviderRegistry> {
            every { get(providerId) } returns provider
        }
    private val repository = ThreadRepositoryImpl(registry, BuildReplyGraphUseCase(), UnconfinedTestDispatcher())

    private fun thread(replies: Int) =
        Thread(
            key = key,
            originalPost = Post(id = PostId(7), board = key.board, threadId = key.thread, isOriginalPost = true),
            stats = ThreadStats(replyCount = replies),
        )

    private fun replyCounts(results: List<OrbinResult<Thread>>) =
        results.map { (it as OrbinResult.Success).data.stats.replyCount }

    @Test
    fun `a first visit loads from the network once`() =
        runTest {
            coEvery { provider.getThread(key.board, key.thread) } returns thread(replies = 3)

            assertThat(replyCounts(repository.observeThread(key).toList())).containsExactly(3)
        }

    @Test
    fun `a revisit shows the cached copy and then the replies posted since`() =
        runTest {
            coEvery { provider.getThread(key.board, key.thread) } returns thread(replies = 3)
            repository.observeThread(key).toList()
            coEvery { provider.getThread(key.board, key.thread) } returns thread(replies = 9)

            assertThat(replyCounts(repository.observeThread(key).toList())).containsExactly(3, 9).inOrder()
        }

    @Test
    fun `a revisit that cannot reach the site keeps the cached copy`() =
        runTest {
            coEvery { provider.getThread(key.board, key.thread) } returns thread(replies = 3)
            repository.observeThread(key).toList()
            coEvery { provider.getThread(key.board, key.thread) } throws IOException("offline")

            assertThat(replyCounts(repository.observeThread(key).toList())).containsExactly(3)
        }

    @Test
    fun `a pull to refresh goes straight to the network`() =
        runTest {
            coEvery { provider.getThread(key.board, key.thread) } returns thread(replies = 3)
            repository.observeThread(key).toList()
            coEvery { provider.getThread(key.board, key.thread) } returns thread(replies = 9)

            assertThat(replyCounts(repository.observeThread(key, forceRefresh = true).toList())).containsExactly(9)
        }
}
