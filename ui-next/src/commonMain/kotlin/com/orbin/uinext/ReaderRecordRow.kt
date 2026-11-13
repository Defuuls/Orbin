package com.orbin.uinext

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.orbin.uinext.tokens.OrbinType
import com.orbin.uinext.tokens.ReaderSize

/** Source-qualified local records use Coral cards without inventing attachment previews. */
@Composable
internal fun ReaderRecordRow(
    title: String,
    source: String,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
    wideWindow: Boolean = false,
    metadata: String = "",
    detail: String = "",
    unread: String = "",
) {
    val bodyStyle = if (wideWindow) OrbinType.postBodyWide.withReaderFont() else OrbinType.readerBody.withReaderFont()
    val detailStyle = OrbinType.meta.withReaderFont()
    OrbinCard(
        modifier = modifier.fillMaxWidth().heightIn(min = ReaderSize.touchTarget).semantics(mergeDescendants = true) {},
        onClick = onOpen,
    ) {
        Column(Modifier.padding(RECORD_INSET), verticalArrangement = Arrangement.spacedBy(RECORD_GAP)) {
            Text(
                title,
                style = OrbinType.rowTitle.withReaderFont(),
                color = reader.ink,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            Text(source, style = detailStyle, color = reader.accent)
            if (metadata.isNotBlank()) Text(metadata, style = detailStyle, color = reader.inkMuted)
            if (detail.isNotBlank()) {
                Text(detail, style = bodyStyle, color = reader.ink, maxLines = 4, overflow = TextOverflow.Ellipsis)
            }
            if (unread.isNotBlank()) {
                Text(
                    unread,
                    style = detailStyle.copy(fontWeight = FontWeight.SemiBold),
                    color = reader.accent,
                )
            }
        }
    }
}

private val RECORD_INSET = 12.dp
private val RECORD_GAP = 6.dp

/** Tablet records retain the larger reading size when the shortest window side is at least 600dp. */
@Composable
internal fun readerRecordUsesWideText(): Boolean {
    val window = LocalWindowInfo.current.containerSize
    return with(LocalDensity.current) { minOf(window.width, window.height).toDp() >= ReaderSize.wideMin }
}
