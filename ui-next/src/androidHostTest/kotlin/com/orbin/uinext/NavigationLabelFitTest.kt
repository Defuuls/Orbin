package com.orbin.uinext

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The rule for primary navigation at 200% text: a visible label never wraps or clips, labels show on every tab
 * or none, and every tab always exposes its label, as its spoken name and, when icon-only, as a long-press tooltip.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w600dp-h900dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NavigationLabelFitTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val labels = listOf("Boards", "Feed", "Library", "Settings")
    private val destinations =
        listOf(NextDestination.BOARDS, NextDestination.FEED, NextDestination.LIBRARY, NextDestination.SETTINGS)

    private lateinit var measurer: TextMeasurer
    private lateinit var style: TextStyle
    private lateinit var density: Density

    @Test
    fun noLabelWrapsAndEveryTabExposesItsLabelAt200Percent() {
        var surface by mutableStateOf<@Composable () -> Unit>({})
        composeRule.setContent {
            NextTheme(fontScale = MAX_SCALE) {
                measurer = rememberTextMeasurer()
                style = tabLabelStyle()
                density = LocalDensity.current
                surface()
            }
        }
        val cases =
            BAR_WIDTHS.map { width ->
                "bar ${width}dp" to @Composable {
                    Box(Modifier.width(width.dp)) {
                        OrbinBottomBar(selected = NextDestination.FEED, onSelect = {}, watchedBadge = 3)
                    }
                }
            } + ("rail" to @Composable { OrbinSideBar(selected = NextDestination.FEED, onSelect = {}) })
        cases.forEach { (name, content) ->
            composeRule.runOnIdle { surface = content }
            composeRule.waitForIdle()
            assertTabsFollowTheRule(name)
        }
    }

    @Test
    fun iconOnlyTabsShowTheirLabelOnLongPress() {
        composeRule.setContent {
            NextTheme(fontScale = MAX_SCALE) {
                Box(Modifier.width(BAR_WIDTHS.first().dp)) {
                    OrbinBottomBar(selected = NextDestination.FEED, onSelect = {})
                }
            }
        }
        composeRule.onAllNodesWithTag(OrbinNavigationTags.LABEL, useUnmergedTree = true).assertCountEquals(0)
        composeRule.onAllNodesWithTag(OrbinNavigationTags.TOOLTIP, useUnmergedTree = true).assertCountEquals(0)
        composeRule.onNodeWithTag(OrbinNavigationTags.ENTRY + NextDestination.LIBRARY.name).performTouchInput {
            longClick()
        }
        composeRule.onNodeWithTag(OrbinNavigationTags.TOOLTIP, useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun labelledTabsHaveNoTooltip() {
        composeRule.setContent {
            NextTheme(fontScale = 1f) {
                Box(Modifier.width(390.dp)) { OrbinBottomBar(selected = NextDestination.FEED, onSelect = {}) }
            }
        }
        composeRule.onAllNodesWithTag(OrbinNavigationTags.LABEL, useUnmergedTree = true).assertCountEquals(4)
        composeRule.onNodeWithTag(OrbinNavigationTags.ENTRY + NextDestination.LIBRARY.name).performTouchInput {
            longClick()
        }
        composeRule.onAllNodesWithTag(OrbinNavigationTags.TOOLTIP, useUnmergedTree = true).assertCountEquals(0)
    }

    private fun assertTabsFollowTheRule(case: String) {
        destinations.forEachIndexed { index, destination ->
            val tab = composeRule.onNodeWithTag(OrbinNavigationTags.ENTRY + destination.name).fetchSemanticsNode()
            assertEquals(
                "$case: ${destination.name} exposes its label",
                listOf(labels[index]),
                tab.config.getOrNull(SemanticsProperties.ContentDescription),
            )
        }
        val shown =
            composeRule.onAllNodesWithTag(OrbinNavigationTags.LABEL, useUnmergedTree = true).fetchSemanticsNodes()
        assertTrue("$case: labels show on every tab or none, got ${shown.size}", shown.isEmpty() || shown.size == 4)
        shown.forEachIndexed { index, node ->
            val line = measurer.measure(labels[index], style, density = density, softWrap = false, maxLines = 1)
            assertTrue(
                "$case: '${labels[index]}' needs ${line.size.width}px on one line but has ${node.size.width}px",
                line.size.width <= node.size.width,
            )
            assertTrue(
                "$case: '${labels[index]}' is ${node.size.height}px tall, more than one ${line.size.height}px line",
                node.size.height <= line.size.height,
            )
        }
    }

    private companion object {
        const val MAX_SCALE = 2f
        val BAR_WIDTHS = listOf(248, 320, 390, 600)
    }
}
