package com.orbin.uinext

import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.AccessibilityManager
import androidx.compose.ui.platform.LocalAccessibilityManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ControlsAccessibilityTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `refresh accessibility action respects the current busy state`() {
        var refreshing by mutableStateOf(false)
        var refreshes = 0
        composeRule.setContent {
            NextTheme {
                NextPullToRefresh(
                    isRefreshing = refreshing,
                    onRefresh = { refreshes++ },
                    modifier = Modifier.testTag("refresh"),
                ) {
                    Text("Feed content")
                }
            }
        }
        val action =
            composeRule.onNodeWithTag("refresh").fetchSemanticsNode()
                .config[SemanticsActions.CustomActions].single()
        assertEquals("Refresh", action.label)
        composeRule.runOnIdle {
            assertTrue(action.action())
            assertEquals(1, refreshes)
            refreshing = true
        }
        composeRule.waitForIdle()
        composeRule.runOnIdle {
            assertFalse(action.action())
            assertEquals(1, refreshes)
            refreshing = false
        }
        composeRule.waitForIdle()
        composeRule.runOnIdle {
            assertTrue(action.action())
            assertEquals(2, refreshes)
        }
    }

    @Test
    fun `snackbar action respects the accessible timeout`() {
        assertAccessibleSnackbar(actionLabel = "Undo", withDismissAction = false)
    }

    @Test
    fun `dismiss-only snackbar respects the accessible timeout`() {
        assertAccessibleSnackbar(actionLabel = null, withDismissAction = true)
    }

    @Test
    fun `message-only snackbar requests a text timeout without controls`() {
        assertAccessibleSnackbar(actionLabel = null, withDismissAction = false)
    }

    private fun assertAccessibleSnackbar(
        actionLabel: String?,
        withDismissAction: Boolean,
    ) {
        val host = NextSnackbarHostState()
        var requestedTimeout = -1L
        var requestedIcons = true
        var requestedText = false
        var requestedControls = false
        val manager =
            object : AccessibilityManager {
                override fun calculateRecommendedTimeoutMillis(
                    originalTimeoutMillis: Long,
                    containsIcons: Boolean,
                    containsText: Boolean,
                    containsControls: Boolean,
                ): Long {
                    requestedTimeout = originalTimeoutMillis
                    requestedIcons = containsIcons
                    requestedText = containsText
                    requestedControls = containsControls
                    return 10_000L
                }
            }
        composeRule.setContent {
            CompositionLocalProvider(LocalAccessibilityManager provides manager) {
                NextTheme {
                    NextSnackbarHost(hostState = host)
                    LaunchedEffect(Unit) {
                        host.showSnackbar("Saved", actionLabel, withDismissAction)
                    }
                }
            }
        }
        composeRule.onNodeWithContentDescription("Saved").assert(
            SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite),
        )
        composeRule.runOnIdle {
            assertEquals(4_000L, requestedTimeout)
            assertFalse(requestedIcons)
            assertTrue(requestedText)
            assertEquals(actionLabel != null || withDismissAction, requestedControls)
        }
        composeRule.mainClock.autoAdvance = false
        composeRule.mainClock.advanceTimeBy(4_001L)
        composeRule.runOnIdle { assertNotNull(host.current) }
        composeRule.mainClock.advanceTimeBy(10_000L)
        composeRule.runOnIdle { assertNull(host.current) }
    }
}
