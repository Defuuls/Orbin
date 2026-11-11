package com.orbin.ios

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
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ThreadVideosTest {
    private val key = ThreadKey(ProviderId("fourchan"), BoardId("g"), ThreadId(100))

    @Test
    fun videosInRepliesRemainAvailableWhenTheOpeningPostOnlyHasAnImage() {
        val first = attachment("reply-video", MediaType.VIDEO, "webm")
        val second = attachment("later-video", MediaType.VIDEO, "mp4")
        val thread =
            Thread(
                key,
                post(100, attachment("opening-image", MediaType.IMAGE, "png")),
                listOf(
                    post(101, attachment("reply-image", MediaType.IMAGE, "jpg"), first),
                    post(102, attachment("audio", MediaType.AUDIO, "mp3")),
                    post(103, second, attachment("animated", MediaType.ANIMATED_IMAGE, "gif")),
                ).toImmutableList(),
            )

        assertEquals(listOf(first, second), thread.videos)
        assertSame(first, thread.videos.first())
    }

    @Test
    fun allVideosKeepTheirPostAndAttachmentOrderWithoutLosingSourceOrSpoilerMetadata() {
        val first = attachment("reused-id", MediaType.VIDEO, "mp4").copy(isSpoiler = true)
        val second =
            attachment("reused-id", MediaType.VIDEO, "webm")
                .copy(sourceUrl = "https://other.example/other-source.webm")
        val third = attachment("third", MediaType.VIDEO, "mov")
        val thread = Thread(key, post(100, first), listOf(post(101, second, third)).toImmutableList())

        assertEquals(3, thread.videos.size)
        assertSame(first, thread.videos[0])
        assertSame(second, thread.videos[1])
        assertSame(third, thread.videos[2])
        assertTrue(thread.videos[0].isSpoiler)
        assertEquals("https://other.example/other-source.webm", thread.videos[1].sourceUrl)
    }

    @Test
    fun aThreadWithoutVideosHasAnEmptyPlaylistRatherThanAnImageOrAudioFallback() {
        val thread =
            Thread(
                key,
                post(100, attachment("image", MediaType.IMAGE, "jpg")),
                listOf(post(101, attachment("audio", MediaType.AUDIO, "mp3"))).toImmutableList(),
            )

        assertTrue(thread.videos.isEmpty())
    }

    @Test
    fun threadVideoLoadingEmptyAndPlayerRoutesHaveLightStatusIconsOnTheirBlackSurface() {
        val videos = Route.ThreadVideos(key)
        val unlocked = LockState(obscured = false)

        assertTrue(lightStatusBarIcons(videos, darkTheme = false, firstRun = false, lockState = unlocked))
        assertFalse(lightStatusBarIcons(videos, darkTheme = false, firstRun = true, lockState = unlocked))
        assertFalse(
            lightStatusBarIcons(videos, darkTheme = false, firstRun = false, lockState = LockState(obscured = true)),
        )
    }

    private fun post(
        id: Long,
        vararg files: MediaAttachment,
    ) = Post(
        id = PostId(id),
        board = key.board,
        threadId = key.thread,
        isOriginalPost = id == key.thread.value,
        attachments = files.toList().toImmutableList(),
    )

    private fun attachment(
        id: String,
        type: MediaType,
        extension: String,
    ) = MediaAttachment(
        id = id,
        originalFileName = "$id.$extension",
        type = type,
        extension = extension,
        sourceUrl = "https://media.example/$id.$extension",
        thumbnailUrl = "https://media.example/$id.jpg",
    )
}
