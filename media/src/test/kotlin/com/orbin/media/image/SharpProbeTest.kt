package com.orbin.media.image

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import coil3.ColorImage
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.annotation.DelicateCoilApi
import coil3.intercept.Interceptor
import coil3.request.ImageResult
import coil3.request.SuccessResult
import coil3.decode.DataSource
import com.orbin.core.model.MediaAttachment
import com.orbin.core.model.MediaType
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@OptIn(DelicateCoilApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xhdpi")
class SharpProbeTest {
    @get:Rule
    val composeRule = createComposeRule()

    @After
    fun reset() = SingletonImageLoader.reset()

    @Test
    fun probe() {
        val log = mutableListOf<String>()
        SingletonImageLoader.setSafe { context ->
            ImageLoader.Builder(context).components {
                add(Interceptor { chain ->
                    log += "req ${chain.request.data} size=${chain.size} precision=${chain.request.precision}"
                    val big = chain.request.data.toString().endsWith("full.jpg")
                    SuccessResult(
                        image = ColorImage(0, if (big) 2000 else 250, if (big) 1000 else 125),
                        request = chain.request,
                        dataSource = DataSource.NETWORK,
                    ) as ImageResult
                })
            }.build()
        }
        val attachment = MediaAttachment(
            id = "1", originalFileName = "a.jpg", extension = "jpg", type = MediaType.IMAGE,
            sourceUrl = "https://x.invalid/full.jpg", thumbnailUrl = "https://x.invalid/thumb.jpg",
            width = 2000, height = 1000,
        )
        composeRule.setContent {
            Box(Modifier.width(400.dp)) {
                MediaThumbnail(
                    attachment = attachment,
                    modifier = Modifier.fillMaxWidth().aspectRatio(2f),
                    fullResolution = true,
                    contentScale = ContentScale.Fit,
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.mainClock.advanceTimeBy(2000)
        composeRule.waitForIdle()
        throw AssertionError("PROBE\n" + log.joinToString("\n"))
    }
}
