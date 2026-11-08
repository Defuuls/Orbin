package com.orbin.uinext

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PlatformSwitchAccessibilityTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val isSwitch = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch)

    @Test
    fun androidSwitchReportsAndChangesItsCheckedState() = assertSwitchState(NextPlatform.ANDROID)

    @Test
    fun iosSwitchReportsItsStateAndHasAnAccessibleTarget() = assertSwitchState(NextPlatform.IOS)

    @Test
    fun passiveSwitchDoesNotExposeAnotherClickTarget() {
        composeRule.setContent {
            NextTheme(platform = NextPlatform.IOS) {
                PlatformSwitch(checked = false, onCheckedChange = null)
            }
        }
        composeRule.onAllNodes(hasClickAction()).assertCountEquals(0)
    }

    private fun assertSwitchState(platform: NextPlatform) {
        var calls = 0
        composeRule.setContent {
            var checked by remember { mutableStateOf(false) }
            NextTheme(platform = platform) {
                PlatformSwitch(
                    checked = checked,
                    onCheckedChange = {
                        checked = it
                        calls++
                    },
                )
            }
        }
        val node = composeRule.onNode(isSwitch)
        node.assertIsOff().performClick().assertIsOn()
        assertEquals(1, calls)
        if (platform == NextPlatform.IOS) {
            node.assertWidthIsAtLeast(MIN_TOUCH_TARGET).assertHeightIsAtLeast(MIN_TOUCH_TARGET)
        }
    }
}
