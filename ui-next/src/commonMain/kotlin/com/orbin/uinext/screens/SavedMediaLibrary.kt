package com.orbin.uinext.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.orbin.uinext.components.MIN_TOUCH_TARGET
import com.orbin.uinext.components.NextConfirmDialog
import com.orbin.uinext.components.NextLinearProgress
import com.orbin.uinext.components.OrbinCard
import com.orbin.uinext.components.PlatformSegments
import com.orbin.uinext.components.ReaderIcons
import com.orbin.uinext.components.ReaderSpoilerCover
import com.orbin.uinext.components.SchematicSearch
import com.orbin.uinext.components.next
import com.orbin.uinext.components.nextClickable
import com.orbin.uinext.components.reader
import com.orbin.uinext.resources.Res
import com.orbin.uinext.resources.saved_media_all
import com.orbin.uinext.resources.saved_media_delete
import com.orbin.uinext.resources.saved_media_delete_message
import com.orbin.uinext.resources.saved_media_delete_title
import com.orbin.uinext.resources.saved_media_done
import com.orbin.uinext.resources.saved_media_empty
import com.orbin.uinext.resources.saved_media_empty_hint
import com.orbin.uinext.resources.saved_media_export
import com.orbin.uinext.resources.saved_media_images
import com.orbin.uinext.resources.saved_media_largest
import com.orbin.uinext.resources.saved_media_name
import com.orbin.uinext.resources.saved_media_newest
import com.orbin.uinext.resources.saved_media_no_matches
import com.orbin.uinext.resources.saved_media_no_matches_hint
import com.orbin.uinext.resources.saved_media_oldest
import com.orbin.uinext.resources.saved_media_progress
import com.orbin.uinext.resources.saved_media_search
import com.orbin.uinext.resources.saved_media_select
import com.orbin.uinext.resources.saved_media_select_all
import com.orbin.uinext.resources.saved_media_selected
import com.orbin.uinext.resources.saved_media_share
import com.orbin.uinext.resources.saved_media_videos
import com.orbin.uinext.tokens.OrbinType
import com.orbin.uinext.tokens.ReaderSpace
import org.jetbrains.compose.resources.stringResource

/** Presentation-only saved bytes, independent of thread bookmarks and transfer history. */
@Immutable
data class SavedMediaItem(
    val id: String,
    val fileName: String,
    val source: String,
    val kind: SavedMediaKind,
    val byteCount: Long,
    val savedAtMillis: Long,
    val width: Int = 0,
    val height: Int = 0,
    val covered: Boolean = false,
)

enum class SavedMediaKind { IMAGE, VIDEO, OTHER }

enum class SavedMediaOrder { NEWEST, OLDEST, NAME, LARGEST }

@Immutable
data class SavedMediaOperation(
    val label: String,
    val completed: Int,
    val total: Int,
    val message: String? = null,
    val running: Boolean = true,
)

/** Stable identity breaks ties and selection survives search and ordering. */
fun visibleSavedMedia(
    items: List<SavedMediaItem>,
    query: String,
    kind: SavedMediaKind?,
    order: SavedMediaOrder,
): List<SavedMediaItem> {
    val text = query.trim()
    val comparator =
        when (order) {
            SavedMediaOrder.NEWEST -> compareByDescending<SavedMediaItem> { it.savedAtMillis }
            SavedMediaOrder.OLDEST -> compareBy { it.savedAtMillis }
            SavedMediaOrder.NAME -> compareBy { it.fileName.lowercase() }
            SavedMediaOrder.LARGEST -> compareByDescending { it.byteCount }
        }.thenBy { it.id }
    return items
        .filter { item ->
            (kind == null || item.kind == kind) &&
                (text.isEmpty() || item.fileName.contains(text, true) || item.source.contains(text, true))
        }.sortedWith(comparator)
}

