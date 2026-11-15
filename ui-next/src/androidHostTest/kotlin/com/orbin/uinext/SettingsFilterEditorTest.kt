package com.orbin.uinext

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.orbin.uinext.components.NextTheme
import com.orbin.uinext.screens.SettingsFilterEditor
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SettingsFilterEditorTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun draftsAreReadOnlyUntilAddedAndExistingRulesCanBeRemoved() {
        var committed = emptyList<SettingFilterRule>()
        var writes = 0
        compose.setContent {
            var rules by remember { mutableStateOf(emptyList<SettingFilterRule>()) }
            NextTheme {
                SettingsFilterEditor(
                    SettingItem("postFilterRules", "Hidden posts", "0", filterRules = rules),
                ) { _, value ->
                    rules = value
                    committed = value
                    writes++
                }
            }
        }
        compose.onNode(hasSetTextAction()).performTextInput("crypto")
        compose.runOnIdle { assertEquals(0, writes) }
        compose.onNodeWithText("Add filter").performClick()
        compose.runOnIdle { assertEquals(listOf(SettingFilterRule(pattern = "crypto")), committed) }
        compose.onNodeWithContentDescription("Remove filter crypto").performClick()
        compose.runOnIdle { assertEquals(emptyList<SettingFilterRule>(), committed) }
    }

    @Test
    fun invalidRegexCannotBeCommitted() {
        var writes = 0
        compose.setContent {
            NextTheme {
                SettingsFilterEditor(
                    SettingItem("postFilterRules", "Hidden posts", "0", filterRules = emptyList()),
                ) { _, _ -> writes++ }
            }
        }
        compose.onNodeWithText("Regular expression").performClick()
        compose.onNode(hasSetTextAction()).performTextInput("(")
        compose.onNodeWithText("Add filter").performClick()
        compose.onNodeWithText("Enter a valid regular expression.").assertExists()
        compose.runOnIdle { assertEquals(0, writes) }
    }
}
