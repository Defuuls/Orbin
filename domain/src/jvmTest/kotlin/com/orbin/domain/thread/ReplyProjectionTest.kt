package com.orbin.domain.thread

import com.orbin.core.model.BoardId
import com.orbin.core.model.Post
import com.orbin.core.model.PostId
import com.orbin.core.model.ThreadId
import kotlinx.collections.immutable.toImmutableList
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReplyProjectionTest {
    private val posts = listOf(post(1), post(2), post(3), post(4, 2), post(5, 4), post(6, 2, 4), post(7, 3, 4))

    @Test
    fun branchesFollowParentsAndRepeatedPostsUseIndependentSourceQualifiedPaths() {
        val rows = projectReplyRows(posts, "fourchan/g/1")

        assertEquals(listOf(1L, 2L, 4L, 5L, 6L, 7L, 3L, 7L), rows.map { it.postId.value })
        assertEquals(listOf(0, 0, 1, 2, 2, 2, 0, 1), rows.map { it.depth })
        assertEquals("fourchan/g/1/2/4/6", rows.single { it.postId == PostId(6) }.path)
        val repeated = rows.filter { it.postId == PostId(7) }
        assertFalse(repeated.first().preview)
        assertTrue(repeated.last().preview)
        assertEquals(repeated.first().path, repeated.last().primaryPath)
        assertEquals(4, rows.single { it.postId == PostId(2) }.descendants.size)
        assertTrue(projectReplyRows(posts, "bbwchan/g/1").none { it.path in rows.map { row -> row.path } })
    }

    @Test
    fun foldingHidesTheSubtreeWithoutMovingItsFullPostsToAnotherBranch() {
        val rows = projectReplyRows(posts, "fourchan/g/1", foldedPaths = setOf("fourchan/g/1/2"))

        assertEquals(listOf(1L, 2L, 3L, 7L), rows.map { it.postId.value })
        assertTrue(rows[1].collapsed)
        assertTrue(rows.last().preview)
        assertEquals("fourchan/g/1/2/4/7", rows.last().primaryPath)
    }

    @Test
    fun flatModeKeepsPostingOrderAndFoldsOnlyTheChosenPost() {
        val rows = projectReplyRows(posts, "fourchan/g/1", nested = false, foldedPaths = setOf("fourchan/g/1/4"))

        assertEquals(posts.map { it.id }, rows.map { it.postId })
        assertTrue(rows.all { it.depth == 0 && !it.preview })
        assertTrue(rows.single { it.postId == PostId(4) }.collapsed)
        assertTrue(rows.any { it.postId == PostId(5) })
    }

    @Test
    fun hiddenPostsRemainPlaceholdersAndTheOpeningPostCannotBeHiddenIndividually() {
        val rows =
            projectReplyRows(
                posts,
                "fourchan/g/1",
                hiddenReasons =
                    mapOf(
                        PostId(1) to "Hidden",
                        PostId(4) to "Hidden by you",
                    ),
            )

        assertEquals(null, rows.first().hiddenReason)
        assertEquals("Hidden by you", rows.single { it.postId == PostId(4) }.hiddenReason)
        assertFalse(rows.any { it.postId == PostId(5) || it.postId == PostId(6) })
    }

    @Test
    fun malformedForwardAndSelfQuotesCannotMakeCyclesOrLosePosts() {
        val malformed = listOf(post(1, 1, 9), post(2, 2, 3, 99), post(3, 2), post(4, 3, 2))
        val rows = projectReplyRows(malformed, "fourchan/g/1")

        assertEquals(listOf(1L, 2L, 3L, 4L), rows.map { it.postId.value })
        assertEquals(listOf(0, 0, 1, 2), rows.map { it.depth })
        assertEquals(rows.size, rows.map { it.path }.toSet().size)
    }

    private fun post(
        id: Long,
        vararg replies: Long,
    ) = Post(
        id = PostId(id),
        board = BoardId("g"),
        threadId = ThreadId(1),
        isOriginalPost = id == 1L,
        repliesTo = replies.map(::PostId).toImmutableList(),
    )
}
