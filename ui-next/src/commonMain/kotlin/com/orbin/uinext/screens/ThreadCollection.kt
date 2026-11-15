package com.orbin.uinext.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.orbin.uinext.components.NextDestination
import com.orbin.uinext.components.NextScaffold
import com.orbin.uinext.components.ReaderAction
import com.orbin.uinext.components.ReaderIcons
import com.orbin.uinext.components.ReaderRecordRow
import com.orbin.uinext.components.reader
import com.orbin.uinext.components.readerRecordUsesWideText
import com.orbin.uinext.resources.Res
import com.orbin.uinext.resources.collection_archived
import com.orbin.uinext.resources.collection_cancel
import com.orbin.uinext.resources.collection_clear
import com.orbin.uinext.resources.collection_clear_question
import com.orbin.uinext.resources.collection_deleted
import com.orbin.uinext.resources.collection_empty
import com.orbin.uinext.resources.collection_live
import com.orbin.uinext.resources.collection_manage
import com.orbin.uinext.resources.collection_mark_all_read
import com.orbin.uinext.resources.collection_mark_read
import com.orbin.uinext.resources.collection_newest
import com.orbin.uinext.resources.collection_oldest
import com.orbin.uinext.resources.collection_refresh
import com.orbin.uinext.resources.collection_refreshing
import com.orbin.uinext.resources.collection_remove
import com.orbin.uinext.resources.collection_remove_deleted
import com.orbin.uinext.resources.reader_row_meta
import com.orbin.uinext.resources.reader_secondary_settings
import com.orbin.uinext.tokens.ReaderSpace
import org.jetbrains.compose.resources.stringResource

enum class ThreadCollectionStatus { UNKNOWN, LIVE, ARCHIVED, DELETED }

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
    val availability: ThreadCollectionStatus = ThreadCollectionStatus.UNKNOWN,
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
    newestFirst: Boolean = true,
    refreshing: Boolean = false,
    onRefresh: (() -> Unit)? = null,
    onToggleOrder: (() -> Unit)? = null,
    onRemove: ((ThreadCollectionRow) -> Unit)? = null,
    onMarkRead: ((ThreadCollectionRow) -> Unit)? = null,
    onMarkAllRead: (() -> Unit)? = null,
    onClear: (() -> Unit)? = null,
    onRemoveDeleted: (() -> Unit)? = null,
) {
    var selectedRow by remember { mutableStateOf<ThreadCollectionRow?>(null) }
    var showManage by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
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
            ReaderCollection(
                title,
                rows,
                onOpen,
                padding,
                wideWindow = readerRecordUsesWideText(),
                saved = saved,
                onManage = { showManage = true }.takeIf { onToggleOrder != null || onClear != null },
                onLongClick =
                    { row: ThreadCollectionRow -> selectedRow = row }.takeIf {
                        onRemove != null ||
                            onMarkRead != null
                    },
            )
        }
    }
    if (showManage) {
        CollectionManagementDialog(
            title,
            newestFirst,
            refreshing,
            onRefresh,
            onToggleOrder,
            onMarkAllRead,
            onRemoveDeleted,
            onClear,
            onDismiss = { showManage = false },
            onConfirmClear = { confirmClear = true },
        )
    }
    selectedRow?.let { row -> CollectionRowDialog(row, onMarkRead, onRemove) { selectedRow = null } }
    if (confirmClear) CollectionClearDialog(onClear) { confirmClear = false }
}

@Composable
private fun CollectionManagementDialog(
    title: String,
    newestFirst: Boolean,
    refreshing: Boolean,
    onRefresh: (() -> Unit)?,
    onToggleOrder: (() -> Unit)?,
    onMarkAllRead: (() -> Unit)?,
    onRemoveDeleted: (() -> Unit)?,
    onClear: (() -> Unit)?,
    onDismiss: () -> Unit,
    onConfirmClear: () -> Unit,
) {
    AlertDialog(onDismissRequest = { onDismiss() }, title = { Text(title) }, text = {
        androidx.compose.foundation.layout.Column(Modifier.verticalScroll(rememberScrollState())) {
            onRefresh?.let { action ->
                TextButton(modifier = Modifier.heightIn(min = 48.dp), onClick = {
                    onDismiss()
                    action()
                }, enabled = !refreshing) {
                    Text(
                        stringResource(
                            if (refreshing) Res.string.collection_refreshing else Res.string.collection_refresh,
                        ),
                    )
                }
            }
            onToggleOrder?.let { action ->
                TextButton(modifier = Modifier.heightIn(min = 48.dp), onClick = {
                    onDismiss()
                    action()
                }) {
                    Text(
                        stringResource(if (newestFirst) Res.string.collection_oldest else Res.string.collection_newest),
                    )
                }
            }
            onMarkAllRead?.let { action ->
                TextButton(modifier = Modifier.heightIn(min = 48.dp), onClick = {
                    onDismiss()
                    action()
                }) {
                    Text(stringResource(Res.string.collection_mark_all_read))
                }
            }
            onRemoveDeleted?.let { action ->
                TextButton(modifier = Modifier.heightIn(min = 48.dp), onClick = {
                    onDismiss()
                    action()
                }) {
                    Text(stringResource(Res.string.collection_remove_deleted))
                }
            }
            onClear?.let {
                TextButton(modifier = Modifier.heightIn(min = 48.dp), onClick = {
                    onDismiss()
                    onConfirmClear()
                }) {
                    Text(stringResource(Res.string.collection_clear))
                }
            }
        }
    }, confirmButton = {
        TextButton(modifier = Modifier.heightIn(min = 48.dp), onClick = {
            onDismiss()
        }) { Text(stringResource(Res.string.collection_cancel)) }
    })
}

