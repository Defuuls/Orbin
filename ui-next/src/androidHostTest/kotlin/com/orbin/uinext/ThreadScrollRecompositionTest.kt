package com.orbin.uinext

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.orbin.uinext.components.NextTheme
import com.orbin.uinext.screens.Post
import com.orbin.uinext.screens.ThreadScreen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * While the reader scrolls, both hosts save the reading position and mark posts read, and each of
 * those recomposes the host and rebuilds the thread's rows. Rows whose post did not change must
 * not run their comment or media again; only a changed post may.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xhdpi")
class ThreadScrollRecompositionTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val bodies = mutableMapOf<String, Int>()
    private val media = mutableMapOf<String, Int>()

    @Test
    fun `host recompositions and equal row rebuilds do not re-run visible comments or media`() {
        var posts by mutableStateOf(fixturePosts())
        var unread by mutableStateOf(setOf("2", "3"))
        // Stands in for the saved reading position: host state that the rows do not draw.
        var savedOffset by mutableStateOf(0)
        composeRule.setContent {
            // Read here, as the hosts read their memory, so each change recomposes the host.
            val offset = savedOffset
            // Both hosts keep their slots across recompositions, as here.
            val body: @Composable (Post) -> Unit =
                remember {
                    { post ->
                        SideEffect { bodies[post.id] = (bodies[post.id] ?: 0) + 1 }
                        Text(post.body)
                    }
                }
            val tile: @Composable (Post, Modifier) -> Unit =
                remember { { post, _ -> SideEffect { media[post.id] = (media[post.id] ?: 0) + 1 } } }
            NextTheme {
                ThreadScreen(
                    subject = "Recomposition",
                    board = "/g/",
                    posts = posts,
                    unreadPostIds = unread,
                    onVisiblePosts = {},
                    statusText = "offset $offset",
                    body = body,
                    media = tile,
                )
            }
        }
        composeRule.waitForIdle()
        val firstBodies = bodies.toMap()
        val firstMedia = media.toMap()
        assertTrue("The fixture must show several posts: $firstBodies", firstBodies.size >= 3)
        assertTrue("The fixture must show media: $firstMedia", firstMedia.size >= 2)

        // The reading position is saved after each scroll settles.
        repeat(5) {
            composeRule.runOnIdle { savedOffset += 40 }
            composeRule.waitForIdle()
        }
        assertEquals("Saving the position must not re-run comments", firstBodies, bodies.toMap())
        assertEquals("Saving the position must not re-run media", firstMedia, media.toMap())

        // Marking a post read rebuilds every row as new, equal instances, and a new unread set.
        composeRule.runOnIdle {
            posts = fixturePosts()
            unread = setOf("2", "3")
        }
        composeRule.waitForIdle()
        assertEquals("Equal posts must not re-run their comment", firstBodies, bodies.toMap())
        assertEquals("Equal posts must not re-run their media", firstMedia, media.toMap())

        // A post that did change still updates, and only that one.
        composeRule.runOnIdle { posts = fixturePosts().map { if (it.id == "2") it.copy(unseen = true) else it } }
        composeRule.waitForIdle()
        assertTrue("A changed post must update", bodies.getValue("2") > firstBodies.getValue("2"))
        assertEquals(firstBodies - "2", bodies.toMap() - "2")
        assertEquals(firstMedia, media.toMap())
    }

    private fun fixturePosts(): List<Post> =
        (1..12).map { index ->
            Post(
                number = "No.$index",
                time = "${index}m",
                body = "Post body $index",
                id = "$index",
                hasMedia = index % 2 == 1,
                author = "Anonymous",
                replyNumbers = if (index == 1) listOf("2", "3") else emptyList(),
                descendantIds = if (index == 1) setOf("2", "3") else emptySet(),
                ancestorPaths = if (index > 1) listOf("1") else emptyList(),
            )
        }
}
