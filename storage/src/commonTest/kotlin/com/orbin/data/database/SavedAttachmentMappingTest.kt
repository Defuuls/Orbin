package com.orbin.data.database

import com.orbin.core.model.BoardId
import com.orbin.core.model.MediaAttachment
import com.orbin.core.model.MediaType
import com.orbin.core.model.Post
import com.orbin.core.model.PostId
import com.orbin.core.model.ProviderId
import com.orbin.core.model.Thread
import com.orbin.core.model.ThreadId
import com.orbin.core.model.ThreadKey
import kotlinx.collections.immutable.toImmutableList
import kotlin.test.Test
import kotlin.test.assertEquals

class SavedAttachmentMappingTest {
    @Test
    fun savedAttachmentsKeepPlayableTypesWhenRestored() {
        val key = ThreadKey(ProviderId("example"), BoardId("g"), ThreadId(42))
        val attachments =
            formats.map { (extension, type) ->
                MediaAttachment(
                    id = extension,
                    originalFileName = "sample.${extension.uppercase()}",
                    extension = extension,
                    type = type,
                    sourceUrl = "https://example.test/sample.$extension",
                    thumbnailUrl = "https://example.test/thumb.jpg",
                )
            }
        val thread =
            Thread(
                key = key,
                originalPost =
                    Post(
                        id = PostId(42),
                        board = key.board,
                        threadId = key.thread,
                        isOriginalPost = true,
                        attachments = attachments.toImmutableList(),
                    ),
            )
        val (savedThread, savedPosts) = thread.toSavedEntities(savedAtMillis = 1_000)
        val restored = savedThread.toDomain(savedPosts).originalPost.attachments

        assertEquals(attachments.map { it.type }, restored.map { it.type })
        assertEquals(attachments.map { it.isPlayable }, restored.map { it.isPlayable })
        assertEquals(attachments.map { it.sourceUrl }, restored.map { it.sourceUrl })
    }

    @Test
    fun savedAttachmentsPreservePositionalPairingAndFallBackToUrlExtension() {
        val key = ThreadKey(ProviderId("example"), BoardId("g"), ThreadId(99))
        val attachments =
            listOf(
                MediaAttachment(
                    id = "1",
                    originalFileName = "",
                    extension = "webm",
                    type = MediaType.VIDEO,
                    sourceUrl = "https://example.test/first.webm",
                    thumbnailUrl = "https://example.test/t1.jpg",
                ),
                MediaAttachment(
                    id = "2",
                    originalFileName = "second_without_ext",
                    extension = "mp4",
                    type = MediaType.VIDEO,
                    sourceUrl = "https://example.test/second.mp4",
                    thumbnailUrl = "https://example.test/t2.jpg",
                ),
            )
        val thread =
            Thread(
                key = key,
                originalPost =
                    Post(
                        id = PostId(99),
                        board = key.board,
                        threadId = key.thread,
                        isOriginalPost = true,
                        attachments = attachments.toImmutableList(),
                    ),
            )
        val (savedThread, savedPosts) = thread.toSavedEntities(savedAtMillis = 1_000)
        val restored = savedThread.toDomain(savedPosts).originalPost.attachments

        assertEquals(listOf("first.webm", "second_without_ext"), restored.map { it.originalFileName })
        assertEquals(listOf(MediaType.VIDEO, MediaType.VIDEO), restored.map { it.type })
    }

    private val formats =
        mapOf(
            "webm" to MediaType.VIDEO,
            "mp4" to MediaType.VIDEO,
            "m4v" to MediaType.VIDEO,
            "mov" to MediaType.VIDEO,
            "mkv" to MediaType.VIDEO,
            "ogv" to MediaType.VIDEO,
            "mp3" to MediaType.AUDIO,
            "ogg" to MediaType.AUDIO,
            "oga" to MediaType.AUDIO,
            "opus" to MediaType.AUDIO,
            "flac" to MediaType.AUDIO,
            "wav" to MediaType.AUDIO,
            "m4a" to MediaType.AUDIO,
            "aac" to MediaType.AUDIO,
            "gif" to MediaType.ANIMATED_IMAGE,
            "apng" to MediaType.ANIMATED_IMAGE,
            "png" to MediaType.IMAGE,
        )
}
