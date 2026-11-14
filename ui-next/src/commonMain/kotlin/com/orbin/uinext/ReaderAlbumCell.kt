package com.orbin.uinext

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.orbin.uinext.resources.Res
import com.orbin.uinext.resources.next_media_actions
import com.orbin.uinext.resources.next_open_file
import com.orbin.uinext.resources.reader_reveal_spoiler
import com.orbin.uinext.tokens.OrbinType
import com.orbin.uinext.tokens.ReaderSize
import com.orbin.uinext.tokens.ReaderSpace
import org.jetbrains.compose.resources.stringResource

object ReaderAlbumTags {
    fun cell(id: String): String = "reader-album-cell:$id"

    fun thumbnail(id: String): String = "reader-album-thumbnail:$id"

    fun details(id: String): String = "reader-album-details:$id"
}

/** The shared square-corner album cell; covered files never compose their thumbnail before reveal. */
@Composable
internal fun ReaderAlbumCell(
    cell: MediaCell,
    modifier: Modifier = Modifier,
    onOpen: ((MediaCell) -> Unit)? = null,
    onLongPress: ((MediaCell) -> Unit)? = null,
    tile: (@Composable (MediaCell, Modifier) -> Unit)? = null,
    details: String = cell.details,
    cellTag: String = ReaderAlbumTags.cell(cell.id),
    detailsTag: String = ReaderAlbumTags.details(cell.id),
    coral: Boolean = false,
) {
    var revealed by rememberSaveable(cell.id, cell.spoiler) { mutableStateOf(false) }
    val covered = cell.spoiler && !revealed
    val revealLabel = stringResource(Res.string.reader_reveal_spoiler)
    val openLabel = stringResource(Res.string.next_open_file)
    val actionsLabel = stringResource(Res.string.next_media_actions)
    val clickable =
        if (covered || onOpen != null || onLongPress != null) {
            Modifier.nextClickable(
                role = Role.Button,
                onClickLabel = if (covered) revealLabel else openLabel,
                onLongClick = onLongPress?.let { handler -> { handler(cell) } },
                onLongClickLabel = actionsLabel,
                onClick = {
                    if (covered) revealed = true else onOpen?.invoke(cell)
                },
            )
        } else {
            Modifier
        }
    Column(
        modifier
            .fillMaxWidth()
            .testTag(cellTag)
            .background(if (coral && !next.dark) reader.background else reader.surfaceVariant)
            .then(clickable),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .testTag(ReaderAlbumTags.thumbnail(cell.id)),
        ) {
            if (covered) {
                ReaderSpoilerCover(onReveal = { revealed = true }, modifier = Modifier.fillMaxSize())
            } else {
                tile?.invoke(cell, Modifier.fillMaxSize())
            }
        }
        if (details.isNotBlank()) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .then(
                        if (coral) Modifier.heightIn(min = 40.dp) else Modifier.height(ReaderSize.albumDetails),
                    ).background(if (coral) reader.surfaceVariant else Color(0x8A000000))
                    .padding(horizontal = ReaderSpace.postInset, vertical = if (coral) 8.dp else 0.dp)
                    .testTag(detailsTag),
                contentAlignment = Alignment.CenterStart,
            ) {
                val style = if (coral) OrbinType.meta.withReaderFont() else OrbinType.postDetails.withReaderFont()
                CompositionLocalProvider(LocalTextStyle provides style) {
                    Text(
                        details,
                        style = style,
                        color = if (coral) reader.ink else Color.White,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
