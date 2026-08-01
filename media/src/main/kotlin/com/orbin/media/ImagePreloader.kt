package com.orbin.media

import android.content.Context
import coil3.ImageLoader
import coil3.request.ImageRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Prefetches images to improve perceived scroll performance. */
class ImagePreloader(
    private val context: Context,
    private val imageLoader: ImageLoader,
) {
    /** Prefetch a single image URL. */
    fun prefetch(
        url: String,
        scope: CoroutineScope = CoroutineScope(Dispatchers.Default),
    ) {
        scope.launch {
            imageLoader.execute(ImageRequest.Builder(context).data(url).build())
        }
    }

    /** Prefetch multiple image URLs in batch. */
    fun prefetchBatch(
        urls: List<String>,
        scope: CoroutineScope = CoroutineScope(Dispatchers.Default),
    ) {
        scope.launch {
            urls.forEach { url ->
                imageLoader.execute(ImageRequest.Builder(context).data(url).build())
            }
        }
    }
}
