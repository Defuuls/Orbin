package com.orbin.uinext

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The shared pane geometry and empty-state behavior from the reader specification. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w1280dp-h800dp-xhdpi")
class ReaderTwoPaneTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `a 960dp window gives the catalog 399dp and the detail 560dp around a 1dp divider`() {
        showPanes()

        val catalog = composeRule.onNodeWithTag(ReaderTwoPaneTags.CATALOG).getUnclippedBoundsInRoot()
        val divider = composeRule.onNodeWithTag(ReaderTwoPaneTags.DIVIDER).getUnclippedBoundsInRoot()
        val detail = composeRule.onNodeWithTag(ReaderTwoPaneTags.DETAIL).getUnclippedBoundsInRoot()
        assertThat(catalog.right.value - catalog.left.value).isWithin(0.5f).of(399f)
        assertThat(divider.right.value - divider.left.value).isWithin(0.5f).of(1f)
        assertThat(detail.right.value - detail.left.value).isWithin(0.5f).of(560f)
        assertThat(catalog.right.value).isWithin(0.5f).of(divider.left.value)
        assertThat(divider.right.value).isWithin(0.5f).of(detail.left.value)
    }

    @Test
    fun `the catalog cannot exceed 420dp and the detail takes the remaining window`() {
        showPanes(windowWidth = 1280.dp, catalogWidth = 600.dp)

        val catalog = composeRule.onNodeWithTag(ReaderTwoPaneTags.CATALOG).getUnclippedBoundsInRoot()
        val detail = composeRule.onNodeWithTag(ReaderTwoPaneTags.DETAIL).getUnclippedBoundsInRoot()
        assertThat(catalog.right.value - catalog.left.value).isWithin(0.5f).of(420f)
        assertThat(detail.right.value - detail.left.value).isWithin(0.5f).of(859f)
    }

    @Test
    fun `the empty message is centered in the detail pane and disappears when a thread opens`() {
        var threadOpen by mutableStateOf(false)
        showPanes(threadOpen = { threadOpen })

        val detail = composeRule.onNodeWithTag(ReaderTwoPaneTags.DETAIL).getUnclippedBoundsInRoot()
        val message = composeRule.onNodeWithText("Select a thread").assertIsDisplayed().getUnclippedBoundsInRoot()
        assertThat((message.left.value + message.right.value) / 2)
            .isWithin(0.5f)
            .of((detail.left.value + detail.right.value) / 2)
        assertThat((message.top.value + message.bottom.value) / 2)
            .isWithin(0.5f)
            .of((detail.top.value + detail.bottom.value) / 2)
        composeRule.onNodeWithText("Thread content").assertDoesNotExist()

        composeRule.runOnIdle { threadOpen = true }

        composeRule.onNodeWithText("Select a thread").assertDoesNotExist()
        composeRule.onNodeWithText("Thread content").assertIsDisplayed()
    }

    @Test
    fun `detail content stays mounted while no thread is open and across thread selection`() {
        var threadOpen by mutableStateOf(false)
        var mounts = 0
        var disposals = 0
        showPanes(threadOpen = { threadOpen }) {
            DisposableEffect(Unit) {
                mounts++
                onDispose { disposals++ }
            }
            Text("Thread content")
        }

        composeRule.runOnIdle {
            assertEquals(1, mounts)
            assertEquals(0, disposals)
            threadOpen = true
        }
        composeRule.onNodeWithText("Thread content").assertIsDisplayed()

        composeRule.runOnIdle { threadOpen = false }

        composeRule.onNodeWithText("Select a thread").assertIsDisplayed()
        composeRule.runOnIdle {
            assertEquals(1, mounts)
            assertEquals(0, disposals)
        }
    }

    @Test
    fun `the empty pane neither exposes nor activates the covered detail controls`() {
        var threadOpen by mutableStateOf(false)
        var clicks = 0
        showPanes(threadOpen = { threadOpen }) {
            Box(Modifier.fillMaxSize().clickable { clicks++ }) {
                Text("Thread action")
            }
        }

        composeRule.onNodeWithText("Thread action").assertDoesNotExist()
        composeRule.onNodeWithTag(ReaderTwoPaneTags.DETAIL).performTouchInput { click(center) }
        composeRule.runOnIdle {
            assertEquals(0, clicks)
            threadOpen = true
        }

        composeRule.onNodeWithText("Thread action").assertIsDisplayed()
        composeRule.onNodeWithTag(ReaderTwoPaneTags.DETAIL).performTouchInput { click(center) }

        composeRule.runOnIdle { assertEquals(1, clicks) }
    }

    private fun showPanes(
        windowWidth: Dp = 960.dp,
        catalogWidth: Dp = 399.dp,
        threadOpen: () -> Boolean = { true },
        detailContent: @Composable () -> Unit = { Text("Thread content") },
    ) {
        composeRule.setContent {
            NextTheme {
                Box(Modifier.size(windowWidth, 600.dp)) {
                    ReaderTwoPane(
                        catalogWidth = catalogWidth,
                        threadOpen = threadOpen(),
                        listContent = { Text("Catalog") },
                        detailContent = detailContent,
                    )
                }
            }
        }
    }
}
