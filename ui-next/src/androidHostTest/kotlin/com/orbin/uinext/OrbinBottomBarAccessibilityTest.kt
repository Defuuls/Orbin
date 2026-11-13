package com.orbin.uinext

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** UIKit concatenates descendant text with a parent label; a primary tab must speak its label once. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "en-rUS")
class OrbinBottomBarAccessibilityTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun downloadsHasOneLabelAnAccessibleLiveCountAndRealTabSelection() {
        var selected by mutableStateOf(NextDestination.FEED)
        var active by mutableIntStateOf(3)
        composeRule.setContent {
            NextTheme(platform = NextPlatform.IOS) {
                OrbinBottomBar(selected = selected, onSelect = { selected = it }, downloadBadge = active)
            }
        }
        composeRule
            .onNodeWithContentDescription("Downloads")
            .assertContentDescriptionEquals("Downloads")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "3 downloads in progress"))
            .performClick()
            .assertIsSelected()
        composeRule.onAllNodesWithText("Downloads", useUnmergedTree = true).assertCountEquals(0)
        composeRule.onAllNodesWithText("3", useUnmergedTree = true).assertCountEquals(0)

        composeRule.runOnIdle { active = 1 }
        composeRule
            .onNodeWithContentDescription("Downloads")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "1 download in progress"))
    }
}