/** The platform supplies actual offline thumbnails, file actions, and the local media viewer. */
@Composable
fun SavedMediaLibraryScreen(
    items: List<SavedMediaItem>,
    selectedIds: Set<String>,
    onSelectionChange: (Set<String>) -> Unit,
    onOpen: (SavedMediaItem) -> Unit,
    onShare: ((List<String>) -> Unit)?,
    onExport: ((List<String>) -> Unit)?,
    onDelete: (List<String>) -> Unit,
    modifier: Modifier = Modifier,
    operation: SavedMediaOperation? = null,
    thumbnail: @Composable (SavedMediaItem, Modifier) -> Unit = { item, tile -> SavedMediaPlaceholder(item, tile) },
) {
    var query by rememberSaveable { mutableStateOf("") }
    var kindIndex by rememberSaveable { mutableStateOf(0) }
    var orderIndex by rememberSaveable { mutableStateOf(0) }
    var selecting by rememberSaveable { mutableStateOf(false) }
    var deleteIds by remember { mutableStateOf<List<String>?>(null) }
    val coverIdentity = remember(items) { items.map { it.id to it.covered } }
    var revealed by remember(coverIdentity) { mutableStateOf(emptySet<String>()) }
    val liveIds = remember(items) { items.mapTo(mutableSetOf()) { it.id } }
    val selected = selectedIds.intersect(liveIds)
    val busy = operation?.running == true
    LaunchedEffect(liveIds, selectedIds) {
        if (selected != selectedIds) onSelectionChange(selected)
    }
    val kind =
        when (kindIndex) {
            1 -> SavedMediaKind.IMAGE
            2 -> SavedMediaKind.VIDEO
            else -> null
        }
    val visible =
        remember(items, query, kind, orderIndex) {
            visibleSavedMedia(items, query, kind, SavedMediaOrder.entries[orderIndex])
        }
    val labels =
        listOf(
            stringResource(Res.string.saved_media_newest),
            stringResource(Res.string.saved_media_oldest),
            stringResource(Res.string.saved_media_name),
            stringResource(Res.string.saved_media_largest),
        )
    val selectionMode = selecting || selected.isNotEmpty()
    val toggle: (String) -> Unit = { id -> onSelectionChange(if (id in selected) selected - id else selected + id) }
    Column(modifier.fillMaxSize().background(next.background)) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = ReaderSpace.gutter),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SchematicSearch(query, { query = it }, stringResource(Res.string.saved_media_search))
            PlatformSegments(
                listOf(
                    stringResource(Res.string.saved_media_all),
                    stringResource(Res.string.saved_media_images),
                    stringResource(Res.string.saved_media_videos),
                ),
                kindIndex,
                { kindIndex = it },
            )
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { PlatformSegments(labels, orderIndex, { orderIndex = it }, compact = true) }
                LibraryAction(
                    if (selectionMode) {
                        stringResource(
                            Res.string.saved_media_done,
                        )
                    } else {
                        stringResource(Res.string.saved_media_select)
                    },
                    !busy,
                ) {
                    selecting = !selectionMode
                    onSelectionChange(emptySet())
                }
            }
            if (selectionMode) {
                Text(
                    stringResource(Res.string.saved_media_selected, selected.size),
                    style = OrbinType.body,
                    color = next.ink,
                    fontWeight = FontWeight.Bold,
                )
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    LibraryAction(stringResource(Res.string.saved_media_select_all), !busy && visible.isNotEmpty()) {
                        onSelectionChange(selected + visible.map { it.id })
                    }
                    onShare?.let { share ->
                        LibraryAction(
                            stringResource(Res.string.saved_media_share),
                            !busy && selected.isNotEmpty(),
                        ) { share(selected.toList()) }
                    }
                    onExport?.let { export ->
                        LibraryAction(
                            stringResource(Res.string.saved_media_export),
                            !busy && selected.isNotEmpty(),
                        ) { export(selected.toList()) }
                    }
                    LibraryAction(stringResource(Res.string.saved_media_delete), !busy && selected.isNotEmpty()) {
                        deleteIds =
                            selected.toList()
                    }
                }
            }
            operation?.let { action ->
                Column(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(action.label, style = OrbinType.body, color = next.ink)
                    if (action.running) {
                        NextLinearProgress(
                            progress =
                                if (action.total >
                                    0
                                ) {
                                    action.completed.toFloat() / action.total
                                } else {
                                    null
                                },
                        )
                        Text(
                            stringResource(Res.string.saved_media_progress, action.completed, action.total),
                            style = OrbinType.label,
                            color = next.muted,
                        )
                    }
                    action.message?.let { Text(it, style = OrbinType.body, color = next.muted) }
                }
            }
        }
        if (visible.isEmpty()) {
            ReaderEmptyState(
                title =
                    stringResource(
                        if (items.isEmpty()) Res.string.saved_media_empty else Res.string.saved_media_no_matches,
                    ),
                message =
                    stringResource(
                        if (items.isEmpty()) {
                            Res.string.saved_media_empty_hint
                        } else {
                            Res.string.saved_media_no_matches_hint
                        },
                    ),
                modifier = Modifier.weight(1f),
            )
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(152.dp),
                modifier = Modifier.fillMaxWidth().weight(1f).testTag("orbin-saved-media"),
                contentPadding = PaddingValues(ReaderSpace.gutter),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(visible, key = { it.id }) { item ->
                    val isSelected = item.id in selected
                    val selectLabel = stringResource(Res.string.saved_media_select)
                    OrbinCard(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .testTag("orbin-saved-media:${item.id}")
                                .nextClickable(
                                    enabled = !busy,
                                    onLongClickLabel = selectLabel,
                                    onLongClick = {
                                        selecting = true
                                        toggle(item.id)
                                    },
                                    onClick = { if (selectionMode) toggle(item.id) else onOpen(item) },
                                ).semantics { this.selected = isSelected },
                        elevated = false,
                    ) {
                        Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(22.dp))) {
                            if (item.covered && item.id !in revealed) {
                                ReaderSpoilerCover(
                                    onReveal = { revealed = revealed + item.id },
                                    modifier = Modifier.fillMaxSize(),
                                )
                            } else {
                                thumbnail(item, Modifier.fillMaxSize())
                            }
                            if (selectionMode) {
                                Checkbox(
                                    isSelected,
                                    onCheckedChange = null,
                                    modifier = Modifier.align(Alignment.TopEnd),
                                )
                            }
                        }
                        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                item.fileName,
                                style = OrbinType.body,
                                color = next.ink,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                listOf(
                                    item.source,
                                    libraryBytes(item.byteCount),
                                ).filter { it.isNotBlank() }.joinToString(" · "),
                                style = OrbinType.label,
                                color = next.muted,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
    deleteIds?.let { ids ->
        NextConfirmDialog(
            title = stringResource(Res.string.saved_media_delete_title),
            message = stringResource(Res.string.saved_media_delete_message, ids.size),
            onDismiss = { deleteIds = null },
            onConfirm = {
                deleteIds = null
                onDelete(ids)
            },
        )
    }
}

