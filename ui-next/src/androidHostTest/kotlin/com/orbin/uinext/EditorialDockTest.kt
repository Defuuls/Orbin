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
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.performClick
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

    /** Replaces largeTextKeepsLabelsReadableAndTargetsReachable for the navigation drawer. */
    @Test
    fun largeTextKeepsDrawerLabelsReadableAndTargetsReachable() {
        composeRule.setContent {
            NextTheme(fontScale = 2f) {
                // Match the real-font renderer and viewport used by the screenshot suite.
                Surface(modifier = Modifier.size(411.dp, 891.dp)) {
                    Box(contentAlignment = Alignment.BottomCenter) {
                        ReaderDrawer(NextDestination.FEED, {})
                    }
                }
            }
        }
        listOf("Home", "Boards", "Watched", "Search", "Saved").forEach { label ->
            composeRule
                .onNode(isTab and hasText(label))
                .assertWidthIsAtLeast(MIN_TOUCH_TARGET)
                .assertHeightIsAtLeast(MIN_TOUCH_TARGET)
        }
        assertIndependentTargets()
    }

    /** Replaces choosingATabUpdatesSelectionWithOneCallback. */
    @Test
    fun choosingADrawerEntryUpdatesSelectionWithOneCallback() {
        var calls = 0
        composeRule.setContent {
            var selected by remember { mutableStateOf(NextDestination.FEED) }
            NextTheme {
                ReaderDrawer(
                    selected = selected,
                    onSelect = {
                        selected = it
                        calls++
                    },
                )
            }
        }
        composeRule.onNode(isTab and hasText("Home")).assertIsSelected()
        composeRule
            .onNode(isTab and hasText("Saved"))
            .performClick()
            .assertIsSelected()
        composeRule.onNode(isTab and hasText("Home")).assertIsNotSelected()
        assertEquals(1, calls)
    }

    /** Replaces narrowViewportKeepsAllFiveIndependent48dpTargets. */
    @Test
    fun narrowViewportKeepsIndependentFullWidthDrawerTargets() {
        var calls = 0
        composeRule.setContent {
            var selected by remember { mutableStateOf(NextDestination.FEED) }
            NextTheme(fontScale = 2f) {
                Surface(modifier = Modifier.size(248.dp, 400.dp)) {
                    Box(contentAlignment = Alignment.BottomCenter) {
                        ReaderDrawer(selected, {
                            selected = it
                            calls++
                        })
                    }
                }
            }
        }
        listOf("Home", "Boards", "Watched", "Search", "Saved").forEach { label ->
            composeRule
                .onNode(isTab and hasText(label))
                .assertWidthIsAtLeast(MIN_TOUCH_TARGET)
                .assertHeightIsAtLeast(MIN_TOUCH_TARGET)
        }
        assertIndependentTargets()
        val first = composeRule.onNode(isTab and hasText("Home")).getUnclippedBoundsInRoot()
        val last = composeRule.onNode(isTab and hasText("Saved")).getUnclippedBoundsInRoot()
        assertEquals(0f, first.left.value, 0.5f)
        assertEquals(248f, last.right.value, 0.5f)
        composeRule
            .onAllNodes(hasText("Watched"), useUnmergedTree = true)
            .assertCountEquals(1)
        composeRule.onNode(isTab and hasText("Saved")).performClick().assertIsSelected()
        assertEquals(1, calls)
    }

    private fun assertIndependentTargets() {
        val bounds =
            listOf("Home", "Watched", "Boards", "Search", "Saved").map { label ->
                composeRule.onNode(isTab and hasText(label)).getUnclippedBoundsInRoot()
            }
        bounds.zipWithNext().forEach { (left, right) ->
            org.junit.Assert.assertTrue("Neighboring navigation targets must not overlap", left.bottom <= right.top)
        }
    }
}
