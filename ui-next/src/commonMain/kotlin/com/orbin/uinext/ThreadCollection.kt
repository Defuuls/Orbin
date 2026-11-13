package com.orbin.uinext

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.orbin.uinext.resources.Res
import com.orbin.uinext.resources.collection_empty
import com.orbin.uinext.resources.reader_row_meta
import com.orbin.uinext.resources.reader_secondary_settings
import com.orbin.uinext.resources.reader_unread
import com.orbin.uinext.tokens.ReaderSpace
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

object ReaderCollectionTags {
    const val LIST = "reader-collection-list"

    fun row(id: String): String = "reader-collection-row:$id"
}

object ReaderSavedTags {
    const val LIST = "reader-saved-list"

    fun row(id: String): String = "reader-saved-row:$id"
}

/** Saved, watched and history share Coral cards over their actual source-qualified records. */
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
                    listOf(ReaderAction(ReaderIcons.Settings, stringResource(Res.string.reader_secondary_settings), it))
                }.orEmpty(),
    ) { padding ->
        Box(
            Modifier.fillMaxSize().windowInsetsPadding(
                WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal),
            ),
        ) {
            val saved = selectedDestination == NextDestination.SAVED && onBack == null
            ReaderCollection(title, rows, onOpen, padding, wideWindow = readerRecordUsesWideText(), saved = saved)
        }
    }
}

@Composable
private fun ReaderCollection(
    title: String,
    rows: List<ThreadCollectionRow>,
    onOpen: (ThreadCollectionRow) -> Unit,
    padding: PaddingValues,
    wideWindow: Boolean,
    saved: Boolean,
) {
    // Stable record identities retain the current reading anchor when another item is inserted.
    LazyColumn(
        modifier =
            Modifier
                .fillMaxSize()
                .background(reader.background)
                .testTag(if (saved) ReaderSavedTags.LIST else ReaderCollectionTags.LIST),
        contentPadding =
            PaddingValues(
                start = ReaderSpace.gutter,
                end = ReaderSpace.gutter,
                top = COLLECTION_GAP,
                bottom = COLLECTION_GAP + padding.calculateBottomPadding(),
            ),
        verticalArrangement = Arrangement.spacedBy(COLLECTION_GAP),
    ) {
        if (rows.isEmpty()) {
            item(key = "reader-collection-empty") {
                ReaderEmptyState(
                    title = title,
                    message = stringResource(Res.string.collection_empty),
                    modifier = Modifier.fillParentMaxSize(),
                )
            }
        } else {
            items(rows, key = { it.id }, contentType = { "stored-thread" }) { row ->
                StoredThreadRow(row, onOpen, wideWindow, saved)
            }
        }
    }
}

private val COLLECTION_GAP = 8.dp

@Composable
private fun StoredThreadRow(
    row: ThreadCollectionRow,
    onOpen: (ThreadCollectionRow) -> Unit,
    wideWindow: Boolean,
    saved: Boolean,
) {
    val metadata =
        if (row.threadNumber.isNotBlank()) {
            stringResource(Res.string.reader_row_meta, row.threadNumber, row.time).trimEnd(' ', '·')
        } else {
            row.time
        }
    ReaderRecordRow(
        title = row.title,
        source = row.source,
        metadata = metadata,
        detail = row.detail,
        unread = if (row.unread > 0) pluralStringResource(Res.plurals.reader_unread, row.unread, row.unread) else "",
        onOpen = { onOpen(row) },
        modifier = Modifier.testTag(if (saved) ReaderSavedTags.row(row.id) else ReaderCollectionTags.row(row.id)),
        wideWindow = wideWindow,
    )
}
