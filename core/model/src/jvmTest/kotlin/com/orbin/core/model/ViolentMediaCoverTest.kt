package com.orbin.core.model

import com.google.common.truth.Truth.assertThat
import kotlinx.collections.immutable.persistentListOf
import org.junit.Test

class ViolentMediaCoverTest {
    @Test
    fun `violence labels match as whole words in any case`() {
        assertThat(ViolentMediaCover.matches("Street FIGHT compilation")).isTrue()
        assertThat(ViolentMediaCover.matches("dashcam_car_crash.webm")).isTrue()
        assertThat(ViolentMediaCover.matches("NSFL")).isTrue()
        assertThat(ViolentMediaCover.matches("knocked-out cold")).isTrue()
    }

    /** Covering is cheap, but covering ordinary threads would make the cover meaningless. */
    @Test
    fun `ordinary words that contain a label do not match`() {
        assertThat(ViolentMediaCover.matches("firefight in the new game update")).isFalse()
        assertThat(ViolentMediaCover.matches("my app crashed again")).isFalse()
        assertThat(ViolentMediaCover.matches("screenshot thread")).isFalse()
        assertThat(ViolentMediaCover.matches("bloodborne is great")).isFalse()
        assertThat(ViolentMediaCover.matches(null)).isFalse()
    }

    @Test
    fun `a labelled post has its media covered and keeps its text`() {
        val covered = post(subject = "brutal knockout", files = 2).withMediaCovered()

        assertThat(covered.attachments.map { it.isSpoiler }).containsExactly(true, true)
        assertThat(covered.subject).isEqualTo("brutal knockout")
    }

    @Test
    fun `a label in a filename is enough`() {
        assertThat(post(fileName = "bodycam_footage.mp4").hasViolentLabel()).isTrue()
    }

    @Test
    fun `a labelled opening post covers every file in the thread`() {
        val thread =
            Thread(
                key = KEY,
                originalPost = post(subject = "Fight thread"),
                replies = persistentListOf(post(id = 2, comment = "here's another"), post(id = 3)),
            ).withViolentMediaCovered()

        assertThat(thread.allPosts.flatMap { it.attachments }.map { it.isSpoiler }).containsExactly(true, true, true)
    }

    @Test
    fun `otherwise only the labelled replies are covered`() {
        val thread =
            Thread(
                key = KEY,
                originalPost = post(subject = "Cars you like"),
                replies = persistentListOf(post(id = 2, comment = "saw a car crash today"), post(id = 3)),
            ).withViolentMediaCovered()

        assertThat(
            thread.allPosts.map { it.attachments.single().isSpoiler },
        ).containsExactly(false, true, false).inOrder()
    }

    @Test
    fun `a catalog entry is covered the same way`() {
        val entry =
            CatalogThread(
                key = KEY,
                originalPost = post(subject = "war footage general"),
                stats = ThreadStats(),
                previewReplies = persistentListOf(post(id = 2)),
            ).withViolentMediaCovered()

        assertThat(
            entry.originalPost.attachments
                .single()
                .isSpoiler,
        ).isTrue()
        assertThat(
            entry.previewReplies
                .single()
                .attachments
                .single()
                .isSpoiler,
        ).isTrue()
    }

    private fun post(
        id: Long = 1,
        subject: String? = null,
        comment: String = "",
        fileName: String = "image.jpg",
        files: Int = 1,
    ) = Post(
        id = PostId(id),
        board = BoardId("b"),
        threadId = ThreadId(1),
        isOriginalPost = id == 1L,
        subject = subject,
        comment = PostComment(raw = comment, nodes = persistentListOf()),
        attachments =
            persistentListOf(
                *Array(files) { index ->
                    MediaAttachment(
                        id = "$id-$index",
                        originalFileName = fileName,
                        extension = "jpg",
                        type = MediaType.IMAGE,
                        sourceUrl = "https://example.invalid/$id-$index.jpg",
                        thumbnailUrl = "https://example.invalid/$id-${index}s.jpg",
                    )
                },
            ),
    )

    private companion object {
        val KEY = ThreadKey(ProviderId("p"), BoardId("b"), ThreadId(1))
    }
}
