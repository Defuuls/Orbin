package com.orbin.ios

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import coil3.ColorImage
import coil3.EventListener
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.annotation.DelicateCoilApi
import coil3.request.ImageRequest
import coil3.size.Precision
import coil3.size.Size
import coil3.test.FakeImageLoaderEngine
import com.orbin.core.model.BoardId
import com.orbin.core.model.MediaAttachment
import com.orbin.core.model.MediaType
import com.orbin.core.model.Post
import com.orbin.core.model.PostId
import com.orbin.core.model.ProviderId
import com.orbin.core.model.Thread
import com.orbin.core.model.ThreadId
import com.orbin.core.model.ThreadKey
import com.orbin.core.reader.preparePresentation
import com.orbin.uinext.NextTheme
import com.orbin.uinext.ReaderAlbumTags
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.roundToInt

/** Exercises production iOS post and Files slots through their actual singleton Coil requests. */
@OptIn(DelicateCoilApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xhdpi")
class ThreadThumbnailBindingTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val requests = CopyOnWriteArrayList<ResolvedRequest>()

    @Before
    fun installImageTransport() {
        val engine = FakeImageLoaderEngine.Builder().default(ColorImage(0xFF444444.toInt())).build()
        SingletonImageLoader.setSafe { context ->
            ImageLoader
                .Builder(context)
                .components { add(engine) }
                .eventListener(
                    object : EventListener() {
                        override fun resolveSizeEnd(
                            request: ImageRequest,
                            size: Size,
                        ) {
                            (request.data as? String)?.let {
                                requests.add(
                                    ResolvedRequest(it, size, request.precision),
                                )
                            }
                        }
                    },
                ).build()
        }
    }

    @After
    fun resetImageTransport() = SingletonImageLoader.reset()

    @Test
    fun postAndFilesBindingsRequestOnlyProviderPreviewsAtTheirDrawnPixelSizes() {
        val thread = thread()
        val presentation = runBlocking { thread.preparePresentation() }
        val previews =
            presentation.attachments
                .map { it.thumbnailUrl }
                .filter(String::isNotBlank)
                .toSet()
        composeRule.setContent {
            NextTheme {
                Box(Modifier.size(width = 360.dp, height = 800.dp)) {
                    ThreadContent(
                        presentation = presentation,
                        refreshing = false,
                        refreshFailure = null,
                        watching = false,
                        firstUnreadPostId = null,
                        onWatch = {},
                        onOpenFile = {},
                    )
                }
            }
        }
        composeRule.waitUntil { requests.map { it.url }.toSet() == previews }
        assertEquals(setOf(Size(144, 144)), requests.map { it.size }.toSet())
        assertEquals(setOf(Precision.EXACT), requests.map { it.precision }.toSet())

        requests.clear()
        composeRule.onNodeWithContentDescription("Files").performClick()
        val expectedSizes =
            presentation.fileCells
                .mapNotNull { cell ->
                    val preview =
                        presentation.attachmentsById
                            .getValue(cell.id)
                            .thumbnailUrl
                            .takeIf(String::isNotBlank)
                            ?: return@mapNotNull null
                    val bounds =
                        composeRule
                            .onNodeWithTag(ReaderAlbumTags.thumbnail(cell.id), useUnmergedTree = true)
                            .getUnclippedBoundsInRoot()
                    val widthPx = ((bounds.right - bounds.left).value * 2f).roundToInt()
                    val heightPx = ((bounds.bottom - bounds.top).value * 2f).roundToInt()
                    preview to Size(widthPx, heightPx)
                }.toMap()
        composeRule.waitUntil { requests.map { it.url }.toSet() == previews }
        assertEquals(expectedSizes, requests.associate { it.url to it.size })
        assertEquals(setOf(Precision.EXACT), requests.map { it.precision }.toSet())
        assertFalse(requests.any { request -> presentation.attachments.any { it.sourceUrl == request.url } })
    }

    private fun thread(): Thread {
        val key = ThreadKey(ProviderId("site"), BoardId("g"), ThreadId(1))
        val attachments =
            listOf(MediaType.IMAGE, MediaType.ANIMATED_IMAGE, MediaType.VIDEO, MediaType.IMAGE)
                .mapIndexed { index, type ->
                    MediaAttachment(
                        id = index.toString(),
                        originalFileName = "$index.jpg",
                        extension = "jpg",
                        type = type,
                        sourceUrl = "https://example.invalid/original-$index",
                        thumbnailUrl = if (index == 3) "" else "https://example.invalid/preview-$index",
                    )
                }
        val posts =
            attachments.mapIndexed { index, attachment ->
                Post(
                    PostId(index + 1L),
                    key.board,
                    key.thread,
                    isOriginalPost = index == 0,
                    attachments = listOf(attachment).toImmutableList(),
                )
            }
        return Thread(key, posts.first(), posts.drop(1).toImmutableList())
    }

    private data class ResolvedRequest(
        val url: String,
        val size: Size,
        val precision: Precision,
    )
}