@Composable
private fun LibraryAction(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .heightIn(min = MIN_TOUCH_TARGET)
            .widthIn(min = MIN_TOUCH_TARGET)
            .clip(RoundedCornerShape(14.dp))
            .nextClickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = OrbinType.label, color = if (enabled) reader.accent else next.muted)
    }
}

@Composable
private fun SavedMediaPlaceholder(
    item: SavedMediaItem,
    modifier: Modifier,
) {
    Box(modifier.background(reader.surfaceVariant), contentAlignment = Alignment.Center) {
        Icon(
            if (item.kind == SavedMediaKind.VIDEO) Icons.Filled.PlayArrow else ReaderIcons.Saved,
            contentDescription = null,
            tint = next.muted,
            modifier = Modifier.size(36.dp),
        )
    }
}

private fun libraryBytes(bytes: Long): String =
    when {
        bytes <= 0 -> ""
        bytes < LIBRARY_KIBIBYTE -> "$bytes B"
        bytes < LIBRARY_MEBIBYTE -> "${bytes / LIBRARY_KIBIBYTE} KB"
        else -> "${bytes / LIBRARY_MEBIBYTE} MB"
    }

private const val LIBRARY_KIBIBYTE = 1024L
private const val LIBRARY_MEBIBYTE = LIBRARY_KIBIBYTE * LIBRARY_KIBIBYTE
