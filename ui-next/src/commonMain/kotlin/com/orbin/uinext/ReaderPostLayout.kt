package com.orbin.uinext

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.orbin.uinext.tokens.OrbinType
import com.orbin.uinext.tokens.ReaderSize
import com.orbin.uinext.tokens.ReaderSpace

/** Rich host-rendered comments inherit the same post size as plain reader comments. */
val LocalReaderPostBodyStyle = staticCompositionLocalOf { OrbinType.postBody }

/**
 * Clover 3.0.2 post geometry: a leading 72/100dp thumbnail and an inset text column.
 *
 * Insets belong to the text, rather than the image. At 2x text size Orbin keeps its accessible
 * stacked presentation; relative placement also preserves the existing RTL reader behavior.
 */
@Composable
internal fun ReaderPostLayout(
    modifier: Modifier = Modifier,
    wideWindow: Boolean = false,
    indent: Dp = 0.dp,
    thumbnail: (@Composable (Modifier) -> Unit)? = null,
    content: @Composable (Modifier) -> Unit,
) {
    val thumbnailSize = if (wideWindow) ReaderSize.thumbnailWide else ReaderSize.thumbnail
    val layoutModifier = modifier.fillMaxWidth().padding(start = indent)
    if (LocalDensity.current.fontScale >= READER_STACKED_FONT_SCALE) {
        Column(layoutModifier, verticalArrangement = Arrangement.Top) {
            thumbnail?.invoke(Modifier.size(thumbnailSize))
            content(Modifier.fillMaxWidth())
        }
    } else {
        Row(layoutModifier, verticalAlignment = Alignment.Top) {
            thumbnail?.invoke(Modifier.size(thumbnailSize))
            content(Modifier.weight(1f))
        }
    }
}

internal fun readerPostInset(wideWindow: Boolean): Dp =
    if (wideWindow) ReaderSpace.postInsetWide else ReaderSpace.postInset
