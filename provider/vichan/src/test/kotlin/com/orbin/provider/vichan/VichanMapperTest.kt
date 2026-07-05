package com.orbin.provider.vichan

import com.google.common.truth.Truth.assertThat
import com.orbin.core.model.BoardId
import com.orbin.core.model.MediaType
import com.orbin.provider.vichan.api.VichanPost
import com.orbin.provider.vichan.api.VichanThreadResponse
import org.junit.Test

class VichanMapperTest {
    private val mapper = VichanMapper(VichanSite.Example)
    private val board = BoardId("g")

    @Test
    fun `fourchan media urls are built from tim and ext`() {
        val op =
            VichanPost(
                no = 1,
                resto = 0,
                time = 100,
                tim = "1690000000000",
                filename = "cat",
                ext = ".jpg",
                w = 800,
                h = 600,
            )
        val thread = mapper.mapThread(board, VichanThreadResponse(listOf(op)))
        val media = thread.originalPost.attachments.single()

        assertThat(media.sourceUrl).isEqualTo("https://i.4cdn.org/g/1690000000000.jpg")
        assertThat(media.thumbnailUrl).isEqualTo("https://i.4cdn.org/g/1690000000000s.jpg")
        assertThat(media.type).isEqualTo(MediaType.IMAGE)
        assertThat(media.originalFileName).isEqualTo("cat.jpg")
    }

    @Test
    fun `webm is classified as video`() {
        val op = VichanPost(no = 1, time = 100, tim = "999", filename = "clip", ext = ".webm")
        val media =
            mapper
                .mapThread(board, VichanThreadResponse(listOf(op)))
                .originalPost.attachments
                .single()
        assertThat(media.type).isEqualTo(MediaType.VIDEO)
    }

    @Test
    fun `thread maps op and replies in order with stats`() {
        val op = VichanPost(no = 1000, resto = 0, time = 1, replies = 2, images = 1, sticky = 1)
        val r1 = VichanPost(no = 1001, resto = 1000, time = 2, com = "first")
        val r2 = VichanPost(no = 1002, resto = 1000, time = 3, com = "second")

        val thread = mapper.mapThread(board, VichanThreadResponse(listOf(op, r1, r2)))

        assertThat(thread.originalPost.isOriginalPost).isTrue()
        assertThat(thread.replies.map { it.id.value }).containsExactly(1001L, 1002L).inOrder()
        assertThat(thread.stats.replyCount).isEqualTo(2)
        assertThat(thread.stats.isSticky).isTrue()
        assertThat(thread.originalPost.createdAtMillis).isEqualTo(1000L)
    }

    @Test
    fun `subject and poster name decode html entities`() {
        val op =
            VichanPost(
                no = 1,
                resto = 0,
                time = 1,
                sub = "Cat&#039;s &quot;toy&quot;",
                name = "Bob &amp; Alice",
            )
        val thread = mapper.mapThread(board, VichanThreadResponse(listOf(op)))

        assertThat(thread.originalPost.subject).isEqualTo("Cat's \"toy\"")
        assertThat(thread.originalPost.poster.name).isEqualTo("Bob & Alice")
    }

    @Test
    fun `board title and description decode html entities`() {
        val boards =
            mapper.mapBoards(
                com.orbin.provider.vichan.api.VichanBoardsResponse(
                    boards =
                        listOf(
                            com.orbin.provider.vichan.api.VichanBoard(
                                board = "g",
                                title = "Tech &amp; Gadgets",
                                metaDescription = "Discuss &quot;technology&quot;",
                            ),
                        ),
                ),
            )
        val g = boards.single()
        assertThat(g.title).isEqualTo("Tech & Gadgets")
        assertThat(g.description).isEqualTo("Discuss \"technology\"")
    }

    @Test
    fun `nsfw board is detected from ws_board flag`() {
        val boards =
            mapper.mapBoards(
                com.orbin.provider.vichan.api.VichanBoardsResponse(
                    boards =
                        listOf(
                            com.orbin.provider.vichan.api
                                .VichanBoard(board = "g", title = "Technology", workSafe = 1),
                            com.orbin.provider.vichan.api
                                .VichanBoard(board = "b", title = "Random", workSafe = 0),
                        ),
                ),
            )
        assertThat(boards.first { it.id.value == "g" }.isNsfw).isFalse()
        assertThat(boards.first { it.id.value == "b" }.isNsfw).isTrue()
    }
}
