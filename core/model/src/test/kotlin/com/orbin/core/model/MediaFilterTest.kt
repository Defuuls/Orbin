package com.orbin.core.model

import com.google.common.truth.Truth.assertThat
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList
import org.junit.Test

class MediaFilterTest {
    @Test
    fun `images keeps stills and animated images, videos keeps only video`() {
        assertThat(MediaFilter.IMAGES.allows(MediaType.IMAGE)).isTrue()
        assertThat(MediaFilter.IMAGES.allows(MediaType.ANIMATED_IMAGE)).isTrue()
        assertThat(MediaFilter.IMAGES.allows(MediaType.VIDEO)).isFalse()

        assertThat(MediaFilter.VIDEOS.allows(MediaType.VIDEO)).isTrue()
        assertThat(MediaFilter.VIDEOS.allows(MediaType.IMAGE)).isFalse()
        assertThat(MediaFilter.VIDEOS.allows(MediaType.ANIMATED_IMAGE)).isFalse()
    }

    @Test
    fun `audio and unknown files belong to neither kind`() {
        listOf(MediaType.AUDIO, MediaType.UNKNOWN).forEach { type ->
            assertThat(MediaFilter.IMAGES.allows(type)).isFalse()
            assertThat(MediaFilter.VIDEOS.allows(type)).isFalse()
            assertThat(MediaFilter.ALL.allows(type)).isTrue()
        }
    }

    @Test
    fun `ALL leaves everything untouched`() {
        val thread = thread()

        assertThat(thread.filteredBy(MediaFilter.ALL)).isSameInstanceAs(thread)
        assertThat(listOf(catalogThread()).filteredCatalogBy(MediaFilter.ALL)).hasSize(1)
    }

    @Test
    fun `filtering a thread keeps every post but only the matching attachments`() {
        val filtered = thread().filteredBy(MediaFilter.VIDEOS)

        assertThat(filtered.replies).hasSize(1)
        assertThat(filtered.originalPost.attachments.map { it.id }).containsExactly("op-webm")
        // The reply's only attachment is an image, so it keeps its text and loses its media.
        assertThat(filtered.replies.single().attachments).isEmpty()
    }

    @Test
    fun `filtering a catalog drops threads left with no media`() {
        val threads =
            listOf(
                catalogThread(id = 1, attachments = listOf(attachment("a", MediaType.VIDEO))),
                catalogThread(id = 2, attachments = listOf(attachment("b", MediaType.IMAGE))),
                catalogThread(id = 3, attachments = emptyList()),
            )

        val filtered = threads.filteredCatalogBy(MediaFilter.VIDEOS)

        assertThat(filtered.map { it.key.thread.value }).containsExactly(1L)
        assertThat(
            filtered
                .single()
                .originalPost.attachments
                .map { it.id },
        ).containsExactly("a")
    }

    private fun attachment(
        id: String,
        type: MediaType,
    ) = MediaAttachment(
        id = id,
        originalFileName = "$id.file",
        extension = "file",
        type = type,
        sourceUrl = "https://example.org/$id",
        thumbnailUrl = "https://example.org/$id/thumb",
    )

    private fun thread() =
        Thread(
            key = ThreadKey(ProviderId("fourchan"), BoardId("g"), ThreadId(1)),
            originalPost =
                post(
                    id = 1,
                    isOriginalPost = true,
                    attachments = listOf(attachment("op-webm", MediaType.VIDEO), attachment("op-jpg", MediaType.IMAGE)),
                ),
            replies = persistentListOf(post(id = 2, attachments = listOf(attachment("reply-jpg", MediaType.IMAGE)))),
            stats = ThreadStats(),
        )

    private fun post(
        id: Long,
        isOriginalPost: Boolean = false,
        attachments: List<MediaAttachment> = emptyList(),
    ) = Post(
        id = PostId(id),
        board = BoardId("g"),
        threadId = ThreadId(1),
        isOriginalPost = isOriginalPost,
        attachments = attachments.toPersistentList(),
    )

    private fun catalogThread(
        id: Long = 1,
        attachments: List<MediaAttachment> = listOf(attachment("a", MediaType.IMAGE)),
    ) = CatalogThread(
        key = ThreadKey(ProviderId("fourchan"), BoardId("g"), ThreadId(id)),
        originalPost = post(id = id, isOriginalPost = true, attachments = attachments),
        stats = ThreadStats(),
    )
}
