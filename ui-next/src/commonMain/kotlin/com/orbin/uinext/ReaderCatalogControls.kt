package com.orbin.uinext

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.orbin.uinext.resources.Res
import com.orbin.uinext.resources.reader_catalog_filter
import com.orbin.uinext.resources.reader_close_filter
import com.orbin.uinext.resources.reader_dismiss_picker
import com.orbin.uinext.resources.reader_pick_board
import com.orbin.uinext.resources.reader_picker_empty
import com.orbin.uinext.tokens.OrbinRadius
import com.orbin.uinext.tokens.OrbinType
import com.orbin.uinext.tokens.ReaderSize
import com.orbin.uinext.tokens.ReaderSpace
import org.jetbrains.compose.resources.stringResource

/** A real board identity, grouped by its owning site in the catalog picker. */
@Immutable
data class ReaderBoardOption(
    val providerId: String,
    val siteLabel: String,
    val boardId: String,
    val title: String,
)

@Composable
fun ReaderCatalogSearch(
    query: String,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .testTag(
                "reader-catalog-search",
            ).fillMaxWidth()
            .background(reader.background)
            .padding(horizontal = ReaderSpace.gutter),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val label = stringResource(Res.string.reader_catalog_filter)
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            singleLine = true,
            textStyle = OrbinType.stateBody.withReaderFont().copy(color = reader.ink),
            cursorBrush = SolidColor(reader.accent),
            modifier =
                Modifier
                    .testTag("reader-catalog-query")
                    .weight(1f)
                    .heightIn(min = ReaderSize.touchTarget)
                    .semantics { contentDescription = label },
            decorationBox = { inner ->
                Box(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = ReaderSize.touchTarget)
                        .clip(RoundedCornerShape(OrbinRadius.small))
                        .background(reader.surfaceVariant)
                        .padding(horizontal = ReaderSpace.gapSmall, vertical = ReaderSpace.line),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (query.isEmpty()) {
                        Text(
                            label,
                            style = OrbinType.stateBody.withReaderFont(),
                            color = reader.inkMuted,
                        )
                    }
                    inner()
                }
            },
        )
        NextIconAction(ReaderIcons.Close, stringResource(Res.string.reader_close_filter), onClose, tint = reader.ink)
    }
}

/** Site grouping stays visible when several providers expose the same board path. */
@Composable
fun ReaderBoardPicker(
    boards: List<ReaderBoardOption>,
    onSelect: (ReaderBoardOption) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    selectedProvider: String? = null,
    selectedBoard: String? = null,
) {
    val grouped = remember(boards) { boards.groupBy { it.providerId }.values.sortedBy { it.firstOrNull()?.siteLabel } }
    AlertDialog(
        modifier = modifier.testTag("reader-board-picker"),
        onDismissRequest = onDismiss,
        containerColor = reader.background,
        titleContentColor = reader.ink,
        textContentColor = reader.ink,
        title = { Text(stringResource(Res.string.reader_pick_board), style = OrbinType.appBarTitle.withReaderFont()) },
        text = {
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = ReaderSize.readingMax)) {
                if (boards.isEmpty()) {
                    item {
                        Text(
                            stringResource(Res.string.reader_picker_empty),
                            style = OrbinType.stateBody.withReaderFont(),
                            color = reader.inkMuted,
                        )
                    }
                }
                grouped.forEach { siteBoards ->
                    item(key = "site-${siteBoards.first().providerId}") {
                        Text(
                            siteBoards.first().siteLabel,
                            style = OrbinType.label.withReaderFont(),
                            color = reader.inkMuted,
                            modifier = Modifier.padding(vertical = ReaderSpace.gapSmall).semantics { heading() },
                        )
                    }
                    items(siteBoards.sortedBy { it.boardId }, key = { "${it.providerId}/${it.boardId}" }) { board ->
                        Column(
                            Modifier
                                .testTag("reader-board-option-${board.providerId}/${board.boardId}")
                                .fillMaxWidth()
                                .heightIn(min = ReaderSize.touchTarget)
                                .selectable(
                                    selected = board.providerId == selectedProvider && board.boardId == selectedBoard,
                                    role = Role.Button,
                                    onClick = { onSelect(board) },
                                ).padding(vertical = ReaderSpace.line),
                        ) {
                            Text("/${board.boardId}/", style = OrbinType.rowTitle.withReaderFont(), color = reader.ink)
                            Text(board.title, style = OrbinType.preview.withReaderFont(), color = reader.inkMuted)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    stringResource(Res.string.reader_dismiss_picker),
                    style = OrbinType.label.withReaderFont(),
                    color = reader.accent,
                )
            }
        },
    )
}
