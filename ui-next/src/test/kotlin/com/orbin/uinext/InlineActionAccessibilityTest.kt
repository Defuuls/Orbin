package com.orbin.uinext

import androidx.compose.foundation.layout.Row
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.v2.createComposeRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The interface sets its actions as words rather than as Material buttons, which is a look, not a
 * licence: an action still has to announce itself as a button and still has to be big enough to
 * hit. A clickable Text gives neither, so both are asserted rather than assumed.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class InlineActionAccessibilityTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val isButton = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)

    @Test
    fun `an action announces itself as a button`() {
        composeRule.setContent {
            NextTheme { InlineAction(label = "Download all", onClick = {}) }
        }

        composeRule.onNode(isButton).assertExists()
    }

    @Test
    fun `an action is at least the minimum touch target in both directions`() {
        composeRule.setContent {
            // In a row, as they are used, so a neighbour cannot be blamed for the size.
            NextTheme {
                Row {
                    InlineAction(label = "List", accent = true, onClick = {})
                    InlineAction(label = "Grid", onClick = {})
                }
            }
        }

        // "Grid" is the unaccented one — the smaller of the two, with 4dp of horizontal padding
        // rather than 13dp, so it is the one a fixed size floor has to rescue.
        val actions = composeRule.onAllNodes(isButton)
        actions[0].assertHeightIsAtLeast(MIN_TOUCH_TARGET).assertWidthIsAtLeast(MIN_TOUCH_TARGET)
        actions[1].assertHeightIsAtLeast(MIN_TOUCH_TARGET).assertWidthIsAtLeast(MIN_TOUCH_TARGET)
    }

    @Test
    fun `an action with nothing to do is not announced as a button`() {
        composeRule.setContent {
            NextTheme { InlineAction(label = "Recent") }
        }

        composeRule.onAllNodes(isButton).assertCountEquals(0)
    }

    @Test
    fun `the rail's search affordance is a button of the same minimum size`() {
        composeRule.setContent {
            NextTheme { ContextRail(where = "Feed", detail = "7 boards", onSearch = {}) }
        }

        composeRule.onNode(isButton).assertHeightIsAtLeast(MIN_TOUCH_TARGET)
        composeRule.onNode(isButton).assertWidthIsAtLeast(MIN_TOUCH_TARGET)
    }
}
