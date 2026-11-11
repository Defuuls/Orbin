package com.orbin.ios

import android.content.Context
import android.os.Looper
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
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
import coil3.size.SizeResolver
import coil3.test.FakeImageLoaderEngine
import com.orbin.core.model.MediaAttachment
import com.orbin.core.model.MediaType
import com.orbin.uinext.NextTheme
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** The iOS catalog pipeline is shared Kotlin, so its requests can be checked without an Apple decoder. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@OptIn(DelicateCoilApi::class)
class ReaderThumbnailTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val resolvedRequests = java.util.concurrent.CopyOnWriteArrayList<Pair<Any, Size>>()

    @Before
    fun installFakeLoader() {
        SingletonImageLoader.reset()
        val engine = FakeImageLoaderEngine.Builder().default(ColorImage(Color.Red.toArgb(), 500, 500)).build()
        SingletonImageLoader.setUnsafe { context ->
            ImageLoader
                .Builder(context)
                .components { add(engine) }
                .eventListener(
                    object : EventListener() {
                        override fun resolveSizeEnd(
                            request: ImageRequest,
                            size: Size,
                        ) {
                            resolvedRequests += request.data to size
                        }
                    },
                ).build()
        }
    }

    @After
    fun resetLoaderAndPencil() {
        Pencil.hoverAt(null)
        SingletonImageLoader.reset()
    }

    @Test
    fun `all media use only the provider thumbnail at the resolved pixel size`() =
        runTest {
            val context: Context = RuntimeEnvironment.getApplication()
            for (type in listOf(MediaType.IMAGE, MediaType.ANIMATED_IMAGE, MediaType.VIDEO)) {
                val request =
                    readerThumbnailRequest(context, ATTACHMENT.copy(type = type), SizeResolver(Size(144, 144)))
                assertEquals(ATTACHMENT.thumbnailUrl, request.data)
                assertEquals(Size(144, 144), request.sizeResolver.size())
                assertEquals(Precision.EXACT, request.precision)
            }
        }

    @Test
    fun `a missing thumbnail never falls back to the full file`() {
        val context: Context = RuntimeEnvironment.getApplication()
        val request = readerThumbnailRequest(context, ATTACHMENT.copy(thumbnailUrl = ""), SizeResolver(Size(200, 200)))
        // Coil's null-data sentinel is an implementation detail; an original URL is never selected.
        assertNull(request.data as? String)
    }

    @Test
    fun `a spoiler cover owns the first tap and disappears after reveal`() {
        composeRule.setContent {
            NextTheme { ReaderThumbnail(ATTACHMENT.copy(isSpoiler = true), Modifier.size(72.dp)) }
        }
        composeRule.onNodeWithText("Spoiler").assertIsDisplayed().performClick()
        composeRule.onNodeWithText("Spoiler").assertDoesNotExist()
    }

    @Test
    fun `pencil hover preserves the larger preview using only thumbnail requests at drawn density`() {
        var density = 0f
        var thumbnailBounds = Rect.Zero
        composeRule.setContent {
            density = LocalDensity.current.density
            NextTheme {
                ReaderThumbnail(
                    ATTACHMENT,
                    Modifier
                        .size(72.dp)
                        .testTag("reader-thumbnail")
                        .onGloballyPositioned { thumbnailBounds = it.boundsInRoot() },
                )
            }
        }
        composeRule.waitUntil { resolvedRequests.isNotEmpty() }
        composeRule.runOnIdle {
            assertTrue("The hover target must have real layout bounds", thumbnailBounds.width > 0)
            Pencil.hoverAt(
                PencilHover(thumbnailBounds.center.x / density, thumbnailBounds.center.y / density, null),
            )
        }
        composeRule.waitForIdle()
        // Pencil dwell uses the platform coroutine delay, whose Robolectric Looper clock is
        // independent from Compose's frame clock. Advance both rather than extending the timeout.
        shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(350))
        composeRule.mainClock.advanceTimeBy(500)
        composeRule.onNodeWithContentDescription(ATTACHMENT.originalFileName).assertIsDisplayed()
        composeRule.waitUntil(timeoutMillis = 5_000) { resolvedRequests.size >= 2 }
        assertTrue(resolvedRequests.all { it.first == ATTACHMENT.thumbnailUrl })
        assertTrue(resolvedRequests.any { it.second == Size((72 * density).toInt(), (72 * density).toInt()) })
        assertTrue(resolvedRequests.any { it.second == Size((280 * density).toInt(), (280 * density).toInt()) })
    }

    private companion object {
        val ATTACHMENT =
            MediaAttachment(
                id = "first",
                originalFileName = "photo.jpg",
                extension = "jpg",
                type = MediaType.IMAGE,
                sourceUrl = "https://example.invalid/original.jpg",
                thumbnailUrl = "https://example.invalid/thumb.jpg",
            )
    }
}
