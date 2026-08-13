package com.orbin.feature.board

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertAny
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.orbin.core.designsystem.theme.OrbinTheme
import com.orbin.core.model.BoardId
import com.orbin.core.model.CatalogThread
import com.orbin.core.model.Post
import com.orbin.core.model.PostComment
import com.orbin.core.model.PostId
import com.orbin.core.model.ProviderId
import com.orbin.core.model.ThreadId
import com.orbin.core.model.ThreadKey
import com.orbin.core.model.ThreadStats
import kotlinx.collections.immutable.persistentListOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A visited thread is only dimmer to the eye; these assert the state also exists for TalkBack,
 * which is the difference between a returning reader who can skim and one who cannot.
 */
@RunWith(AndroidJUnit4::class)
class CatalogCellSemanticsTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private fun thread(): CatalogThread {
        val key = ThreadKey(ProviderId("fourchan"), BoardId("g"), ThreadId(1))
        return CatalogThread(
            key = key,
            originalPost =
                Post(
                    id = PostId(1),
                    board = key.board,
                    threadId = key.thread,
                    isOriginalPost = true,
                    subject = "A thread",
                    comment = PostComment(raw = "hello", nodes = persistentListOf()),
                ),
            stats = ThreadStats(),
        )
    }

    private fun hasStateDescription(value: String) =
        SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, value)

    @Test
    fun aVisitedListCellSaysSo() {
        composeTestRule.setContent {
            OrbinTheme {
                KurobaListThreadCell(
                    thread = thread(),
                    isSubscribed = false,
                    isVisited = true,
                    onToggleSubscription = {},
                    onClick = {},
                )
            }
        }

        composeTestRule.onAllNodes(hasStateDescription("Already read")).assertAny(hasStateDescription("Already read"))
    }

    @Test
    fun anUnvisitedListCellCarriesNoReadState() {
        composeTestRule.setContent {
            OrbinTheme {
                KurobaListThreadCell(
                    thread = thread(),
                    isSubscribed = false,
                    isVisited = false,
                    onToggleSubscription = {},
                    onClick = {},
                )
            }
        }

        // No node should claim a read state on a thread that has not been opened.
        composeTestRule
            .onAllNodes(hasStateDescription("Already read"))
            .fetchSemanticsNodes()
            .let { nodes -> check(nodes.isEmpty()) { "expected no 'Already read' state, found ${nodes.size}" } }
    }

    @Test
    fun aVisitedGridCellSaysSo() {
        composeTestRule.setContent {
            OrbinTheme {
                KurobaGridThreadCell(
                    thread = thread(),
                    isSubscribed = false,
                    isVisited = true,
                    onToggleSubscription = {},
                    onClick = {},
                )
            }
        }

        composeTestRule.onAllNodes(hasStateDescription("Already read")).assertAny(hasStateDescription("Already read"))
    }
}
