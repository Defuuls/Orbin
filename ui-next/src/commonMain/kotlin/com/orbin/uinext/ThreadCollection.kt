package com.orbin.uinext

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.orbin.uinext.resources.Res
import com.orbin.uinext.resources.collection_empty
import com.orbin.uinext.resources.reader_row_meta
import com.orbin.uinext.resources.reader_unread
import com.orbin.uinext.resources.tiktok_secondary_settings
import com.orbin.uinext.tokens.NextType
import com.orbin.uinext.tokens.ReaderSize
import com.orbin.uinext.tokens.ReaderType
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/** A locally saved thread, carrying its complete source identity in [id] and [source]. */
@Immutable
data class ThreadCollectionRow(
    val id: String,
    val title: String,
    val source: String,
    val detail: String = "",
    val time: String = "",
    val threadNumber: String = "",
    val unread: Int = 0,
)

object ReaderSavedTags {
    const val LIST = "reader-saved-list"

    fun row(id: String): String = "reader-saved-row:$id"
}

/** Saved uses reader rows; watched/history retain activity rows over the same real records. */
@Composable
fun ThreadCollectionScreen(
    title: String,
    rows: List<ThreadCollectionRow>,
    onOpen: (ThreadCollectionRow) -> Unit,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    onSettings: (() -> Unit)? = null,
    onDestination: ((NextDestination) -> Unit)? = null,
    selectedDestination: NextDestination = NextDestination.SAVED,
) {
    NextScaffold(
        where = title,
        modifier = modifier,
        destination = selectedDestination.takeIf { onBack == null },
        onDestination = onDestination,
        onBack = onBack,
        actions =
            onSettings
                ?.let {
                    listOf(ReaderAction(ReaderIcons.Settings, stringResource(Res.string.tiktok_secondary_settings), it))
                }.orEmpty(),
    ) { padding ->
        Column(
            Modifier.fillMaxSize().windowInsetsPadding(
                WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal),
            ),
        ) {
            if (selectedDestination == NextDestination.SAVED && onBack == null) {
                SavedCollection(title, rows, onOpen, padding)
            } else if (rows.isEmpty()) {
                NextEmpty(stringResource(Res.string.collection_empty))
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = padding.calculateBottomPadding() + 16.dp),
                ) {
                    items(rows, key = { it.id }) { row -> CollectionActivityRow(row, onOpen) }
                }
            }
        }
    }
}

@Composable
private fun SavedCollection(
    title: String,
    rows: List<ThreadCollectionRow>,
    onOpen: (ThreadCollectionRow) -> Unit,
    padding: PaddingValues,
) {
    // Stored records have source identities and text metadata, not guaranteed attachment data.
    // Keep the reading surface compact without fabricating media covers or a profile header.
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(reader.background).testTag(ReaderSavedTags.LIST),
        contentPadding = PaddingValues(bottom = padding.calculateBottomPadding()),
    ) {
        if (rows.isEmpty()) {
            item(key = "reader-saved-empty") {
                ReaderEmptyState(
                    title = title,
                    message = stringResource(Res.string.collection_empty),
                    modifier = Modifier.fillParentMaxSize(),
                )
            }
        } else {
            items(rows, key = { it.id }, contentType = { "saved-thread" }) { row ->
                SavedThreadRow(row, onOpen)
            }
        }
    }
}

@Composable
private fun SavedThreadRow(
    row: ThreadCollectionRow,
    onOpen: (ThreadCollectionRow) -> Unit,
) {
    val metadata =
        if (row.threadNumber.isNotBlank()) {
            stringResource(Res.string.reader_row_meta, row.threadNumber, row.time).trimEnd(' ', '·')
        } else {
            row.time
        }
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(min = ReaderSize.touchTarget)
            .background(reader.background)
            .testTag(ReaderSavedTags.row(row.id))
            .nextClickable(role = Role.Button) { onOpen(row) }
            .semantics(mergeDescendants = true) {},
    ) {
        ReaderCellLayout(contentMaxWidth = ReaderSize.readingMax) { textModifier ->
            Text(row.title, style = ReaderType.rowTitle, color = reader.ink, modifier = textModifier)
            Text(row.source, style = ReaderType.meta, color = reader.inkMuted, modifier = textModifier)
            if (metadata.isNotBlank()) {
                Text(metadata, style = ReaderType.meta, color = reader.inkMuted, modifier = textModifier)
            }
            if (row.detail.isNotBlank()) {
                Text(row.detail, style = ReaderType.preview, color = reader.inkMuted, modifier = textModifier)
            }
            if (row.unread > 0) {
                Text(
                    pluralStringResource(Res.plurals.reader_unread, row.unread, row.unread),
                    style = ReaderType.meta,
                    color = reader.accent,
                    modifier = textModifier,
                )
            }
        }
        ReaderHairline()
    }
}

@Composable
private fun CollectionActivityRow(
    row: ThreadCollectionRow,
    onOpen: (ThreadCollectionRow) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 80.dp)
            .nextClickable(role = Role.Button) { onOpen(row) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CollectionBoardIcon(row, Modifier.size(48.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                row.title,
                style = NextType.body.copy(fontWeight = FontWeight.SemiBold),
                color = next.ink,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(row.source, style = NextType.bodyMedium, color = next.muted)
            if (row.detail.isNotBlank()) Text(row.detail, style = NextType.footnote, color = next.muted)
        }
        Icon(ReaderIcons.ChevronRight, null, tint = next.faint, modifier = Modifier.size(16.dp))
    }
}

@Composable
private fun CollectionBoardIcon(
    row: ThreadCollectionRow,
    modifier: Modifier,
) {
    val identity = row.boardPresentationIdentity
    BoardIcon(
        siteId = identity.substringBefore('/'),
        boardId = identity.substringAfter('/'),
        title = row.title,
        modifier = modifier,
    )
}

/** Both adapters already format this label as a source name followed by a slash board path. */
private val ThreadCollectionRow.boardPresentationIdentity: String
    get() = boardIconIdentity(source.substringBefore(" · "), source.substringAfter(" · ", ""))
