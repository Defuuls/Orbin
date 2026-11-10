package com.orbin.uinext

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xhdpi")
class EditorialDockTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val isTab = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)

    @Test
    fun largeTextKeepsLabelsReadableAndTargetsReachable() {
        composeRule.setContent {
            NextTheme(fontScale = 2f) {
                // Match the real-font renderer and viewport used by the screenshot suite.
                Surface(modifier = Modifier.size(411.dp, 891.dp)) {
                    Box(contentAlignment = Alignment.BottomCenter) {
                        DestinationPill(NextDestination.FEED, {})
                    }
                }
            }
        }
        listOf("Home", "Boards", "Watched", "Search", "Saved").forEach { label ->
            composeRule
                .onNode(isTab and hasContentDescription(label))
                .assertWidthIsAtLeast(MIN_TOUCH_TARGET)
                .assertHeightIsAtLeast(MIN_TOUCH_TARGET)
        }
        assertIndependentTargets()
    }

    @Test
    fun choosingATabUpdatesSelectionWithOneCallback() {
        var calls = 0
        composeRule.setContent {
            var selected by remember { mutableStateOf(NextDestination.FEED) }
            NextTheme {
                DestinationPill(
                    selected = selected,
                    onSelect = {
                        selected = it
                        calls++
                    },
                )
            }
        }
        composeRule.onNode(isTab and hasContentDescription("Home")).assertIsSelected()
        composeRule
            .onNode(isTab and hasContentDescription("Saved"))
            .performClick()
            .assertIsSelected()
        composeRule.onNode(isTab and hasContentDescription("Home")).assertIsNotSelected()
        assertEquals(1, calls)
    }

    @Test
    fun narrowViewportKeepsAllFiveIndependent48dpTargets() {
        var calls = 0
        composeRule.setContent {
            var selected by remember { mutableStateOf(NextDestination.FEED) }
            NextTheme(fontScale = 2f) {
                Surface(modifier = Modifier.size(248.dp, 400.dp)) {
                    Box(contentAlignment = Alignment.BottomCenter) {
                        DestinationPill(selected, {
                            selected = it
                            calls++
                        })
                    }
                }
            }
        }
        listOf("Home", "Boards", "Watched", "Search", "Saved").forEach { label ->
            composeRule
                .onNode(isTab and hasContentDescription(label))
                .assertWidthIsAtLeast(MIN_TOUCH_TARGET)
                .assertHeightIsAtLeast(MIN_TOUCH_TARGET)
        }
        assertIndependentTargets()
        val first = composeRule.onNode(isTab and hasContentDescription("Home")).getUnclippedBoundsInRoot()
        val last = composeRule.onNode(isTab and hasContentDescription("Saved")).getUnclippedBoundsInRoot()
        assertEquals(0f, first.left.value, 0.5f)
        assertEquals(248f, last.right.value, 0.5f)
        val layouts = mutableListOf<TextLayoutResult>()
        composeRule
            .onNode(hasText("Watched"), useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { getLayout -> getLayout(layouts) }
        assertEquals(1, layouts.size)
        org.junit.Assert.assertFalse(
            "The enlarged label must remain fully readable",
            layouts.single().hasVisualOverflow,
        )
        repeat(layouts.single().lineCount) { line ->
            org.junit.Assert.assertFalse("No label line may be ellipsized", layouts.single().isLineEllipsized(line))
        }
        composeRule.onNode(isTab and hasContentDescription("Saved")).performClick().assertIsSelected()
        assertEquals(1, calls)
    }

    private fun assertIndependentTargets() {
        val bounds =
            listOf("Home", "Watched", "Boards", "Search", "Saved").map { label ->
                composeRule.onNode(isTab and hasContentDescription(label)).getUnclippedBoundsInRoot()
            }
        bounds.zipWithNext().forEach { (left, right) ->
            org.junit.Assert.assertTrue("Neighboring navigation targets must not overlap", left.right <= right.left)
        }
    }
}
