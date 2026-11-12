package com.orbin.ios

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Exercises the actual iOS viewer/pager; native AVKit decoding remains covered by PlayerHostTest. */
@OptIn(DelicateCoilApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w390dp-h844dp-mdpi")
class MediaViewerIdentityTest {
    @get:Rule
    val compose = createComposeRule()

    private val playback = PlaybackSettings(autoplay = false, preloadNextClip = false)

    @Before
    fun installImageTransport() {
        val engine = FakeImageLoaderEngine.Builder().default(ColorImage(0xFF444444.toInt())).build()
        SingletonImageLoader.setSafe { context -> ImageLoader.Builder(context).components { add(engine) }.build() }
    }

    @After
    fun resetImageTransport() = SingletonImageLoader.reset()

    @Test
    fun requestedAttachmentIsShownAndSaveCloseReportThatExactFile() {
        val files = listOf(file("first"), file("requested"), file("last"))
        val reported = mutableListOf<String>()
        val saved = mutableListOf<MediaAttachment>()
        var visible by mutableStateOf(true)
        var closes = 0
        compose.setContent {
            NextTheme {
                if (visible) {
                    MediaViewer(
                        files,
                        startIndex = 1,
                        onClose = {
                            closes++
                            visible = false
                        },
                        onSave = {
                            saved += it
                            true
                        },
                        onPageChanged = { reported += files[it].sourceUrl },
                        playback = playback,
                    )
                }
            }
        }
        assertSelected(files[1], 2, 3)
        compose.onNodeWithContentDescription("Save").performClick()
        compose.onNodeWithContentDescription("Close").performClick()
        compose.runOnIdle {
            assertEquals(listOf(files[1]), saved)
            assertTrue(reported.isNotEmpty())
            assertTrue(reported.all { it == files[1].sourceUrl })
            assertEquals(1, closes)
        }
        compose.onAllNodesWithTag(MediaViewerTags.PLAYER, useUnmergedTree = true).assertCountEquals(0)
    }

    @Test
    fun insertedAndReorderedAttachmentsDoNotReplaceTheSelectedFileOrReportANeighbour() {
        val first = file("first")
        val selected = file("selected")
        val last = file("last")
        var files by mutableStateOf(listOf(first, selected, last))
        val reported = mutableListOf<String>()
        val saved = mutableListOf<MediaAttachment>()
        compose.setContent {
            NextTheme {
                MediaViewer(files, 1, {}, onSave = {
                    saved += it
                    true
                }, onPageChanged = {
                    reported +=
                        files[it].sourceUrl
                }, playback = playback)
            }
        }
        assertSelected(selected, 2, 3)
        compose.runOnIdle { files = listOf(file("inserted")) + files }
        assertSelected(selected, 3, 4)
        compose.runOnIdle { files = listOf(last, first, file("inserted"), selected) }
        assertSelected(selected, 4, 4)
        compose.onNodeWithContentDescription("Save").performClick()
        compose.runOnIdle {
            assertEquals(listOf(selected), saved)
            assertTrue(reported.isNotEmpty())
            assertTrue(
                "A data update must never activate or report an adjacent clip",
                reported.all {
                    it ==
                        selected.sourceUrl
                },
            )
        }
    }

    @Test
    fun removingAnEarlierFileKeepsTheSelectedAttachmentAtItsNewIndex() {
        val first = file("first")
        val selected = file("selected")
        var files by mutableStateOf(listOf(first, selected, file("last")))
        val reported = mutableListOf<String>()
        compose.setContent {
            NextTheme {
                MediaViewer(
                    files,
                    1,
                    {},
                    onPageChanged = { reported += files[it].sourceUrl },
                    playback = playback,
                )
            }
        }
        assertSelected(selected, 2, 3)
        compose.runOnIdle { files = files.drop(1) }
        assertSelected(selected, 1, 2)
        compose.runOnIdle { assertTrue(reported.isNotEmpty() && reported.all { it == selected.sourceUrl }) }
    }

    @Test
    fun removingTheSelectedFileUsesItsPreviousIndexAndClampsAtTheEnd() {
        val first = file("first")
        val second = file("second")
        val selected = file("selected")
        val last = file("last")
        var files by mutableStateOf(listOf(first, second, selected, last))
        compose.setContent { NextTheme { MediaViewer(files, 2, {}, playback = playback) } }
        assertSelected(selected, 3, 4)
        compose.runOnIdle { files = listOf(first, second, last) }
        assertSelected(last, 3, 3)
        compose.runOnIdle { files = listOf(first, second) }
        assertSelected(second, 2, 2)
    }

    @Test
    fun aManuallySelectedPageBecomesTheIdentityRetainedByLaterUpdates() {
        val selected = file("selected")
        var files by mutableStateOf(listOf(file("first"), file("second"), selected))
        compose.setContent { NextTheme { MediaViewer(files, 0, {}, playback = playback.copy(preloadNextClip = true)) } }
        compose.onNodeWithTag(MediaViewerTags.PAGER).performScrollToIndex(2)
        assertSelected(selected, 3, 3)
        compose.runOnIdle { files = listOf(file("inserted")) + files }
        assertSelected(selected, 4, 4)
    }

    @Test
    fun reusedIdsRemainDistinctAcrossSourceUrls() {
        val first = file("reused", source = "fourchan", name = "first.mp4")
        val selected = file("reused", source = "bbwchan", name = "selected.mp4")
        var files by mutableStateOf(listOf(first, selected))
        val saved = mutableListOf<MediaAttachment>()
        compose.setContent {
            NextTheme {
                MediaViewer(files, 1, {}, onSave = {
                    saved += it
                    true
                }, playback = playback)
            }
        }
        assertSelected(selected, 2, 2)
        compose.runOnIdle { files = listOf(selected, first) }
        assertSelected(selected, 1, 2)
        compose.onNodeWithContentDescription("Save").performClick()
        compose.runOnIdle { assertEquals(listOf(selected), saved) }
    }

    @Test
    fun identicalFileOccurrencesHaveLegalKeysAndTheRemainingFileStaysSelected() {
        val original = file("duplicate")
        val selected = original.copy(originalFileName = "selected.mp4")
        var files by mutableStateOf(listOf(original, selected, file("last")))
        compose.setContent { NextTheme { MediaViewer(files, 1, {}, playback = playback) } }
        assertSelected(selected, 2, 3)
        compose.runOnIdle { files = files.drop(1) }
        assertSelected(selected, 1, 2)
    }

    @Test
    fun metadataRefreshRetainsTheCurrentClipLoopChoiceAndDownloadState() {
        var selected = file("selected")
        var files by mutableStateOf(listOf(file("first"), selected))
        val saved = mutableListOf<MediaAttachment>()
        compose.setContent {
            NextTheme {
                MediaViewer(
                    files,
                    1,
                    {},
                    onSave = {
                        saved += it
                        true
                    },
                    playback = playback.copy(loopVideos = true),
                )
            }
        }
        assertSelected(selected, 2, 2)
        compose.onNodeWithContentDescription("More options").performClick()
        compose.onNodeWithText("Loop: On").performClick()
        compose.onNodeWithContentDescription("Save").performClick()
        compose.runOnIdle {
            selected = selected.copy(originalFileName = "updated.mp4", width = 1920, height = 1080)
            files = listOf(files[0], selected)
        }
        assertSelected(selected, 2, 2)
        compose.onNodeWithContentDescription("More options").performClick()
        compose.onNodeWithText("Loop: Off").assertIsDisplayed()
        compose.onNodeWithText("Loop: Off").performClick()
        compose.onNodeWithContentDescription("Added to Downloads").assertIsDisplayed()
        compose.runOnIdle { assertEquals(1, saved.size) }
    }

    @Test
    fun removingAllFilesClosesTheViewerAndReleasesItsPlayerNode() {
        var files by mutableStateOf(listOf(file("only")))
        var closes = 0
        compose.setContent { NextTheme { MediaViewer(files, 0, { closes++ }, playback = playback) } }
        assertSelected(files.single(), 1, 1)
        compose.runOnIdle { files = emptyList() }
        compose.runOnIdle { assertEquals(1, closes) }
        compose.onAllNodesWithTag(MediaViewerTags.PLAYER, useUnmergedTree = true).assertCountEquals(0)
    }

    private fun assertSelected(
        file: MediaAttachment,
        position: Int,
        count: Int,
    ) {
        compose
            .onNodeWithTag(MediaViewerTags.ACTIVE_PAGE, useUnmergedTree = true)
            .assertIsDisplayed()
            .assertContentDescriptionEquals(file.originalFileName)
        compose.onAllNodesWithTag(MediaViewerTags.PLAYER, useUnmergedTree = true).assertCountEquals(1)
        compose.onNodeWithText("$position/$count").assertIsDisplayed()
    }

    private fun file(
        id: String,
        source: String = "media",
        name: String = "$id.mp4",
    ) = MediaAttachment(
        id = id,
        originalFileName = name,
        extension = "mp4",
        type = MediaType.VIDEO,
        sourceUrl = "https://$source.example/$id.mp4",
        thumbnailUrl = "https://$source.example/$id.jpg",
    )
}
