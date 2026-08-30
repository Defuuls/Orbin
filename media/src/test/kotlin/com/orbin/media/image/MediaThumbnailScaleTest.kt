package com.orbin.media.image

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import coil3.ColorImage
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.annotation.DelicateCoilApi
import coil3.test.FakeImageLoaderEngine
import com.google.common.truth.Truth.assertThat
import com.orbin.core.model.MediaAttachment
import com.orbin.core.model.MediaType
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Holds [MediaThumbnail] to its scaling contract, which is what decides whether a list row shows
 * the whole attachment or a crop out of the middle of it.
 *
 * The check is a pixel one because that is the only place the difference exists: both scales lay
 * the image out in the same box and expose the same semantics, and only the raster says which of
 * them was used. A 2:1 image in a square tile is the discriminator — cropped it covers the tile
 * edge to edge, fitted it leaves the backing colour showing above and below.
 *
 * Replacing the singleton loader is what `DelicateCoilApi` warns about: it is process-wide and
 * racy if anything else is loading. Nothing else is here, and `@After` puts it back.
 */
@OptIn(DelicateCoilApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xhdpi")
class MediaThumbnailScaleTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Before
    fun installFakeImageLoader() {
        // Twice as wide as it is tall, so a square tile cannot show all of it without letterboxing.
        val engine =
            FakeImageLoaderEngine
                .Builder()
                .default(ColorImage(IMAGE_COLOUR.toArgb(), width = 200, height = 100))
                .build()
        SingletonImageLoader.setSafe { context ->
            ImageLoader.Builder(context).components { add(engine) }.build()
        }
    }

    @After
    fun resetImageLoader() = SingletonImageLoader.reset()

    @Test
    fun `fitting a wide attachment into a square tile keeps the whole image`() {
        assertThat(tileEdgeColour(ContentScale.Fit)).isEqualTo(BACKING_COLOUR)
    }

    @Test
    fun `cropping a wide attachment into a square tile fills the tile`() {
        assertThat(tileEdgeColour(ContentScale.Crop)).isEqualTo(IMAGE_COLOUR)
    }

    @Test
    fun `a tile crops unless it is told otherwise`() {
        // The grid, the image wall and the thread reader all rely on the default, so a change to it
        // is a change to them.
        assertThat(tileEdgeColour(contentScale = null)).isEqualTo(IMAGE_COLOUR)
    }

    /**
     * Renders a wide attachment in a square tile over a known backing colour and reads the pixel at
     * the middle of the tile's top edge — inside the tile's rounded corners, and the first place
     * the image stops covering once it is fitted rather than cropped.
     */
    private fun tileEdgeColour(contentScale: ContentScale?): Color {
        composeRule.setContent {
            Box(modifier = Modifier.size(TILE_SIZE).background(BACKING_COLOUR)) {
                if (contentScale == null) {
                    MediaThumbnail(attachment = WIDE_IMAGE, modifier = Modifier.size(TILE_SIZE))
                } else {
                    MediaThumbnail(
                        attachment = WIDE_IMAGE,
                        modifier = Modifier.size(TILE_SIZE),
                        contentScale = contentScale,
                    )
                }
            }
        }
        composeRule.waitForIdle()

        val pixels = composeRule.onRoot().captureToImage().toPixelMap()
        return pixels[pixels.width / 2, TOP_EDGE_INSET]
    }

    private companion object {
        val TILE_SIZE = 68.dp

        /** Far enough in to clear the tile's 8dp corner radius, still in the top letterbox band. */
        const val TOP_EDGE_INSET = 2

        val BACKING_COLOUR = Color.Blue
        val IMAGE_COLOUR = Color.Red

        val WIDE_IMAGE =
            MediaAttachment(
                id = "1",
                originalFileName = "wide.png",
                extension = "png",
                type = MediaType.IMAGE,
                sourceUrl = "https://example.invalid/wide.png",
                thumbnailUrl = "https://example.invalid/wide-thumb.png",
                width = 200,
                height = 100,
            )
    }
}
