package com.orbin.media.image

import org.robolectric.RuntimeEnvironment
import android.content.Context
import coil3.BitmapImage
import coil3.request.ErrorResult
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.size.Precision
import coil3.size.Scale
import coil3.size.Size
import com.orbin.media.di.ImageLoaderModule
import com.orbin.network.NetworkConfig
import com.orbin.network.interceptor.HeadersInterceptor
import com.orbin.network.interceptor.PowBlockInterceptor
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class RealLoaderProbeTest {
    @Test
    fun probe() {
        val context = RuntimeEnvironment.getApplication() as Context
        val client =
            OkHttpClient.Builder()
                .addInterceptor(PowBlockInterceptor())
                .addInterceptor(HeadersInterceptor { NetworkConfig() })
                .build()
        val loader = ImageLoaderModule.providesImageLoader(context, client)
        val urls =
            listOf(
                "https://i.4cdn.org/g/1790480210552729.jpg",
                "https://i.4cdn.org/g/1790480210552729s.jpg",
                "https://bbw-chan.link/.media/7d2d1c3e1e64a44dbf25c4c160037323cd9b0e7e966fd02d0ed9280d80604f22.png",
                "https://bbw-chan.link/.media/t_7d2d1c3e1e64a44dbf25c4c160037323cd9b0e7e966fd02d0ed9280d80604f22",
            )
        val out =
            urls.map { url ->
                val started = System.currentTimeMillis()
                val result =
                    runBlocking {
                        withTimeoutOrNull(60_000) {
                            loader.execute(
                                ImageRequest.Builder(context).data(url).size(Size(800, 400))
                                    .precision(Precision.INEXACT).scale(Scale.FIT).build(),
                            )
                        }
                    }
                val took = System.currentTimeMillis() - started
                when (result) {
                    null -> "$url TIMEOUT after ${took}ms"
                    is SuccessResult -> {
                        val image = result.image
                        "$url OK ${image.width}x${image.height} ${image::class.simpleName} " +
                            "${(image as? BitmapImage)?.bitmap?.config} source=${result.dataSource} ${took}ms"
                    }
                    is ErrorResult -> "$url ERROR ${result.throwable} ${took}ms"
                }
            }
        throw AssertionError("PROBE2\n" + out.joinToString("\n"))
    }
}
