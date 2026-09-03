package com.orbin.provider.api

import com.google.common.truth.Truth.assertThat
import com.orbin.core.model.Board
import com.orbin.core.model.BoardId
import com.orbin.core.model.MediaAttachment
import com.orbin.core.model.MediaType
import com.orbin.core.model.Post
import com.orbin.core.model.PostId
import com.orbin.core.model.ProviderId
import com.orbin.core.model.Thread
import com.orbin.core.model.ThreadId
import com.orbin.core.model.ThreadKey
import kotlinx.collections.immutable.persistentListOf
import org.junit.Test

class ProviderContractTest {
    @Test
    fun `duplicate boards are rejected`() {
        val boards = listOf(Board(BoardId("a"), "A"), Board(BoardId("a"), "Again"))
        assertThat(ProviderContract.validateBoards(boards)).contains("duplicate board id 'a'")
    }

    @Test
    fun `thread requires absolute safe-shaped media urls`() {
        val key = ThreadKey(ProviderId("test"), BoardId("a"), ThreadId(10))
        val op =
            Post(
                id = PostId(10),
                board = key.board,
                threadId = key.thread,
                isOriginalPost = true,
                attachments =
                    persistentListOf(
                        MediaAttachment(
                            id = "x",
                            originalFileName = "x.jpg",
                            extension = "jpg",
                            type = MediaType.IMAGE,
                            sourceUrl = "/relative.jpg",
                            thumbnailUrl = "https://example.test/thumb.jpg",
                        ),
                    ),
            )
        val errors = ProviderContract.validateThread(Thread(key, op))
        assertThat(errors).contains("post[0] attachment[0] sourceUrl is not absolute HTTP(S)")
    }

    @Test
    fun `valid thread passes`() {
        val key = ThreadKey(ProviderId("test"), BoardId("a"), ThreadId(10))
        val op =
            Post(
                id = PostId(10),
                board = key.board,
                threadId = key.thread,
                isOriginalPost = true,
            )
        assertThat(ProviderContract.validateThread(Thread(key, op))).isEmpty()
    }
}
