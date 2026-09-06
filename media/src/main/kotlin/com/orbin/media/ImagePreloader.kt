package com.orbin.media

import android.content.Context
import coil3.ImageLoader
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.size.Size
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Prefetches images into Coil caches.
 *
 * Callers must pass a lifecycle-scoped [CoroutineScope] (typically `viewModelScope` or a
 * `rememberCoroutineScope` tied to a composition). The previous default
 * `CoroutineScope(Dispatchers.Default)` was unstructured and could not be cancelled when the
 * screen left.
 */
class ImagePreloader(
    private val context: Context,
    private val imageLoader: ImageLoader,
) {
    /** Prefetch a single image URL under [scope]. */
    fun prefetch(
        url: String,
        scope: CoroutineScope,
        maxDimensionPx: Int? = DEFAULT_PREFETCH_MAX_PX,
    ) {
        if (url.isBlank()) return
        scope.launch {
            imageLoader.execute(buildRequest(url, maxDimensionPx))
        }
    }

    /** Prefetch multiple image URLs in batch under [scope]. */
    fun prefetchBatch(
        urls: List<String>,
        scope: CoroutineScope,
        maxDimensionPx: Int? = DEFAULT_PREFETCH_MAX_PX,
    ) {
        val distinct = urls.filter { it.isNotBlank() }.distinct()
        if (distinct.isEmpty()) return
        scope.launch {
            distinct.forEach { url ->
                imageLoader.execute(buildRequest(url, maxDimensionPx))
            }
        }
    }

    private fun buildRequest(
        url: String,
        maxDimensionPx: Int?,
    ): ImageRequest {
        val builder =
            ImageRequest
                .Builder(context)
                .data(url)
                .memoryCachePolicy(CachePolicy.ENABLED)
                .diskCachePolicy(CachePolicy.ENABLED)
        if (maxDimensionPx != null && maxDimensionPx > 0) {
            builder.size(Size(maxDimensionPx, maxDimensionPx))
        }
        return builder.build()
    }

    private companion object {
        const val DEFAULT_PREFETCH_MAX_PX = 720
    }
}
