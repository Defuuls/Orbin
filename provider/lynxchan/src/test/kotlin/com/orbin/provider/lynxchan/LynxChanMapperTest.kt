package com.orbin.provider.lynxchan

import com.google.common.truth.Truth.assertThat
import com.orbin.core.model.BoardId
import com.orbin.core.model.MediaType
import com.orbin.core.model.ThreadId
import com.orbin.provider.api.ProviderContract
import com.orbin.provider.lynxchan.api.LynxChanBoard
import com.orbin.provider.lynxchan.api.LynxChanBoardsData
import com.orbin.provider.lynxchan.api.LynxChanBoardsResponse
import com.orbin.provider.lynxchan.api.LynxChanCatalogThread
import com.orbin.provider.lynxchan.api.LynxChanFile
import com.orbin.provider.lynxchan.api.LynxChanPost
import com.orbin.provider.lynxchan.api.LynxChanThreadResponse
import org.junit.Test
import java.time.Instant

class LynxChanMapperTest {
    private val mapper = LynxChanMapper(LynxChanSite.BbwChan)
    private val board = BoardId("bbw")

    @Test
    fun `board list maps uri, name and description`() {
        val boards =
            mapper.mapBoards(
                LynxChanBoardsResponse(
                    status = "ok",
                    data =
                        LynxChanBoardsData(
                            boards =
                                listOf(
                                    LynxChanBoard(boardUri = "bbw", boardName = "BBW Real", boardDescription = "Thick"),
                                ),
                        ),
                ),
            )
        ProviderContract.requireValidBoards(boards)
        val g = boards.single()
        assertThat(g.id.value).isEqualTo("bbw")
        assertThat(g.title).isEqualTo("BBW Real")
        assertThat(g.description).isEqualTo("Thick")
    }

    @Test
    fun `inactive boards are omitted like Orbin Minimal`() {
        val boards =
            mapper.mapBoards(
                LynxChanBoardsResponse(
                    data =
                        LynxChanBoardsData(
                            boards =
                                listOf(
                                    LynxChanBoard(boardUri = "bbw", boardName = "Active"),
                                    LynxChanBoard(boardUri = "old", boardName = "Inactive", inactive = true),
                                ),
                        ),
                ),
            )

        assertThat(boards.map { it.id.value }).containsExactly("bbw")
    }

    @Test
    fun `catalog thread maps subject, stats and thumbnail`() {
        val dto =
            LynxChanCatalogThread(
                threadId = 7,
                subject = "Begging Thread",
                markdown = "Hello",
                postCount = 400,
                fileCount = 182,
                pinned = true,
                cyclic = true,
                lastBump = "2026-07-05T00:50:41.956Z",
                thumb = "/.media/t_abc123",
                mime = "image/jpeg",
            )
        val threads = mapper.mapCatalog(board, listOf(dto))
        ProviderContract.requireValidCatalog(threads)
        val thread = threads.single()

        assertThat(thread.key.thread).isEqualTo(ThreadId(7))
        assertThat(thread.originalPost.subject).isEqualTo("Begging Thread")
        assertThat(thread.stats.replyCount).isEqualTo(400)
        assertThat(thread.stats.imageCount).isEqualTo(182)
        assertThat(thread.stats.isSticky).isTrue()
        val attachment = thread.originalPost.attachments.single()
        assertThat(attachment.thumbnailUrl).isEqualTo("https://bbw-chan.link/.media/t_abc123")
        assertThat(attachment.type).isEqualTo(MediaType.IMAGE)
    }

    @Test
    fun `catalog creation time is preferred with last bump fallback`() {
        val withCreation =
            mapper
                .mapCatalog(
                    board,
                    listOf(
                        LynxChanCatalogThread(
                            threadId = 1,
                            creation = "2026-01-01T00:00:00Z",
                            lastBump = "2026-02-01T00:00:00Z",
                        ),
                    ),
                ).single()
        assertThat(
            withCreation.originalPost.createdAtMillis,
        ).isEqualTo(Instant.parse("2026-01-01T00:00:00Z").toEpochMilli())

        val fallback =
            mapper
                .mapCatalog(
                    board,
                    listOf(
                        LynxChanCatalogThread(
                            threadId = 2,
                            lastBump = "2026-02-01T00:00:00Z",
                        ),
                    ),
                ).single()
        assertThat(
            fallback.originalPost.createdAtMillis,
        ).isEqualTo(Instant.parse("2026-02-01T00:00:00Z").toEpochMilli())
    }

