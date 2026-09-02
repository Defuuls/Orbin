package com.orbin.feature.thread

import com.google.common.truth.Truth.assertThat
import com.orbin.core.model.BoardId
import com.orbin.core.model.MediaAttachment
import com.orbin.core.model.MediaType
import com.orbin.core.model.Post
import com.orbin.core.model.PostId
import com.orbin.core.model.ProviderId
import com.orbin.core.model.Thread
import com.orbin.core.model.ThreadId
import com.orbin.core.model.ThreadKey
import com.orbin.core.model.ThreadStats
import kotlinx.collections.immutable.persistentListOf
import org.junit.Test

class MediaScrollSyncTest {
    @Test
    fun `global gallery media index resolves to owning thread post`() {
        val thread =
            Thread(
                key = ThreadKey(ProviderId("fourchan"), BoardId("g"), ThreadId(100)),
                originalPost =
                    post(
                        id = 100,
                        attachments = persistentListOf(media("op-1"), media("op-2")),
                        isOriginalPost = true,
                    ),
                replies =
                    persistentListOf(
                        post(id = 101),
                        post(id = 102, attachments = persistentListOf(media("reply-1"))),
                        post(
                            id = 103,
                            attachments = persistentListOf(media("reply-2"), media("reply-3")),
                        ),
                    ),
                stats = ThreadStats(),
            )

        assertThat(thread.postIdForMediaIndex(0)).isEqualTo(PostId(100))
        assertThat(thread.postIdForMediaIndex(1)).isEqualTo(PostId(100))
        assertThat(thread.postIdForMediaIndex(2)).isEqualTo(PostId(102))
        assertThat(thread.postIdForMediaIndex(3)).isEqualTo(PostId(103))
        assertThat(thread.postIdForMediaIndex(4)).isEqualTo(PostId(103))
        assertThat(thread.postIdForMediaIndex(5)).isNull()
        assertThat(thread.postIdForMediaIndex(-1)).isNull()
    }

    private fun post(
        id: Long,
        attachments: kotlinx.collections.immutable.PersistentList<MediaAttachment> = persistentListOf(),
        isOriginalPost: Boolean = false,
    ) = Post(
        id = PostId(id),
        board = BoardId("g"),
        threadId = ThreadId(100),
        isOriginalPost = isOriginalPost,
        attachments = attachments,
    )

    private fun media(id: String) =
        MediaAttachment(
            id = id,
            originalFileName = "$id.jpg",
            extension = "jpg",
            type = MediaType.IMAGE,
            sourceUrl = "https://example.org/$id.jpg",
            thumbnailUrl = "https://example.org/$id-thumb.jpg",
        )
}
