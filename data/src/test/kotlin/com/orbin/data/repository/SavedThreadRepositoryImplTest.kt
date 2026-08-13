package com.orbin.data.repository

import com.google.common.truth.Truth.assertThat
import com.orbin.core.model.BoardId
import com.orbin.core.model.MediaAttachment
import com.orbin.core.model.Post
import com.orbin.core.model.PostComment
import com.orbin.core.model.PostId
import com.orbin.core.model.PosterInfo
import com.orbin.core.model.ProviderId
import com.orbin.core.model.Thread
import com.orbin.core.model.ThreadId
import com.orbin.core.model.ThreadKey
import com.orbin.data.database.dao.SavedThreadDao
import com.orbin.data.database.entity.SavedPostEntity
import com.orbin.data.database.entity.SavedThreadEntity
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.slot
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SavedThreadRepositoryImplTest {
    private val dao = mockk<SavedThreadDao>(relaxUnitFun = true)
    private val repository = SavedThreadRepositoryImpl(dao, UnconfinedTestDispatcher())

    private val key = ThreadKey(ProviderId("fourchan"), BoardId("g"), ThreadId(42))

    private fun post(
        id: Long,
        isOp: Boolean,
        comment: String,
        attachments: List<MediaAttachment> = emptyList(),
    ) = Post(
        id = PostId(id),
        board = key.board,
        threadId = key.thread,
        isOriginalPost = isOp,
        subject = if (isOp) "A subject" else null,
        comment = PostComment(raw = comment, nodes = persistentListOf()),
        poster = PosterInfo(name = "Anonymous", posterId = "Ab3"),
        attachments = attachments.toImmutableList(),
    )

    private val thread =
        Thread(
            key = key,
            originalPost = post(1, isOp = true, comment = "opening post"),
            replies =
                persistentListOf(
                    post(2, isOp = false, comment = "first reply"),
                    post(3, isOp = false, comment = "second reply"),
                ),
        )

    @Test
    fun `save flattens the thread and every post`() =
        runTest {
            val savedThread = slot<SavedThreadEntity>()
            val savedPosts = slot<List<SavedPostEntity>>()
            coEvery { dao.save(capture(savedThread), capture(savedPosts)) } returns Unit

            repository.save(thread)

            assertThat(savedThread.captured.title).isEqualTo("A subject")
            assertThat(savedThread.captured.postCount).isEqualTo(3)
            assertThat(savedPosts.captured.map { it.comment })
                .containsExactly("opening post", "first reply", "second reply")
                .inOrder()
        }

    @Test
    fun `save records reading order so replies come back in sequence`() =
        runTest {
            val savedPosts = slot<List<SavedPostEntity>>()
            coEvery { dao.save(any(), capture(savedPosts)) } returns Unit

            repository.save(thread)

            assertThat(savedPosts.captured.map { it.sortIndex }).containsExactly(0, 1, 2).inOrder()
        }

    @Test
    fun `a thread with no subject is titled from its opening comment`() =
        runTest {
            val untitled =
                thread.copy(
                    originalPost = post(1, isOp = true, comment = "no subject here").copy(subject = null),
                )
            val savedThread = slot<SavedThreadEntity>()
            coEvery { dao.save(capture(savedThread), any()) } returns Unit

            repository.save(untitled)

            assertThat(savedThread.captured.title).isEqualTo("no subject here")
        }

    @Test
    fun `load rebuilds the thread in reading order`() =
        runTest {
            coEvery { dao.getSavedThread(any(), any(), any()) } returns savedThreadRow()
            coEvery { dao.getSavedPosts(any(), any(), any()) } returns
                listOf(
                    savedPostRow(1, isOp = true, comment = "opening post", sortIndex = 0),
                    savedPostRow(2, isOp = false, comment = "first reply", sortIndex = 1),
                )

            val loaded = repository.load(key)

            assertThat(loaded?.originalPost?.comment?.raw).isEqualTo("opening post")
            assertThat(loaded?.replies?.map { it.comment.raw }).containsExactly("first reply")
        }

    @Test
    fun `load returns null when nothing is saved`() =
        runTest {
            coEvery { dao.getSavedThread(any(), any(), any()) } returns null

            assertThat(repository.load(key)).isNull()
        }

    @Test
    fun `a saved thread with no posts reads as absent rather than failing`() =
        runTest {
            coEvery { dao.getSavedThread(any(), any(), any()) } returns savedThreadRow()
            coEvery { dao.getSavedPosts(any(), any(), any()) } returns emptyList()

            // A half-written copy should behave like one that was never taken.
            assertThat(repository.load(key)).isNull()
        }

    @Test
    fun `attachment links survive a save and load round trip`() =
        runTest {
            coEvery { dao.getSavedThread(any(), any(), any()) } returns savedThreadRow()
            coEvery { dao.getSavedPosts(any(), any(), any()) } returns
                listOf(
                    savedPostRow(
                        1,
                        isOp = true,
                        comment = "with media",
                        sortIndex = 0,
                        urls = "https://example.test/a.jpg\nhttps://example.test/b.png",
                        names = "a.jpg\nb.png",
                    ),
                )

            val attachments =
                repository
                    .load(key)
                    ?.originalPost
                    ?.attachments
                    .orEmpty()

            assertThat(attachments.map { it.sourceUrl })
                .containsExactly("https://example.test/a.jpg", "https://example.test/b.png")
                .inOrder()
            assertThat(attachments.map { it.originalFileName }).containsExactly("a.jpg", "b.png").inOrder()
        }

    private fun savedThreadRow() =
        SavedThreadEntity(
            provider = key.provider.value,
            board = key.board.value,
            thread = key.thread.value,
            title = "A subject",
            savedAtMillis = 1_000,
            postCount = 2,
        )

    private fun savedPostRow(
        postId: Long,
        isOp: Boolean,
        comment: String,
        sortIndex: Int,
        urls: String = "",
        names: String = "",
    ) = SavedPostEntity(
        provider = key.provider.value,
        board = key.board.value,
        thread = key.thread.value,
        postId = postId,
        isOriginalPost = isOp,
        subject = null,
        comment = comment,
        posterName = "Anonymous",
        posterTripcode = null,
        posterIdentifier = "Ab3",
        posterCapcode = null,
        createdAtMillis = 0,
        sortIndex = sortIndex,
        attachmentUrls = urls,
        attachmentNames = names,
    )
}
