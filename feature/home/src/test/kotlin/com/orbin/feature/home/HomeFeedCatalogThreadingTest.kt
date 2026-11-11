package com.orbin.feature.home

import com.orbin.core.model.Board
import com.orbin.core.model.BoardId
import com.orbin.core.model.CatalogRequest
import com.orbin.core.model.CatalogThread
import com.orbin.core.model.HomeFeedMode
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
import com.orbin.provider.api.ImageBoardProvider
import com.orbin.provider.api.ProviderCapabilities
import com.orbin.provider.api.ProviderException
import com.orbin.provider.api.ProviderMetadata
import com.orbin.provider.api.ViolentMediaCoverProvider
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import com.orbin.core.model.Thread as ImageThread

class HomeFeedCatalogThreadingTest {
    @Test
    fun `post-network cover processing and permanent filters leave the controller thread responsive`() =
        runBlocking {
            val controllerThread = AtomicReference<Thread>()
            val workerThread = AtomicReference<Thread>()
            val controller =
                Executors
                    .newSingleThreadExecutor { Thread(it, "home-controller").also(controllerThread::set) }
                    .asCoroutineDispatcher()
            val worker =
                Executors
                    .newSingleThreadExecutor { Thread(it, "home-content-worker").also(workerThread::set) }
                    .asCoroutineDispatcher()
            val coverEntered = CountDownLatch(1)
            val releaseCover = CountDownLatch(1)
            val coverThread = AtomicReference<Thread>()
            val filterThreads = ConcurrentLinkedQueue<Thread>()
            val decorated =
                ViolentMediaCoverProvider(rawProvider(filterThreads)) {
                    coverThread.set(Thread.currentThread())
                    coverEntered.countDown()
                    check(releaseCover.await(10, TimeUnit.SECONDS)) { "Test did not release cover processing" }
                    true
                }
            val provider =
                object : ImageBoardProvider by decorated {
                    override suspend fun getCatalog(request: CatalogRequest): List<CatalogThread> =
                        decorated.getCatalog(request).also {
                            // Subsequent comment reads belong to SubscribedFeedLoader's permanent
                            // filter, after all provider decorators have returned.
                            filterThreads.clear()
                        }
                }
            val settings = FeedLoadSettings(hideNsfwBoards = false, coverViolentMedia = true)
            val inputs =
                FeedInputs(
                    "fourchan",
                    listOf(Board(BoardId("g"), "Technology")),
                    emptyMap(),
                    settings,
                    0,
                    HomeFeedMode.FOR_YOU,
                    null,
                    false,
                )
            val loader = HomeFeedCatalogLoader(worker)
            val result =
                async(controller) {
                    loader.prepare(settings)
                    loader
                        .load(
                            provider,
                            inputs,
                        ).first { it is SubscribedFeedUiState.Success && !it.isLoadingMore }
                        .also {
                            assertSame(controllerThread.get(), Thread.currentThread())
                        }
                }
            try {
                assertTrue("Provider's post-network cover phase must start", coverEntered.await(5, TimeUnit.SECONDS))
                val heartbeat = async(controller) { Thread.currentThread() }
                assertSame(controllerThread.get(), withTimeout(5_000) { heartbeat.await() })
                releaseCover.countDown()
                val loaded = withTimeout(5_000) { result.await() } as SubscribedFeedUiState.Success
                assertSame(workerThread.get(), coverThread.get())
                assertTrue("Permanent content filtering must read the post", filterThreads.isNotEmpty())
                filterThreads.forEach { assertSame(workerThread.get(), it) }
                val thread =
                    loaded.boards
                        .single()
                        .threads
                        .single()
                assertEquals(1L, thread.key.thread.value)
                assertTrue(
                    thread.originalPost.attachments
                        .single()
                        .isSpoiler,
                )
            } finally {
                releaseCover.countDown()
                result.cancelAndJoin()
                controller.close()
                worker.close()
            }
        }

    private fun rawProvider(readers: ConcurrentLinkedQueue<Thread>) =
        object : ImageBoardProvider {
            override val metadata = ProviderMetadata(ProviderId("fourchan"), "4chan", "https://example.org")
            override val capabilities = ProviderCapabilities()

            override suspend fun getBoards(): List<Board> = emptyList()

            override suspend fun getCatalog(request: CatalogRequest): List<CatalogThread> =
                withContext(Dispatchers.IO) {
                    listOf(thread(request, 1, "fight", readers), thread(request, 2, "gore", readers))
                }

            override suspend fun getThread(
                board: BoardId,
                thread: ThreadId,
            ): ImageThread = throw ProviderException.NotFound("unused")
        }

    private fun thread(
        request: CatalogRequest,
        id: Long,
        subject: String,
        readers: ConcurrentLinkedQueue<Thread>,
    ): CatalogThread {
        val nodes = persistentListOf<PostNode>(PostNode.Text("ordinary discussion"))
        val observed =
            object : ImmutableList<PostNode> by nodes {
                override fun isEmpty(): Boolean {
                    readers += Thread.currentThread()
                    return nodes.isEmpty()
                }

                override fun iterator(): Iterator<PostNode> {
                    readers += Thread.currentThread()
                    return nodes.iterator()
                }
            }
        return CatalogThread(
            ThreadKey(request.provider, request.board, ThreadId(id)),
            Post(
                id = PostId(id),
                board = request.board,
                threadId = ThreadId(id),
                isOriginalPost = true,
                subject = subject,
                comment = PostComment("ordinary discussion", observed),
                attachments =
                    persistentListOf(
                        MediaAttachment("clip", "clip.mp4", "mp4", MediaType.VIDEO, "https://example.org/clip.mp4", ""),
                    ),
            ),
            ThreadStats(),
        )
    }
}
