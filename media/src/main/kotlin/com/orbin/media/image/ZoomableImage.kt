package com.orbin.media.image

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

private const val MIN_SCALE = 1f
private const val MAX_SCALE = 5f

/**
 * A pinch-to-zoom, pan-able image for the gallery. Scale is clamped to [MIN_SCALE]..[MAX_SCALE];
 * panning is only meaningful while zoomed in. Pure Compose gestures — no extra dependencies.
 */
@Composable
fun ZoomableImage(
    url: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
) {
    var scale by remember { mutableFloatStateOf(MIN_SCALE) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    val transformableState =
        rememberTransformableState { zoomChange, panChange, _ ->
            scale = (scale * zoomChange).coerceIn(MIN_SCALE, MAX_SCALE)
            offset = if (scale > MIN_SCALE) offset + panChange else Offset.Zero
        }

    OrbinAsyncImage(
        url = url,
        contentDescription = contentDescription,
        contentScale = ContentScale.Fit,
        modifier =
            modifier
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                }.transformable(transformableState),
    )
}
