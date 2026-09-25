package com.orbin.core.model

import com.google.common.truth.Truth.assertThat
import kotlinx.collections.immutable.persistentListOf
import org.junit.Test

class ContentFilterTest {
    private fun post(
        subject: String? = null,
        comment: String = "",
        poster: PosterInfo = PosterInfo(),
    ) = Post(
        id = PostId(1),
        board = BoardId("g"),
        threadId = ThreadId(1),
        isOriginalPost = true,
        subject = subject,
        comment = PostComment(raw = comment, nodes = persistentListOf()),
        poster = poster,
    )

    @Test
    fun `matches a token in the subject`() {
        assertThat(post(subject = "Weekly sticky").matchesFilterTokens(setOf("sticky"))).isTrue()
    }

    @Test
    fun `matches a token in the comment text`() {
        assertThat(post(comment = "This is a Crypto thread").matchesFilterTokens(setOf("crypto"))).isTrue()
    }

    @Test
    fun `does not match a post it has nothing in common with`() {
        assertThat(post(comment = "hello").matchesFilterTokens(setOf("crypto"))).isFalse()
    }

    @Test
    fun `no tokens matches nothing`() {
        assertThat(post(comment = "anything").matchesFilterTokens(emptySet())).isFalse()
    }

    /**
     * The regression this file exists for: the old matcher searched `PostComment.toString()`, so
     * these tokens — its field names and node class names — hid every thread in the feed.
     */
    @Test
    fun `does not match the comment's own class and field names`() {
        val post = post(comment = "an ordinary post")

        assertThat(post.matchesFilterTokens(setOf("raw"))).isFalse()
        assertThat(post.matchesFilterTokens(setOf("nodes"))).isFalse()
        assertThat(post.matchesFilterTokens(setOf("postcomment"))).isFalse()
    }

    @Test
    fun `matches a poster name as a substring`() {
        val post = post(poster = PosterInfo(name = "Anonymous"))

        assertThat(post.matchesFilterTokens(setOf("name:anon"))).isTrue()
    }

    @Test
    fun `matches a capcode as a substring`() {
        assertThat(post(poster = PosterInfo(capcode = "Mod")).matchesFilterTokens(setOf("cap:mod"))).isTrue()
    }

    @Test
    fun `matches a tripcode exactly, ignoring case`() {
        val post = post(poster = PosterInfo(tripcode = "!!AbCdEf"))

        assertThat(post.matchesFilterTokens(setOf("trip:!!abcdef"))).isTrue()
    }

    @Test
    fun `does not match part of a tripcode`() {
        val post = post(poster = PosterInfo(tripcode = "!!AbCdEf"))

        // Opaque identifiers are matched whole: a partial match would catch unrelated posters.
        assertThat(post.matchesFilterTokens(setOf("trip:!!abc"))).isFalse()
    }

    @Test
    fun `matches a poster id exactly`() {
        val post = post(poster = PosterInfo(posterId = "Ab3xY"))

        assertThat(post.matchesFilterTokens(setOf("id:ab3xy"))).isTrue()
        assertThat(post.matchesFilterTokens(setOf("id:ab3"))).isFalse()
    }

    @Test
    fun `a poster token does not match the post's text`() {
        // Otherwise "name:mod" would hide every post that merely mentions a mod.
        assertThat(post(comment = "the mod deleted it").matchesFilterTokens(setOf("name:mod"))).isFalse()
    }

    @Test
    fun `a poster token against an absent field matches nothing`() {
        assertThat(post(comment = "hello").matchesFilterTokens(setOf("id:anything"))).isFalse()
    }

    @Test
    fun `an empty prefixed token matches nothing rather than everything`() {
        val post = post(poster = PosterInfo(name = "Anonymous"))

        assertThat(post.matchesFilterTokens(setOf("name:"))).isFalse()
    }

    @Test
    fun `matches a board by id, title or description`() {
        val board = Board(id = BoardId("g"), title = "Technology", description = "Computers and such")

        assertThat(board.matchesFilterTokens(setOf("technology"))).isTrue()
        assertThat(board.matchesFilterTokens(setOf("computers"))).isTrue()
        assertThat(board.matchesFilterTokens(setOf("politics"))).isFalse()
    }
}
