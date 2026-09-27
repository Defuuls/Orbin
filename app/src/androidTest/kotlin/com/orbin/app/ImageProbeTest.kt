package com.orbin.app

import android.util.Log
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import coil3.EventListener
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.annotation.DelicateCoilApi
import coil3.request.ErrorResult
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.size.Precision
import coil3.size.Scale
import coil3.size.Size
import com.orbin.core.model.MediaAttachment
import com.orbin.core.model.MediaType
import com.orbin.media.image.MediaThumbnail
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Collections
import javax.inject.Inject

@OptIn(DelicateCoilApi::class)
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class ImageProbeTest {
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createComposeRule()

    @Inject lateinit var imageLoader: ImageLoader

    @Test
    fun probe() {
        hiltRule.inject()
        val log = Collections.synchronizedList(mutableListOf<String>())
        val start = System.currentTimeMillis()
        fun t() = System.currentTimeMillis() - start
        val loader =
            imageLoader.newBuilder().eventListener(
                object : EventListener() {
                    override fun onStart(request: ImageRequest) {
                        log += "${t()}ms start ${request.data}"
                    }

                    override fun onSuccess(request: ImageRequest, result: SuccessResult) {
                        log += "${t()}ms OK ${request.data} ${result.image.width}x${result.image.height} " +
                            "src=${result.dataSource} sampled=${result.isSampled}"
                    }

                    override fun onError(request: ImageRequest, result: ErrorResult) {
                        log += "${t()}ms ERROR ${request.data} ${result.throwable}"
                    }
                },
            ).build()
        SingletonImageLoader.setUnsafe(loader)

        val direct =
            runBlocking {
                loader.execute(
                    ImageRequest.Builder(InstrumentationRegistry.getInstrumentation().targetContext)
                        .data(FULL).size(Size(800, 400)).precision(Precision.INEXACT).scale(Scale.FIT).build(),
                )
            }
        log += "direct: ${(direct as? SuccessResult)?.image?.let { "${it.width}x${it.height}" } ?: (direct as ErrorResult).throwable}"

        composeRule.setContent {
            Box(Modifier.width(400.dp)) {
                MediaThumbnail(
                    attachment = ATTACHMENT,
                    modifier = Modifier.fillMaxWidth().aspectRatio(2212f / 1254f),
                    fullResolution = true,
                    contentScale = ContentScale.Fit,
                )
            }
        }
        composeRule.waitUntil(30_000) { log.any { it.contains("OK $FULL") || it.contains("ERROR $FULL") } }
        composeRule.waitForIdle()
        val message = "PROBE3 density=${composeRule.density.density}\n" + log.joinToString("\n")
        Log.w("OrbinProbe", message)
        throw AssertionError(message)
    }

    private companion object {
        const val FULL = "https://i.4cdn.org/g/1790480210552729.jpg"
        val ATTACHMENT =
            MediaAttachment(
                id = "1",
                originalFileName = "a.jpg",
                extension = "jpg",
                type = MediaType.IMAGE,
                sourceUrl = FULL,
                thumbnailUrl = "https://i.4cdn.org/g/1790480210552729s.jpg",
                width = 2212,
                height = 1254,
            )
    }
}