@Composable
private fun CollectionRowDialog(
    row: ThreadCollectionRow,
    onMarkRead: ((ThreadCollectionRow) -> Unit)?,
    onRemove: ((ThreadCollectionRow) -> Unit)?,
    onDismiss: () -> Unit,
) {
    AlertDialog(onDismissRequest = { onDismiss() }, title = { Text(row.title) }, text = {
        androidx.compose.foundation.layout.Column(Modifier.verticalScroll(rememberScrollState())) {
            onMarkRead?.let { action ->
                TextButton(modifier = Modifier.heightIn(min = 48.dp), onClick = {
                    onDismiss()
                    action(row)
                }) {
                    Text(stringResource(Res.string.collection_mark_read))
                }
            }
            onRemove?.let { action ->
                TextButton(modifier = Modifier.heightIn(min = 48.dp), onClick = {
                    onDismiss()
                    action(row)
                }) {
                    Text(stringResource(Res.string.collection_remove))
                }
            }
        }
    }, confirmButton = {
        TextButton(modifier = Modifier.heightIn(min = 48.dp), onClick = {
            onDismiss()
        }) { Text(stringResource(Res.string.collection_cancel)) }
    })
}

@Composable
private fun CollectionClearDialog(
    onClear: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = { onDismiss() },
        text = { Text(stringResource(Res.string.collection_clear_question)) },
        confirmButton = {
            TextButton(modifier = Modifier.heightIn(min = 48.dp), onClick = {
                onDismiss()
                onClear?.invoke()
            }) {
                Text(stringResource(Res.string.collection_clear))
            }
        },
        dismissButton = {
            TextButton(modifier = Modifier.heightIn(min = 48.dp), onClick = {
                onDismiss()
            }) { Text(stringResource(Res.string.collection_cancel)) }
        },
    )
}

@Composable
private fun ReaderCollection(
    title: String,
    rows: List<ThreadCollectionRow>,
    onOpen: (ThreadCollectionRow) -> Unit,
    padding: PaddingValues,
    wideWindow: Boolean,
    saved: Boolean,
    onManage: (() -> Unit)?,
    onLongClick: ((ThreadCollectionRow) -> Unit)?,
) {
    // Stable record identities retain the current reading anchor when another item is inserted.
    Box(Modifier.fillMaxSize().background(reader.background), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier =
                Modifier
                    .widthIn(max = 720.dp)
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
            onManage?.let { action ->
                item(key = "reader-collection-manage") {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(modifier = Modifier.heightIn(min = 48.dp), onClick = action) {
                            Text(stringResource(Res.string.collection_manage))
                        }
                    }
                }
            }
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
                    StoredThreadRow(row, onOpen, wideWindow, saved, onLongClick)
                }
            }
        }
    }
}

private val COLLECTION_GAP = 10.dp

@Composable
private fun StoredThreadRow(
    row: ThreadCollectionRow,
    onOpen: (ThreadCollectionRow) -> Unit,
    wideWindow: Boolean,
    saved: Boolean,
    onLongClick: ((ThreadCollectionRow) -> Unit)?,
) {
    val status =
        when (row.availability) {
            ThreadCollectionStatus.LIVE -> stringResource(Res.string.collection_live)
            ThreadCollectionStatus.ARCHIVED -> stringResource(Res.string.collection_archived)
            ThreadCollectionStatus.DELETED -> stringResource(Res.string.collection_deleted)
            ThreadCollectionStatus.UNKNOWN -> ""
        }
    val metadata =
        if (row.threadNumber.isNotBlank()) {
            stringResource(Res.string.reader_row_meta, row.threadNumber, row.time).trimEnd(' ', '·')
        } else {
            row.time
        }
    ReaderRecordRow(
        title = row.title,
        source = row.source,
        metadata = listOf(metadata, status).filter { it.isNotBlank() }.joinToString(" · "),
        detail = row.detail,
        unread = row.unread,
        onOpen = { onOpen(row) },
        onLongClick = onLongClick?.let { action -> { action(row) } },
        onLongClickLabel = stringResource(Res.string.collection_manage),
        modifier = Modifier.testTag(if (saved) ReaderSavedTags.row(row.id) else ReaderCollectionTags.row(row.id)),
        wideWindow = wideWindow,
    )
}