    @Test
    fun `catalog thread with no thumb has no attachments`() {
        val dto = LynxChanCatalogThread(threadId = 1, subject = null)
        val threads = mapper.mapCatalog(board, listOf(dto))
        ProviderContract.requireValidCatalog(threads)
        assertThat(threads.single().originalPost.attachments).isEmpty()
    }

    @Test
    fun `absolute media urls are preserved`() {
        val response =
            LynxChanThreadResponse(
                threadId = 9,
                files =
                    listOf(
                        LynxChanFile(
                            path = "https://cdn.example.org/media/full.jpg",
                            thumb = "https://cdn.example.org/media/thumb.jpg",
                            mime = "image/jpeg",
                        ),
                    ),
            )

        val attachment =
            mapper
                .mapThread(board, response)
                .originalPost.attachments
                .single()
        assertThat(attachment.sourceUrl).isEqualTo("https://cdn.example.org/media/full.jpg")
        assertThat(attachment.thumbnailUrl).isEqualTo("https://cdn.example.org/media/thumb.jpg")
        assertThat(attachment.originalFileName).isEqualTo("full.jpg")
    }

    @Test
    fun `missing or unsafe media paths are ignored`() {
        val response =
            LynxChanThreadResponse(
                threadId = 10,
                files =
                    listOf(
                        LynxChanFile(path = null, mime = "image/jpeg"),
                        LynxChanFile(path = "javascript:alert(1)", mime = "image/jpeg"),
                        LynxChanFile(path = "/../secret", mime = "image/jpeg"),
                    ),
            )

        assertThat(mapper.mapThread(board, response).originalPost.attachments).isEmpty()
    }

    @Test
    fun `thread maps op, replies and files with absolute urls`() {
        val response =
            LynxChanThreadResponse(
                threadId = 7,
                boardUri = "bbw",
                subject = "Begging Thread",
                name = "BananaMan ##AMp2yG",
                id = null,
                markdown = "Okay guys",
                creation = "2020-01-19T06:55:59.440Z",
                pinned = true,
                cyclic = true,
                files =
                    listOf(
                        LynxChanFile(
                            originalName = "beggars.jpg",
                            path = "/.media/abc.jpg",
                            thumb = "/.media/t_abc",
                            mime = "image/jpeg",
                            size = 365351,
                            width = 1024,
                            height = 683,
                        ),
                    ),
                posts =
                    listOf(
                        LynxChanPost(
                            postId = 243308,
                            name = "Ooga Booga",
                            id = "64d164",
                            markdown = "Reply text",
                            creation = "2026-04-28T12:20:27.819Z",
                        ),
                    ),
                uniquePosters = 42,
            )

        val thread = mapper.mapThread(board, response)
        ProviderContract.requireValidThread(thread)

        assertThat(thread.originalPost.id.value).isEqualTo(7L)
        assertThat(thread.originalPost.poster.name).isEqualTo("BananaMan ##AMp2yG")
        assertThat(
            thread.originalPost.attachments
                .single()
                .sourceUrl,
        ).isEqualTo("https://bbw-chan.link/.media/abc.jpg")
        assertThat(thread.replies).hasSize(1)
        assertThat(
            thread.replies
                .single()
                .id.value,
        ).isEqualTo(243308L)
        assertThat(
            thread.replies
                .single()
                .poster.posterId,
        ).isEqualTo("64d164")
        assertThat(thread.stats.uniquePosterCount).isEqualTo(42)
        assertThat(thread.stats.replyCount).isEqualTo(1)
        assertThat(thread.stats.imageCount).isEqualTo(1)
        assertThat(thread.stats.isSticky).isTrue()
    }
}
