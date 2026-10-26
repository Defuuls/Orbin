package com.orbin.uinext

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SettingsChoiceAccessibilityTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `theme choices expose selection and keep it after repeated selection`() {
        composeRule.setContent {
            var selected by remember { mutableIntStateOf(0) }
            NextTheme {
                SettingsScreen(
                    groups =
                        listOf(
                            "" to
                                listOf(
                                    SettingItem(
                                        id = "colorTheme",
                                        label = "Color theme",
                                        value = "Current theme",
                                        kind = SettingKind.CHOICE,
                                        options = listOf("Blue", "Teal"),
                                        selected = selected,
                                    ),
                                ),
                        ),
                    expandedId = "colorTheme",
                    showRail = false,
                    onSelectOption = { _, index -> selected = index },
                )
            }
        }

        val radio = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton)
        composeRule.onNodeWithText("Blue").assert(radio).assertIsSelected()
        composeRule
            .onNodeWithText("Teal")
            .assert(radio)
            .assertIsNotSelected()
            .performClick()
        composeRule.onNodeWithText("Teal").assertIsSelected().performClick()
        composeRule.onNodeWithText("Teal").assertIsSelected()
        composeRule.onNodeWithText("Blue").assertIsNotSelected()
    }
}
