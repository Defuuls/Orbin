package com.orbin.uinext

import androidx.compose.material3.Text
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createComposeRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Page restoration must retain explicit choices without reviving an obsolete playback/cover policy. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w390dp-h844dp-mdpi")
class FeedPlaybackStateTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `manual play and mute choices survive page disposal but remain source qualified`() {
        var visible by mutableStateOf(true)
        var identity by mutableStateOf("fourchan/g/7/file")
        var values = true to true
        var updateChoices: (() -> Unit)? = null
        composeRule.setContent {
            val pages = rememberSaveableStateHolder()
            if (visible) {
                pages.SaveableStateProvider(identity) {
                    var playing by rememberFeedPlaybackChoice(identity, "playing", true)
                    var muted by rememberFeedPlaybackChoice(identity, "muted", true)
                    SideEffect {
                        values = playing to muted
                        updateChoices = {
                            playing = false
                            muted = false
                        }
                    }
                    Text("$playing/$muted")
                }
            }
        }
        composeRule.runOnIdle { requireNotNull(updateChoices).invoke() }
        composeRule.runOnIdle { assertEquals(false to false, values) }
        composeRule.runOnIdle { visible = false }
        composeRule.runOnIdle { visible = true }
        composeRule.runOnIdle { assertEquals(false to false, values) }
        composeRule.runOnIdle { identity = "bbwchan/g/7/file" }
        composeRule.runOnIdle { assertEquals(true to true, values) }
        composeRule.runOnIdle { identity = "fourchan/g/7/file" }
        composeRule.runOnIdle { assertEquals(false to false, values) }
    }

    @Test
    fun `policy changes replace saved choices across true false true page restores`() {
        var visible by mutableStateOf(true)
        var policy by mutableStateOf(true)
        var values = true to true
        var pauseAndUnmute: (() -> Unit)? = null
        composeRule.setContent {
            val pages = rememberSaveableStateHolder()
            if (visible) {
                pages.SaveableStateProvider("fourchan/g/7/file") {
                    var playing by rememberFeedPlaybackChoice("fourchan/g/7/file", "playing", policy)
                    var muted by rememberFeedPlaybackChoice("fourchan/g/7/file", "muted", policy)
                    SideEffect {
                        values = playing to muted
                        pauseAndUnmute = {
                            playing = false
                            muted = false
                        }
                    }
                    Text("$playing/$muted")
                }
            }
        }
        composeRule.runOnIdle { assertEquals(true to true, values) }
        composeRule.runOnIdle { requireNotNull(pauseAndUnmute).invoke() }
        composeRule.runOnIdle { assertEquals(false to false, values) }
        composeRule.runOnIdle { visible = false }
        composeRule.runOnIdle { policy = false }
        composeRule.runOnIdle { visible = true }
        composeRule.runOnIdle { assertEquals(false to false, values) }
        composeRule.runOnIdle { visible = false }
        composeRule.runOnIdle { policy = true }
        composeRule.runOnIdle { visible = true }
        composeRule.runOnIdle {
            assertEquals("An obsolete policy's historical manual choices must not return", true to true, values)
        }
    }
}
