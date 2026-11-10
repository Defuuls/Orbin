package com.orbin.uinext

import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xhdpi")
class RedesignInteractionTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun boardsFromDifferentSitesCanShareAPathAndOpenIndependently() {
        var opened = ""
        composeRule.setContent {
            NextTheme {
                BoardsScreen(
                    boards =
                        listOf(
                            BoardTile("first", "/b/", "First board"),
                            BoardTile("second", "/b/", "Second board"),
                        ),
                    showRail = false,
                    onOpenBoard = { opened = it.id },
                )
            }
        }
        composeRule.onNodeWithText("Second board").performClick()
        assertEquals("second", opened)
    }

    @Test
    fun postMetadataHasAFullTouchTargetAndItsOwnCollapseAction() {
        var collapsed = 0
        var opened = 0
        composeRule.setContent {
            NextTheme {
                ThreadScreen(
                    subject = "Thread",
                    board = "/g/",
                    posts = listOf(Post("No. 1", "12:00", "Post body")),
                    onToggleCollapse = { collapsed++ },
                    onPostClick = { opened++ },
                )
            }
        }
        composeRule
            .onNode(hasText("No. 1", substring = true) and hasClickAction())
            .assertHeightIsAtLeast(MIN_TOUCH_TARGET)
            .performClick()
        assertEquals(1, collapsed)
        assertEquals(0, opened)
    }
}
