package com.orbin.uinext

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Real rendered bounds/paint and callbacks, independent of the platform decoder. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w390dp-h844dp-mdpi")
class ReaderMediaChromeTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun toolbarUsesReferenceGeometryAndRoutesRealActions() {
        val actions = mutableListOf<String>()
        compose.setContent {
            NextTheme {
                Box(Modifier.width(390.dp).background(Color.White)) {
                    ReaderMediaToolbar {
                        ReaderMediaBack("Back") { actions += "back" }
                        ReaderMediaTitle("selected.mp4", "2/7", Modifier.weight(1f))
                        ReaderMediaAction(Icons.AutoMirrored.Filled.VolumeOff, "Unmute", { actions += "mute" })
                        ReaderMediaAction(Icons.Filled.FileDownload, "Save", { actions += "save" })
                        ReaderMediaAction(Icons.Filled.MoreVert, "More", { actions += "more" }, overflow = true)
                    }
                }
            }
        }
        val bar = compose.onNodeWithTag(ReaderMediaToolbarTags.CONTENT).fetchSemanticsNode().boundsInRoot
        assertEquals(390f, bar.width, 0.1f)
        assertEquals(56f, bar.height, 0.1f)
        val back = compose.onNodeWithContentDescription("Back").fetchSemanticsNode().boundsInRoot
        val mute = compose.onNodeWithContentDescription("Unmute").fetchSemanticsNode().boundsInRoot
        val save = compose.onNodeWithContentDescription("Save").fetchSemanticsNode().boundsInRoot
        val more = compose.onNodeWithContentDescription("More").fetchSemanticsNode().boundsInRoot
        assertEquals(56f, back.width, 0.1f)
        assertEquals(50f, mute.width, 0.1f)
        assertEquals(50f, save.width, 0.1f)
        assertEquals(44f, more.width, 0.1f)
        assertTrue(back.right < mute.left)
        assertEquals(mute.right, save.left, 0.1f)
        assertEquals(save.right, more.left, 0.1f)
        assertEquals(
            72f,
            compose
                .onNodeWithText("selected.mp4")
                .fetchSemanticsNode()
                .boundsInRoot.left,
            0.1f,
        )
        val pixels = compose.onNodeWithTag(ReaderMediaToolbarTags.BAR).captureToImage().toPixelMap()
        assertEquals(120f / 255, pixels[0, 0].red, 0.01f)
        listOf("Back", "Unmute", "Save", "More").forEach {
            compose.onNodeWithContentDescription(it).performClick()
        }
        compose.runOnIdle { assertEquals(listOf("back", "mute", "save", "more"), actions) }
    }

    @Test
    fun transportKeepsSingleClipBehaviorAndExactNormalSizes() {
        val seeks = mutableListOf<Double>()
        var plays = 0
        compose.setContent {
            NextTheme {
                ReaderMediaControls(false, 20.0, 60.0, { plays++ }, { seeks += it }, Modifier.width(390.dp))
            }
        }
        val restart = compose.onNodeWithContentDescription("Restart")
        val rewind = compose.onNodeWithContentDescription("Rewind 5 seconds")
        val play = compose.onNodeWithContentDescription("Play")
        val forward = compose.onNodeWithContentDescription("Forward 15 seconds")
        val next = compose.onNodeWithContentDescription("Next")
        val bounds = listOf(restart, rewind, play, forward, next).map { it.fetchSemanticsNode().boundsInRoot }
        bounds.forEach {
            assertEquals(71f, it.width, 0.1f)
            assertEquals(52f, it.height, 0.1f)
        }
        bounds.zipWithNext().forEach { (left, right) -> assertEquals(left.right, right.left, 0.1f) }
        assertEquals(
            26f,
            compose
                .onNodeWithTag(ReaderMediaControlsTags.TIME_BAR)
                .fetchSemanticsNode()
                .boundsInRoot.height,
            0.1f,
        )
        restart.performClick()
        rewind.performClick()
        play.performClick()
        forward.performClick()
        next.assertIsNotEnabled()
        compose.onNodeWithTag(ReaderMediaControlsTags.TIME_BAR).performSemanticsAction(SemanticsActions.SetProgress) {
            it(0.5f)
        }
        compose.runOnIdle {
            assertEquals(listOf(0.0, 15.0, 35.0, 30.0), seeks)
            assertEquals(1, plays)
        }
    }

    @Test
    fun narrowControllerKeepsEveryTransportTargetInsideTheWindow() {
        compose.setContent {
            NextTheme {
                Column(Modifier.width(248.dp)) {
                    ReaderMediaControls(true, 2.0, 10.0, {}, {})
                }
            }
        }
        val panel = compose.onNodeWithTag(ReaderMediaControlsTags.PANEL).fetchSemanticsNode().boundsInRoot
        listOf("Restart", "Rewind 5 seconds", "Pause", "Forward 15 seconds", "Next").forEach { label ->
            val bounds = compose.onNodeWithContentDescription(label).fetchSemanticsNode().boundsInRoot
            assertTrue(bounds.width >= 48f)
            assertTrue(bounds.left >= panel.left)
            assertTrue(bounds.right <= panel.right + 0.1f)
        }
    }
}
