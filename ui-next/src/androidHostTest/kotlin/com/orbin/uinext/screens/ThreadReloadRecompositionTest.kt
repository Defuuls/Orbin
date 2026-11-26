package com.orbin.uinext.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.orbin.uinext.components.NextTheme
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A thread reload hands the screen freshly built posts: equal to the ones on screen, but new
 * [Post] instances holding new List and Set instances. Compose compares them with equals, so an
 * equal reload recomposes nothing and a reload with one new reply composes only that row.
 *
 * This holds with or without config/compose/stability.conf (Post and List<Post> are already
 * compared by equals at runtime); the test guards the behaviour, it doesn't depend on the config.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xhdpi")
class ThreadReloadRecompositionTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Before
    fun reset() {
        rows.clear()
        hosts = 0
    }

    private fun post(n: Int) =
        Post(
            number = "No. $n",
            time = "2m",
            body = "Post $n",
            replyNumbers = listOf("${n + 1}", "${n + 2}"),
            ancestorPaths = listOf("root"),
            descendantIds = setOf("${n + 1}", "${n + 2}"),
        )

    /** The same post rebuilt the way a reload does: every collection is a new instance. */
    private fun Post.rebuilt() =
        copy(
            replyNumbers = ArrayList(replyNumbers),
            ancestorPaths = ArrayList(ancestorPaths),
            descendantIds = LinkedHashSet(descendantIds),
        )

    private fun List<Post>.rebuilt(): List<Post> {
        val copy = map { it.rebuilt() }
        copy.zip(this).forEach { (new, old) ->
            check(new == old && new !== old && new.replyNumbers !== old.replyNumbers)
        }
        return ArrayList(copy)
    }

    private fun show(initial: List<Post>): (List<Post>) -> Unit {
        var posts by mutableStateOf(initial)
        composeRule.setContent { NextTheme { ReloadHost(posts) } }
        composeRule.waitForIdle()
        return { next ->
            composeRule.runOnIdle { posts = next }
            composeRule.waitForIdle()
        }
    }

    @Test
    fun `a reload of equal posts does not recompose the thread`() {
        val first = (1..ROWS).map(::post)
        val reload = show(first)
        assertEquals(1, hosts)
        val rowsBefore = rows.toMap()

        reload(first.rebuilt())
        println("ThreadReloadRecomposition: equal reload recomposed the thread host ${hosts - 1} time(s)")
        assertEquals("An equal reload must not recompose the thread", 1, hosts)
        assertEquals("An equal reload must not recompose any row", rowsBefore, rows.toMap())

        // Control: a reload that does change the posts still recomposes.
        reload(first.rebuilt() + post(ROWS + 1))
        assertEquals("A changed reload recomposes the thread", 2, hosts)
    }

    @Test
    fun `a reload with one new reply recomposes only the new row`() {
        val first = (1..ROWS).map(::post)
        val reload = show(first)
        assertEquals("Every row fits on screen", first.map { it.id }.toSet(), rows.keys)
        val rowsBefore = rows.toMap()

        reload(first.rebuilt() + post(ROWS + 1))
        val recomposed = rowsBefore.keys.count { rows[it] != rowsBefore[it] }
        println("ThreadReloadRecomposition: $recomposed of $ROWS unchanged rows recomposed on reload")
        assertEquals("Unchanged rows must skip recomposition on reload", 0, recomposed)
        assertEquals("The new reply composes once", 1, rows[post(ROWS + 1).id])

        // Control: a row whose post changed does recompose.
        reload(first.rebuilt().mapIndexed { i, p -> if (i == 0) p.copy(body = "Edited") else p } + post(ROWS + 1))
        assertEquals("An edited row recomposes", rowsBefore.getValue(first[0].id) + 1, rows[first[0].id])
    }

    private companion object {
        const val ROWS = 6
    }
}

/** Body compositions per post id: a row's body composes again only if its cell does. */
private val rows = mutableMapOf<String, Int>()

/** Compositions of the host that hands the reloaded list to [ThreadScreen]. */
private var hosts = 0

/** Stands in for a screen host: it takes the reloaded list as a parameter, as the app's hosts do. */
@Composable
private fun ReloadHost(posts: List<Post>) {
    SideEffect { hosts++ }
    ThreadScreen(
        subject = "Reload",
        board = "/g/",
        posts = posts,
        // Captures nothing, so the slot is the same instance on every recomposition.
        body = { post -> SideEffect { rows[post.id] = (rows[post.id] ?: 0) + 1 } },
    )
}
