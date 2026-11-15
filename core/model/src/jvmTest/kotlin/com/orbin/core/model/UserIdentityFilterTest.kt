package com.orbin.core.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UserIdentityFilterTest {
    private val post =
        Post(
            PostId(2),
            BoardId("g"),
            ThreadId(1),
            false,
            poster = PosterInfo(name = "Anon42", tripcode = "!abcd", countryCode = "CA", countryName = "Canada"),
        )

    @Test
    fun metadataRulesHonorTheRegularExpressionOption() {
        assertTrue(post.matchesUserPostFilter(PostFilterRule(PostFilterKind.NAME, "^anon[0-9]+$", regex = true)))
        assertTrue(post.matchesUserPostFilter(PostFilterRule(PostFilterKind.TRIPCODE, "^!a.*$", regex = true)))
        assertTrue(post.matchesUserPostFilter(PostFilterRule(PostFilterKind.COUNTRY, "^(uk|ca)$", regex = true)))
        assertFalse(post.matchesUserPostFilter(PostFilterRule(PostFilterKind.COUNTRY, "^uk$", regex = true)))
    }

    @Test
    fun literalMetadataRulesContinueToMatchTheWholeIdentity() {
        assertTrue(post.matchesUserPostFilter(PostFilterRule(PostFilterKind.NAME, "anon42")))
        assertFalse(post.matchesUserPostFilter(PostFilterRule(PostFilterKind.NAME, "Anon")))
        assertFalse(post.matchesUserPostFilter(PostFilterRule(PostFilterKind.TRIPCODE, "!a.*")))
        assertTrue(post.matchesUserPostFilter(PostFilterRule(PostFilterKind.COUNTRY, "canada")))
    }
}
