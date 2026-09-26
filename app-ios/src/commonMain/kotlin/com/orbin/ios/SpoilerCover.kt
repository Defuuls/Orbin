package com.orbin.ios

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.orbin.ios.resources.Res
import com.orbin.ios.resources.ios_media_spoiler
import org.jetbrains.compose.resources.stringResource

/**
 * What a spoilered file shows until it is opened: a blackout over its thumbnail, as Android's
 * `SpoilerOverlay` draws. Posts the violent-media cover marks (`ViolentMediaCoverProvider`) arrive
 * spoilered too, so this is also that cover.
 */
@Composable
internal fun SpoilerCover(
    modifier: Modifier = Modifier,
    text: String = stringResource(Res.string.ios_media_spoiler),
) {
    Box(
        modifier = modifier.fillMaxSize().background(Color.Black.copy(alpha = SPOILER_SCRIM_ALPHA)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = Color.White,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(8.dp),
        )
    }
}

// Opaque enough that shape and colour do not read through, the same as Android's.
private const val SPOILER_SCRIM_ALPHA = 0.85f
