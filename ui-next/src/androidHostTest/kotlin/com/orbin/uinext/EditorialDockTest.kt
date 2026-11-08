package com.orbin.uinext

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xhdpi")
class EditorialDockTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val isTab = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)

    @Test
    fun largeTextKeepsLabelsReadableAndTargetsReachable() {
        composeRule.setContent {
            NextTheme(fontScale = 2f) {
                DestinationPill(NextDestination.FEED, {})
            }
        }
        listOf("Feed", "Downloads", "Boards").forEach { label ->
            composeRule
                .onNode(isTab and hasText(label))
                .assertWidthIsAtLeast(MIN_TOUCH_TARGET)
                .assertHeightIsAtLeast(MIN_TOUCH_TARGET)
            composeRule
                .onNodeWithText(label, useUnmergedTree = true)
                .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { action ->
                    val layouts = mutableListOf<TextLayoutResult>()
                    action(layouts)
                    assertFalse(layouts.single().hasVisualOverflow)
                }
        }
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
        composeRule.onNode(isTab and hasText("Feed")).assertIsSelected()
        composeRule
            .onNode(isTab and hasText("Downloads"))
            .performClick()
            .assertIsSelected()
        composeRule.onNode(isTab and hasText("Feed")).assertIsNotSelected()
        assertEquals(1, calls)
    }
}
