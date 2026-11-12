package com.orbin.uinext

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import com.orbin.uinext.resources.Res
import com.orbin.uinext.resources.next_spoiler_reveal
import com.orbin.uinext.resources.reader_spoiler
import com.orbin.uinext.tokens.ReaderSize
import com.orbin.uinext.tokens.ReaderSpace
import com.orbin.uinext.tokens.ReaderType
import org.jetbrains.compose.resources.stringResource

/**
 * The cell geometry shared by catalog rows, posts and their loading skeletons.
 *
 * The optional image always reserves its drawn square before loading. Relative start/end
 * padding and Row placement mirror in RTL. At large text sizes the trailing label moves below
 * the text, then the image moves above it; neither text nor image is scaled down to fit.
 * [contentMaxWidth] limits the reading column without moving its leading edge.
 */
@Composable
fun ReaderCellLayout(
    modifier: Modifier = Modifier,
    wideWindow: Boolean = false,
    thumbnail: (@Composable (Modifier) -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    verticalPadding: Dp = ReaderSpace.rowPadY,
    indent: Dp = 0.dp,
    contentMaxWidth: Dp = Dp.Unspecified,
    content: @Composable (Modifier) -> Unit,
) {
    val fontScale = LocalDensity.current.fontScale
    val stacked = fontScale >= READER_STACKED_FONT_SCALE
    val trailingBelow = fontScale >= READER_TRAILING_BELOW_FONT_SCALE
    val thumbnailSize = if (wideWindow) ReaderSize.thumbnailWide else ReaderSize.thumbnail
    val cellMin = if (wideWindow) ReaderSize.cellMinWide else ReaderSize.cellMin
    val padding =
        modifier.fillMaxWidth().padding(
            start = ReaderSpace.gutter + indent,
            end = ReaderSpace.gutter,
            top = verticalPadding,
            bottom = verticalPadding,
        )
    val textColumn: @Composable (Modifier) -> Unit = { textModifier ->
        Column(
            textModifier.widthIn(max = contentMaxWidth),
            verticalArrangement = Arrangement.spacedBy(ReaderSpace.line),
        ) {
            content(Modifier.fillMaxWidth())
            if (trailingBelow && trailing != null) trailing()
        }
    }
    if (stacked) {
        Column(padding, verticalArrangement = Arrangement.spacedBy(ReaderSpace.gapSmall)) {
            if (thumbnail != null) thumbnail(Modifier.size(thumbnailSize))
            textColumn(Modifier.widthIn(max = contentMaxWidth).fillMaxWidth())
        }
    } else {
        Row(
            padding.heightIn(min = if (thumbnail != null) cellMin - verticalPadding * 2 else 0.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (thumbnail != null) {
                Box(Modifier.align(Alignment.Top)) { thumbnail(Modifier.size(thumbnailSize)) }
                Spacer(Modifier.width(ReaderSpace.gap))
            }
            textColumn(Modifier.weight(1f, fill = !contentMaxWidth.isSpecified).align(Alignment.Top))
            if (!trailingBelow && trailing != null) {
                Spacer(Modifier.width(ReaderSpace.gapSmall))
                trailing()
            }
        }
    }
}

internal const val READER_TRAILING_BELOW_FONT_SCALE = 1.3f
internal const val READER_STACKED_FONT_SCALE = 2f

/** An opaque thumbnail cover whose first tap only reveals the provider's preview. */
@Composable
fun ReaderSpoilerCover(
    onReveal: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    onLongClickLabel: String? = null,
) {
    val colors = reader
    Box(
        modifier
            .background(colors.surfaceVariant)
            .nextClickable(
                onClickLabel = stringResource(Res.string.next_spoiler_reveal),
                onLongClick = onLongClick,
                onLongClickLabel = onLongClickLabel,
                onClick = onReveal,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(Res.string.reader_spoiler),
            style = ReaderType.meta.withReaderFont(),
            color = colors.ink,
            textAlign = TextAlign.Center,
        )
    }
}
