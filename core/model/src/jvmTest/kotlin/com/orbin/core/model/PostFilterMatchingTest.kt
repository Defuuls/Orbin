package com.orbin.core.model

import kotlinx.collections.immutable.persistentListOf
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PostFilterMatchingTest {
    private val post =
        Post(
            id = PostId(2),
            board = BoardId("g"),
            threadId = ThreadId(1),
            isOriginalPost = false,
            subject = "A [literal] title",
            comment = PostComment("", persistentListOf(PostNode.Text("Hello from the garden"))),
            poster =
                PosterInfo(
                    name = "Alice",
                    tripcode = "!trip",
                    posterId = "local-id",
                    countryCode = "GB",
                    countryName = "United Kingdom",
                ),
        )

    @Test
    fun textRulesDistinguishKeywordsRegexAndInvalidRegexFallback() {
        assertTrue(post.matchesUserPostFilter(PostFilterRule(PostFilterKind.TEXT, "[literal]")))
        assertTrue(post.matchesUserPostFilter(PostFilterRule(PostFilterKind.TEXT, "hello.*garden", regex = true)))
        assertFalse(post.matchesUserPostFilter(PostFilterRule(PostFilterKind.TEXT, "hello.*garden")))
        assertTrue(post.matchesUserPostFilter(PostFilterRule(PostFilterKind.TEXT, "[literal", regex = true)))
        assertFalse(post.matchesUserPostFilter(PostFilterRule(PostFilterKind.TEXT, "  ", regex = true)))
    }

    @Test
    fun identityRulesMatchOnlyTheRequestedPosterField() {
        assertTrue(post.matchesUserPostFilter(PostFilterRule(PostFilterKind.NAME, "alice")))
        assertFalse(post.matchesUserPostFilter(PostFilterRule(PostFilterKind.NAME, "Ali")))
        assertTrue(post.matchesUserPostFilter(PostFilterRule(PostFilterKind.TRIPCODE, "!trip")))
        assertTrue(post.matchesUserPostFilter(PostFilterRule(PostFilterKind.COUNTRY, "gb")))
        assertTrue(post.matchesUserPostFilter(PostFilterRule(PostFilterKind.COUNTRY, "United Kingdom")))
        assertFalse(
            post.copy(poster = PosterInfo()).matchesUserPostFilter(PostFilterRule(PostFilterKind.COUNTRY, "GB")),
        )
    }

    @Test
    fun threadPatternsInspectOnlyTheOpeningPostsTitleAndComment() {
        val thread = CatalogThread(ThreadKey(ProviderId("fourchan"), post.board, post.threadId), post, ThreadStats())
        assertTrue(thread.matchesUserThreadPatterns(listOf("hello.*garden")))
        assertTrue(thread.matchesUserThreadPatterns(listOf("[literal")))
        assertFalse(thread.matchesUserThreadPatterns(listOf("Alice")))
        assertFalse(thread.matchesUserThreadPatterns(listOf("")))
    }
}
