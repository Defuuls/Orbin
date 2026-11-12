package com.orbin.uinext

import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import com.orbin.uinext.tokens.ReaderSize
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** An open drawer consumes Back before the containing route or two-pane reader can handle it. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xhdpi")
class ReaderDrawerBackTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun hardwareBackClosesTheDrawerBeforeLeavingTheCurrentRoute() {
        var routeBacks = 0
        compose.setContent {
            NextTheme {
                ReaderScaffold("Boards", destination = NextDestination.BOARDS, onDestination = {}) {
                    BackHandler { routeBacks++ }
                }
            }
        }
        openDrawerAndAwaitItsVisiblePosition()
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.runOnIdle { assertThat(routeBacks).isEqualTo(0) }
        compose.waitUntil(timeoutMillis = 1_000) { !compose.onNodeWithTag(ReaderDrawerTags.DRAWER).isDisplayed() }
        compose.waitForIdle()
        compose.onNodeWithTag(ReaderDrawerTags.DRAWER).assertIsNotDisplayed()
        compose.runOnIdle { assertThat(routeBacks).isEqualTo(0) }

        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.runOnIdle { assertThat(routeBacks).isEqualTo(1) }
    }

    @Test
    @Config(sdk = [35], qualifiers = "w800dp-h600dp-mdpi")
    fun aFullWindowDrawerCoversTheLiveDetailPaneAndConsumesBackBeforeItsHandler() {
        var threadBacks = 0
        var threadClicks = 0
        compose.setContent {
            NextTheme {
                ReaderScaffold("Boards", destination = NextDestination.BOARDS, onDestination = {}, drawAppBar = false) {
                    val openDrawer = LocalReaderOpenDrawer.current!!
                    ReaderTwoPane(
                        catalogWidth = 332.dp,
                        threadOpen = true,
                        listContent = {
                            BoardScreen(
                                board = "/g/",
                                description = "Technology",
                                itemCount = 0,
                                rowAt = { null },
                                navigation = ReaderNavigation.MENU,
                                onNavigation = openDrawer,
                            )
                        },
                        detailContent = {
                            BackHandler { threadBacks++ }
                            Box(Modifier.testTag("live-thread-pane").fillMaxSize().nextClickable { threadClicks++ })
                        },
                    )
                }
            }
        }
        openDrawerAndAwaitItsVisiblePosition()
        compose.onNodeWithTag(ReaderDrawerTags.DRAWER).assertWidthIsEqualTo(ReaderSize.drawerMax)
        compose.onNodeWithTag("live-thread-pane").performTouchInput { click(center) }
        compose.onNodeWithTag(ReaderDrawerTags.DRAWER).assertIsNotDisplayed()
        compose.runOnIdle { assertThat(threadClicks).isEqualTo(0) }

        openDrawerAndAwaitItsVisiblePosition()
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.runOnIdle { assertThat(threadBacks).isEqualTo(0) }
        compose.waitUntil(timeoutMillis = 1_000) { !compose.onNodeWithTag(ReaderDrawerTags.DRAWER).isDisplayed() }
        compose.waitForIdle()
        compose.onNodeWithTag(ReaderDrawerTags.DRAWER).assertIsNotDisplayed()
        compose.runOnIdle { assertThat(threadBacks).isEqualTo(0) }
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.runOnIdle { assertThat(threadBacks).isEqualTo(1) }
    }

    private fun openDrawerAndAwaitItsVisiblePosition() {
        compose.onNodeWithContentDescription("Menu").performClick()
        compose.waitUntil(timeoutMillis = 1_000) {
            val bounds = compose.onNodeWithTag(ReaderDrawerTags.DRAWER).getUnclippedBoundsInRoot()
            kotlin.math.abs(bounds.left.value) <= 0.5f &&
                kotlin.math.abs(bounds.right.value - ReaderSize.drawerMax.value) <= 0.5f
        }
        compose.onNodeWithTag(ReaderDrawerTags.DRAWER).assertWidthIsEqualTo(ReaderSize.drawerMax)
    }

    @Test
    fun theNativeDrawerBackBridgeReceivesTheActivityEventBeforeTheRouteHandler() {
        var drawerBacks = 0
        var routeBacks = 0
        compose.setContent {
            BackHandler { routeBacks++ }
            ReaderDrawerBackHandler(enabled = true) { drawerBacks++ }
        }
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.runOnIdle {
            assertThat(drawerBacks).isEqualTo(1)
            assertThat(routeBacks).isEqualTo(0)
        }
    }
}
