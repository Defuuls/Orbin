package com.orbin.uinext

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h640dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class OnboardingErrorRecoveryTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun longNativeErrorKeepsRetryVisibleAndTouchableInAShortPaneAtDoubleTextSize() {
        var retries = 0
        compose.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                NextTheme(platform = NextPlatform.IOS, fontScale = 2f) {
                    Box(Modifier.size(280.dp, 180.dp).testTag(ERROR_PANE)) {
                        NextError(LONG_NATIVE_ERROR, onRetry = { retries++ })
                    }
                }
            }
        }

        val pane =
            compose
                .onNodeWithTag(ERROR_PANE)
                .assertWidthIsEqualTo(280.dp)
                .assertHeightIsEqualTo(180.dp)
                .getUnclippedBoundsInRoot()
        val messageLayout = textLayout(LONG_NATIVE_ERROR)
        assertEquals(2f, messageLayout.layoutInput.density.fontScale, 0f)
        assertTrue("The native error must actually wrap across multiple rendered lines", messageLayout.lineCount > 1)
        val retry = compose.onNodeWithText("Retry").assertIsDisplayed().assertHeightIsAtLeast(MIN_TOUCH_TARGET)
        val action = retry.getUnclippedBoundsInRoot()
        assertTrue(
            "Retry must remain fully inside the short pane",
            action.top >= pane.top && action.bottom <= pane.bottom,
        )
        assertTrue(
            "Retry must remain fully inside the narrow pane",
            action.left >= pane.left && action.right <= pane.right,
        )
        retry.performTouchInput { click() }
        compose.runOnIdle { assertEquals(1, retries) }
    }

    @Test
    fun failedBoardDirectoryShowsFriendlyCopyAndRetriesWithoutSkippingMandatorySetup() {
        var boards by mutableStateOf<OnboardingBoards>(OnboardingBoards.Failed(LONG_NATIVE_ERROR))
        var retries = 0
        compose.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                NextTheme(platform = NextPlatform.IOS, fontScale = 2f) {
                    OnboardingScreen(
                        boards = boards,
                        following = emptySet(),
                        sites = listOf("4chan", "BBWChan"),
                        selectedSite = 0,
                        onSelectSite = {},
                        onFollow = { _, _ -> },
                        onRetry = {
                            retries++
                            boards = OnboardingBoards.Ready(listOf(OnboardingBoard("3", "3DCG", "3D graphics", false)))
                        },
                        onStart = {},
                        modifier = Modifier.size(280.dp, 440.dp).testTag(ONBOARDING_PANE),
                    )
                }
            }
        }

        compose.onNodeWithText("Could not load boards. Try again.").assertIsDisplayed()
        compose.onNodeWithText(LONG_NATIVE_ERROR).assertDoesNotExist()
        compose.onNodeWithText("Start browsing").assertIsNotEnabled()
        val pane =
            compose
                .onNodeWithTag(ONBOARDING_PANE)
                .assertWidthIsEqualTo(280.dp)
                .assertHeightIsEqualTo(440.dp)
                .getUnclippedBoundsInRoot()
        val retry = compose.onNodeWithText("Retry").assertIsDisplayed().assertHeightIsAtLeast(MIN_TOUCH_TARGET)
        val action = retry.getUnclippedBoundsInRoot()
        assertTrue(
            "Retry must remain fully inside the short onboarding screen",
            action.top >= pane.top && action.bottom <= pane.bottom,
        )
        assertTrue(
            "Retry must remain fully inside the narrow onboarding screen",
            action.left >= pane.left && action.right <= pane.right,
        )
        retry.performTouchInput { click() }
        compose.runOnIdle { assertEquals(1, retries) }
        compose.onNodeWithText("3DCG").assertIsDisplayed()
        compose.onNodeWithText("Could not load boards. Try again.").assertDoesNotExist()
        compose.onNodeWithText("Start browsing").assertIsNotEnabled()
    }

    private fun textLayout(text: String): TextLayoutResult {
        val layouts = mutableListOf<TextLayoutResult>()
        compose
            .onNodeWithText(text, useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        return layouts.single()
    }

    private companion object {
        const val ERROR_PANE = "onboarding-error-pane"
        const val ONBOARDING_PANE = "onboarding-recovery-pane"
        val LONG_NATIVE_ERROR =
            "Network error: Exception in http request: Error Domain=NSURLErrorDomain Code=-1005 " +
                "The network connection was lost. " +
                "NSErrorFailingURLStringKey=https://a.4cdn.org/boards.json; connection lost. ".repeat(80)
    }
}
