package com.orbin.ios

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTouchInput
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
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Preparing a supported page is distinct from a format the app cannot play. */
@OptIn(DelicateCoilApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w390dp-h844dp-mdpi")
class MediaViewerStartupTest {
    @get:Rule
    val compose = createComposeRule()

    @Before
    fun installPreviewTransport() {
        val engine = FakeImageLoaderEngine.Builder().default(ColorImage(0xFF444444.toInt())).build()
        SingletonImageLoader.setSafe { context -> ImageLoader.Builder(context).components { add(engine) }.build() }
    }

    @After
    fun resetPreviewTransport() = SingletonImageLoader.reset()

    @Test
    fun supportedInactiveNeighboursArePostersInsteadOfBrowserFallbacks() {
        compose.setContent {
            NextTheme {
                MediaViewer(
                    listOf(file("first"), file("next")),
                    0,
                    {},
                    playback = PlaybackSettings(preloadNextClip = true),
                )
            }
        }
        compose.onAllNodesWithTag(MediaViewerTags.PREVIEW, useUnmergedTree = true).assertCountEquals(1)
        assertNoBrowserFallback()
        compose.onAllNodesWithTag(MediaViewerTags.PLAYER, useUnmergedTree = true).assertCountEquals(1)
    }

    @Test
    fun draggingAndPromotingASupportedVideoNeverAnnouncesBrowserPlayback() {
        compose.setContent {
            NextTheme {
                MediaViewer(
                    listOf(file("first"), file("next")),
                    0,
                    {},
                    playback = PlaybackSettings(preloadNextClip = true),
                )
            }
        }
        compose.onNodeWithTag(MediaViewerTags.PAGER).performTouchInput {
            down(center)
            moveTo(center - Offset(0f, height * 0.6f), delayMillis = 300)
        }
        assertNoBrowserFallback()
        compose
            .onNodeWithTag(MediaViewerTags.ACTIVE_PAGE, useUnmergedTree = true)
            .assertContentDescriptionEquals("first.mp4")
        compose.onAllNodesWithTag(MediaViewerTags.PLAYER, useUnmergedTree = true).assertCountEquals(1)
        compose.onNodeWithTag(MediaViewerTags.PAGER).performTouchInput { cancel() }
        compose.onNodeWithTag(MediaViewerTags.PAGER).performScrollToIndex(1)
        compose
            .onNodeWithTag(MediaViewerTags.ACTIVE_PAGE, useUnmergedTree = true)
            .assertIsDisplayed()
            .assertContentDescriptionEquals("next.mp4")
        assertNoBrowserFallback()
        compose.onAllNodesWithTag(MediaViewerTags.PLAYER, useUnmergedTree = true).assertCountEquals(1)
    }

    @Test
    fun aDefinitivelyUnsupportedFormatStillOffersItsBrowserFallback() {
        val unsupported = file("unsupported").copy(extension = "flv")
        compose.setContent { NextTheme { MediaViewer(listOf(unsupported), 0, {}) } }
        compose.onNodeWithTag(MediaViewerTags.EXTERNAL).assertIsDisplayed()
        compose.onNodeWithText("This file plays in the browser.").assertIsDisplayed()
        compose.onNodeWithText("Open in browser").assertIsDisplayed()
        compose.onAllNodesWithTag(MediaViewerTags.PLAYER, useUnmergedTree = true).assertCountEquals(0)
    }

    @Test
    fun spoilerCoverBlocksThePosterAndPlayerUntilExplicitReveal() {
        compose.setContent { NextTheme { MediaViewer(listOf(file("covered").copy(isSpoiler = true)), 0, {}) } }
        compose.onAllNodesWithTag(MediaViewerTags.PREVIEW, useUnmergedTree = true).assertCountEquals(0)
        compose.onAllNodesWithTag(MediaViewerTags.PLAYER, useUnmergedTree = true).assertCountEquals(0)
        assertNoBrowserFallback()
        compose.onNodeWithText("Spoiler — tap to show").performClick()
        compose.onAllNodesWithTag(MediaViewerTags.PLAYER, useUnmergedTree = true).assertCountEquals(1)
        assertNoBrowserFallback()
    }

    private fun assertNoBrowserFallback() {
        compose.onAllNodesWithTag(MediaViewerTags.EXTERNAL, useUnmergedTree = true).assertCountEquals(0)
        compose.onAllNodesWithText("This file plays in the browser.", useUnmergedTree = true).assertCountEquals(0)
        compose.onAllNodesWithText("Open in browser", useUnmergedTree = true).assertCountEquals(0)
    }

    private fun file(id: String) =
        MediaAttachment(
            id = id,
            originalFileName = "$id.mp4",
            extension = "mp4",
            type = MediaType.VIDEO,
            sourceUrl = "https://media.example/$id.mp4",
            thumbnailUrl = "https://media.example/$id.jpg",
        )
}
