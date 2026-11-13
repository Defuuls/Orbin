package com.orbin.ios

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.swipeUp
import coil3.ColorImage
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.annotation.DelicateCoilApi
import coil3.test.FakeImageLoaderEngine
import com.orbin.core.model.MediaAttachment
import com.orbin.core.model.MediaType
import com.orbin.core.model.PlaybackSettings
import com.orbin.uinext.NextTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(DelicateCoilApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w390dp-h844dp-mdpi")
class MediaViewerActionsTest {
    @get:Rule
    val compose = createComposeRule()

    @Before
    fun fakeImages() {
        val engine = FakeImageLoaderEngine.Builder().default(ColorImage(0xFF444444.toInt())).build()
        SingletonImageLoader.setSafe { context -> ImageLoader.Builder(context).components { add(engine) }.build() }
    }

    @After
    fun resetImages() = SingletonImageLoader.reset()

    @Test
    fun doubleTapSavesTheCurrentImageOnceAndKeepsTheSaveActionVisible() {
        val files = listOf(file("first"), file("selected"))
        val saved = mutableListOf<MediaAttachment>()
        compose.setContent {
            NextTheme {
                MediaViewer(files, 1, {}, onSave = {
                    saved += it
                    true
                })
            }
        }
        compose.onNodeWithTag(MediaViewerTags.ACTIVE_PAGE, true).performTouchInput { doubleClick(center) }
        compose.onNodeWithContentDescription("Added to Downloads").assertIsDisplayed()
        compose.onNodeWithTag(MediaViewerTags.ACTIVE_PAGE, true).performTouchInput { doubleClick(center) }
        compose.runOnIdle { assertEquals(listOf(files[1]), saved) }
    }

    @Test
    fun sidewaysSwipeClosesOnceWhileVerticalSwipePagesInstead() {
        var closes = 0
        compose.setContent { NextTheme { MediaViewer(listOf(file("first"), file("second")), 0, { closes++ }) } }
        compose.onNodeWithTag(MediaViewerTags.PAGER).performTouchInput { swipeUp() }
        compose.onNodeWithText("2/2").assertIsDisplayed()
        compose.runOnIdle { assertEquals(0, closes) }
        compose.onNodeWithTag(MediaViewerTags.PAGER).performTouchInput {
            swipe(center, Offset(width * 0.9f, center.y), durationMillis = 300)
        }
        compose.runOnIdle { assertEquals(1, closes) }
    }

    @Test
    fun playbackMenuProvidesBothNewRatesAndRetainsTheSelectedRate() {
        compose.setContent {
            NextTheme {
                MediaViewer(
                    listOf(file("video", MediaType.VIDEO)),
                    0,
                    {},
                    playback = PlaybackSettings(autoplay = false),
                )
            }
        }
        compose.onNodeWithContentDescription("More options").performClick()
        compose.onNodeWithText("Playback speed: 1.25×").performClick()
        compose.onNodeWithContentDescription("More options").performClick()
        compose
            .onNodeWithText(
                "Playback speed: 1.25×",
            ).assert(SemanticsMatcher.expectValue(SemanticsProperties.Selected, true))
        compose.onNodeWithText("Playback speed: 2×").performClick()
        compose.onNodeWithContentDescription("More options").performClick()
        compose
            .onNodeWithText(
                "Playback speed: 2×",
            ).assert(SemanticsMatcher.expectValue(SemanticsProperties.Selected, true))
    }

    @Test
    fun restOfThreadRemainsAvailableForTheLastFile() {
        val requested = mutableListOf<Int>()
        compose.setContent {
            NextTheme { MediaViewer(listOf(file("first"), file("last")), 1, {}, onDownloadRest = { requested += it }) }
        }
        compose.onNodeWithContentDescription("More options").performClick()
        compose.onNodeWithText("Download rest of thread").performClick()
        compose.runOnIdle { assertEquals(listOf(1), requested) }
    }

    private fun file(
        id: String,
        type: MediaType = MediaType.IMAGE,
    ) = MediaAttachment(
        id = id,
        originalFileName = "$id.${if (type == MediaType.VIDEO) "mp4" else "jpg"}",
        extension = if (type == MediaType.VIDEO) "mp4" else "jpg",
        type = type,
        sourceUrl = "https://media.example/$id",
        thumbnailUrl = "https://media.example/$id-thumb.jpg",
    )
}
