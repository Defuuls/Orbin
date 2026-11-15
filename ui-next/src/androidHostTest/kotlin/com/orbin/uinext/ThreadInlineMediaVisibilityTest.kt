package com.orbin.uinext

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w390dp-h844dp-mdpi")
class ThreadInlineMediaVisibilityTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun clippingScrollAndLifecycleGatePlayerCompositionToOneVisibleTile() {
        var foreground by mutableStateOf(true)
        compose.setContent {
            val state = rememberThreadInlineMediaState("site/board/thread")
            Column(
                Modifier
                    .fillMaxWidth()
                    .height(260.dp)
                    .clipToBounds()
                    .verticalScroll(rememberScrollState()),
            ) {
                listOf("first", "second", "offscreen").forEach { id ->
                    ThreadInlineMediaFrame(
                        state,
                        id,
                        foreground,
                        Modifier.fillMaxWidth().height(200.dp).testTag("tile-$id"),
                    ) { active ->
                        if (active) Box(Modifier.fillMaxSize().testTag("player-$id"))
                    }
                }
            }
        }
        compose.onAllNodesWithTag("player-first").assertCountEquals(1)
        compose.onAllNodesWithTag("player-second").assertCountEquals(0)
        compose.onAllNodesWithTag("player-offscreen").assertCountEquals(0)
        compose.onNodeWithTag("tile-second").performScrollTo()
        compose.onAllNodesWithTag("player-first").assertCountEquals(0)
        compose.onAllNodesWithTag("player-second").assertCountEquals(1)
        compose.runOnIdle { foreground = false }
        compose.onAllNodesWithTag("player-second").assertCountEquals(0)
        compose.onAllNodesWithTag("player-offscreen").assertCountEquals(0)
    }
}
