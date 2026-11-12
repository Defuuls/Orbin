package com.orbin.ios

import android.view.ViewConfiguration
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import com.orbin.uinext.NextTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w390dp-h844dp-mdpi")
class VideoControlsLoadingTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun preparingVideoShowsProgressAndStillLetsTheReaderPauseTheRequest() {
        var state by mutableStateOf(VideoState(loading = true))
        var toggles = 0
        compose.setContent {
            NextTheme {
                VideoControls(
                    state,
                    onTogglePlay = {
                        toggles++
                        state = state.copy(loading = false)
                    },
                    onSeekTo = {},
                    modifier = Modifier.fillMaxSize().testTag("video-controls"),
                )
            }
        }
        compose.onNodeWithContentDescription("Loading video").assertIsDisplayed()
        compose.onNodeWithContentDescription("Play video").assertDoesNotExist()
        compose.onNodeWithTag("video-controls").performTouchInput { click(center) }
        // The controls wait for a possible double tap before dispatching a single-tap pause.
        compose.mainClock.advanceTimeBy(ViewConfiguration.getDoubleTapTimeout().toLong() + 1)
        compose.onNodeWithContentDescription("Loading video").assertDoesNotExist()
        compose.onNodeWithContentDescription("Play video").assertIsDisplayed()
        compose.runOnIdle { assertEquals(1, toggles) }
    }
}
