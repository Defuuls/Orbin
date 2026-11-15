package com.orbin.uinext

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Exercise real navigation panes and content as the shared host crosses its wide breakpoint. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "en-rUS-w960dp-h800dp-mdpi")
class OrbinAdaptiveNavigationTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val isTab = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)

    @Test
    fun widePrimaryPagesReserve112DpAndReplaceTheEmbeddedBottomTabsAt840Dp() {
        var width by mutableStateOf(839.dp)
        var enabled by mutableStateOf(true)
        compose.setContent { NextTheme { FeedHost(width = width, enabled = enabled) } }

        compose.onNodeWithTag(OrbinNavigationTags.RAIL).assertDoesNotExist()
        compose.onNodeWithTag(OrbinNavigationTags.BODY).assertWidthIsEqualTo(839.dp)
        tabsInside(OrbinNavigationTags.BODY).assertCountEquals(4)

        compose.runOnIdle { width = 840.dp }

        compose.onNodeWithTag(OrbinNavigationTags.RAIL).assertWidthIsEqualTo(112.dp)
        compose.onNodeWithTag(OrbinNavigationTags.BODY).assertWidthIsEqualTo(728.dp)
        tabsInside(OrbinNavigationTags.RAIL).assertCountEquals(4)
        tabsInside(OrbinNavigationTags.BODY).assertCountEquals(0)
        assertRailPrecedesBody()

        // The host disables adaptation for setup, locked screens and full-screen media.
        compose.runOnIdle { enabled = false }

        compose.onNodeWithTag(OrbinNavigationTags.RAIL).assertDoesNotExist()
        compose.onNodeWithTag(OrbinNavigationTags.BODY).assertWidthIsEqualTo(840.dp)
        tabsInside(OrbinNavigationTags.BODY).assertCountEquals(4)
    }

    @Test
    fun rtlKeepsTheRailAtThePhysicalLeftWithAccessibleTabsAndRealSelectionCallbacks() {
        var selected by mutableStateOf(NextDestination.FEED)
        val selections = mutableListOf<NextDestination>()
        compose.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                NextTheme {
                    FeedHost(
                        width = 840.dp,
                        selected = selected,
                        onSelect = {
                            selections += it
                            selected = it
                        },
                    )
                }
            }
        }

        assertRailPrecedesBody()
        tabsInside(OrbinNavigationTags.RAIL).assertCountEquals(4)
        tabsInside(OrbinNavigationTags.BODY).assertCountEquals(0)
        for (label in listOf("Boards", "Feed", "Library", "Settings")) {
            val target = compose.onNode(isTab and hasContentDescription(label))
            target.assertIsDisplayed().assertContentDescriptionEquals(label)
            val bounds = target.getUnclippedBoundsInRoot()
            assertTrue("$label has an accessible target width", (bounds.right - bounds.left) >= 48.dp)
            assertTrue("$label has an accessible target height", (bounds.bottom - bounds.top) >= 48.dp)
        }
        compose
            .onNode(isTab and hasContentDescription("Library"))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "3 downloads in progress"))

        compose.onNode(isTab and hasContentDescription("Settings")).performClick().assertIsSelected()
        compose.onNode(isTab and hasContentDescription("Settings")).performClick()
        compose.onNode(isTab and hasContentDescription("Library")).performClick().assertIsSelected()
        compose.runOnIdle {
            assertEquals(listOf(NextDestination.SETTINGS, NextDestination.LIBRARY), selections)
        }
    }

    @Test
    fun anUncommittedBodyDraftSurvivesBreakpointAndEnabledChanges() {
        var width by mutableStateOf(839.dp)
        var enabled by mutableStateOf(true)
        compose.setContent {
            NextTheme {
                OrbinAdaptiveNavigation(
                    enabled = enabled,
                    modifier = Modifier.width(width).height(600.dp),
                    navigation = { OrbinSideBar(selected = NextDestination.FEED, onSelect = {}) },
                ) {
                    // Intentionally ordinary remember: resizing must retain the live body instance.
                    var draft by remember { mutableStateOf("") }
                    BasicTextField(draft, { draft = it }, Modifier.testTag(DRAFT))
                }
            }
        }
        compose.onNodeWithTag(DRAFT).performTextInput("An unfinished reply")

        compose.runOnIdle { width = 840.dp }
        compose.onNodeWithTag(OrbinNavigationTags.RAIL).assertExists()
        compose.onNodeWithTag(DRAFT).assertTextEquals("An unfinished reply")
        compose.onNodeWithTag(DRAFT).performTextInput(" survives")

        compose.runOnIdle { enabled = false }
        compose.onNodeWithTag(OrbinNavigationTags.RAIL).assertDoesNotExist()
        compose.onNodeWithTag(DRAFT).assertTextEquals("An unfinished reply survives")

        compose.runOnIdle {
            enabled = true
            width = 839.dp
        }
        compose.onNodeWithTag(DRAFT).assertTextEquals("An unfinished reply survives")
    }

    @Test
    fun theRailOwnsTheLeftSafeInsetWhileTheBodyStillClearsTheRightInset() {
        compose.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                NextTheme {
                    OrbinAdaptiveNavigation(
                        enabled = true,
                        modifier = Modifier.width(960.dp).height(600.dp).testTag(HOST),
                        navigation = { OrbinSideBar(selected = NextDestination.FEED, onSelect = {}) },
                    ) {
                        Box(
                            Modifier.fillMaxSize().windowInsetsPadding(
                                WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal),
                            ),
                        ) {
                            Box(Modifier.fillMaxSize().testTag(SAFE_BODY))
                        }
                    }
                }
            }
        }
        applyHorizontalSafeInsets(left = 24.dp, right = 16.dp)

        compose.onNodeWithTag(OrbinNavigationTags.RAIL).assertWidthIsEqualTo(136.dp)
        compose.onNodeWithTag(OrbinNavigationTags.BODY).assertWidthIsEqualTo(824.dp)
        assertRailPrecedesBody()
        val body = compose.onNodeWithTag(OrbinNavigationTags.BODY).getUnclippedBoundsInRoot()
        val safe = compose.onNodeWithTag(SAFE_BODY).getUnclippedBoundsInRoot()
        assertEquals("The rail consumes the left inset once", body.left.value, safe.left.value, 0.5f)
        assertEquals("The body still applies its right safe inset", body.right.value - 16f, safe.right.value, 0.5f)
    }

    @Composable
    private fun FeedHost(
        width: Dp,
        enabled: Boolean = true,
        selected: NextDestination = NextDestination.FEED,
        onSelect: (NextDestination) -> Unit = {},
    ) {
        OrbinAdaptiveNavigation(
            enabled = enabled,
            modifier = Modifier.width(width).height(600.dp).testTag(HOST),
            navigation = { OrbinSideBar(selected = selected, onSelect = onSelect, downloadBadge = 3) },
        ) {
            FeedScreen(
                rows = emptyList(),
                onOpenBoards = { onSelect(NextDestination.BOARDS) },
                onOpenDownloads = { onSelect(NextDestination.DOWNLOADS) },
                onSettings = { onSelect(NextDestination.SETTINGS) },
                downloadBadge = 3,
            )
        }
    }

    private fun tabsInside(tag: String) = compose.onAllNodes(isTab and hasAnyAncestor(hasTestTag(tag)))

    private fun assertRailPrecedesBody() {
        val host = compose.onNodeWithTag(HOST).getUnclippedBoundsInRoot()
        val rail = compose.onNodeWithTag(OrbinNavigationTags.RAIL).getUnclippedBoundsInRoot()
        val body = compose.onNodeWithTag(OrbinNavigationTags.BODY).getUnclippedBoundsInRoot()
        assertEquals("Navigation stays at the physical left", host.left.value, rail.left.value, 0.5f)
        assertEquals("The body starts after the reserved rail", rail.right.value, body.left.value, 0.5f)
        assertEquals("The body uses the rest of the viewport", host.right.value, body.right.value, 0.5f)
    }

    private fun applyHorizontalSafeInsets(
        left: Dp,
        right: Dp,
    ) {
        val decor = compose.activity.window.decorView
        val bars = with(compose.density) { Insets.of(left.roundToPx(), 0, right.roundToPx(), 0) }
        val insets = WindowInsetsCompat.Builder().setInsets(WindowInsetsCompat.Type.systemBars(), bars).build()
        compose.runOnUiThread { ViewCompat.dispatchApplyWindowInsets(decor, insets) }
        compose.waitForIdle()
    }

    private companion object {
        const val HOST = "adaptive-navigation-host"
        const val DRAFT = "adaptive-navigation-draft"
        const val SAFE_BODY = "adaptive-navigation-safe-body"
    }
}
