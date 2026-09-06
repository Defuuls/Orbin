package com.orbin.media.image

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.size.Size

private const val MIN_SCALE = 1f
private const val MAX_SCALE = 5f

/** Cap decode edge so huge sourceUrl bitmaps cannot allocate full-resolution in the gallery pager. */
private const val GALLERY_MAX_DECODE_DP = 1600

/**
 * A pinch-to-zoom, pan-able image for the gallery. Scale is clamped to [MIN_SCALE]..[MAX_SCALE];
 * panning is only meaningful while zoomed in. Pure Compose gestures — no extra dependencies.
 *
 * Decodes are capped to roughly the display's longer edge (and never above [GALLERY_MAX_DECODE_DP])
 * so off-screen pager neighbours do not pin multi-megapixel bitmaps in memory. Pass
 * [placeholderUrl] (usually the thumbnail) for a progressive first paint. When [active] is false
 * only the cheap placeholder is kept composition-ready.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ZoomableImage(
    url: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    placeholderUrl: String? = null,
    active: Boolean = true,
) {
    var scale by remember { mutableFloatStateOf(MIN_SCALE) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    val transformableState =
        rememberTransformableState { _, zoomChange, panChange, _ ->
            scale = (scale * zoomChange).coerceIn(MIN_SCALE, MAX_SCALE)
            offset = if (scale > MIN_SCALE) offset + panChange else Offset.Zero
        }

    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val maxEdgePx =
        remember(configuration, density) {
            val longerDp = maxOf(configuration.screenWidthDp, configuration.screenHeightDp)
            val longerPx = with(density) { longerDp.dp.roundToPx() }
            val capPx = with(density) { GALLERY_MAX_DECODE_DP.dp.roundToPx() }
            longerPx.coerceAtMost(capPx).coerceAtLeast(1)
        }
    val context = LocalPlatformContext.current
    val fullModel =
        remember(url, maxEdgePx, active) {
            if (!active || url.isNullOrBlank()) {
                null
            } else {
                ImageRequest
                    .Builder(context)
                    .data(url)
                    .size(Size(maxEdgePx, maxEdgePx))
                    .build()
            }
        }

    OrbinAsyncImage(
        url = if (active) url else placeholderUrl,
        model = fullModel,
        contentDescription = contentDescription,
        contentScale = ContentScale.Fit,
        placeholderUrl = placeholderUrl,
        modifier =
            modifier
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                }.transformable(
                    state = transformableState,
                    canPan = { scale > MIN_SCALE },
                ),
    )
}
