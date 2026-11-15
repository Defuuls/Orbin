package com.orbin.uinext

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.MouseButton
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ReaderInputTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun commandShortcutsUseRealCallbacksAndIgnoreFocusedEditors() {
        var refresh = 0
        var search = 0
        val destinations = mutableListOf<NextDestination>()
        compose.setContent {
            var text by remember { mutableStateOf("") }
            NextTheme(showSavedMediaTab = false) {
                Column(
                    Modifier
                        .fillMaxSize()
                        .readerKeyboardShortcuts(
                            onRefresh = { refresh++ },
                            onSearch = { search++ },
                            onDestination = { destinations.add(it) },
                        ).testTag("shortcuts"),
                ) {
                    NextTextField(text, { text = it }, modifier = Modifier.testTag("editor"))
                }
            }
        }
        compose.onNodeWithTag("shortcuts").performKeyInput {
            keyDown(Key.CtrlLeft)
            keyDown(Key.R)
            keyUp(Key.R)
            keyUp(Key.CtrlLeft)
            keyDown(Key.MetaLeft)
            keyDown(Key.F)
            keyUp(Key.F)
            keyUp(Key.MetaLeft)
            keyDown(Key.CtrlLeft)
            keyDown(Key.Three)
            keyUp(Key.Three)
            keyUp(Key.CtrlLeft)
        }
        compose.runOnIdle {
            assertEquals(1, refresh)
            assertEquals(1, search)
            assertEquals(listOf(NextDestination.SETTINGS), destinations)
        }
        compose.onNodeWithTag("editor").performClick()
        compose.onNodeWithTag("editor").performKeyInput {
            keyDown(Key.CtrlLeft)
            keyDown(Key.R)
            keyUp(Key.R)
            keyUp(Key.CtrlLeft)
        }
        compose.runOnIdle { assertEquals(1, refresh) }
    }

    @Test
    fun secondaryClickOpensExistingOptionsWithoutOpeningTheItem() {
        var opened = 0
        var options = 0
        compose.setContent {
            NextTheme {
                Box(
                    Modifier
                        .size(
                            200.dp,
                        ).nextClickable(onLongClick = { options++ }, onClick = { opened++ })
                        .testTag("item"),
                )
            }
        }
        compose.onNodeWithTag("item").performMouseInput { click(MouseButton.Secondary) }
        compose.runOnIdle {
            assertEquals(0, opened)
            assertEquals(1, options)
        }
    }

    @Test
    fun backSwipeRequiresHorizontalIntentAndLeavesChildScrollingAlone() {
        var back = 0
        var scrollable by mutableStateOf(false)
        compose.setContent {
            NextTheme {
                Box(Modifier.size(300.dp).readerBackSwipe { back++ }.testTag("page")) {
                    if (scrollable) {
                        Box(Modifier.fillMaxSize().horizontalScroll(rememberScrollState(100)).testTag("pan")) {
                            Box(Modifier.width(600.dp))
                        }
                    }
                }
            }
        }
        compose.onNodeWithTag("page").performTouchInput {
            swipe(Offset(60f, 150f), Offset(280f, 150f))
            swipe(Offset(150f, 60f), Offset(150f, 280f))
        }
        compose.runOnIdle {
            assertEquals(1, back)
            scrollable = true
        }
        compose.onNodeWithTag("pan").performTouchInput { swipe(Offset(60f, 150f), Offset(280f, 150f)) }
        compose.runOnIdle { assertEquals(1, back) }
    }
}
