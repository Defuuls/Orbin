package com.orbin.uinext

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.orbin.uinext.resources.Res
import com.orbin.uinext.resources.collection_empty
import com.orbin.uinext.resources.tiktok_collection_boards
import com.orbin.uinext.resources.tiktok_collection_sites
import com.orbin.uinext.resources.tiktok_collection_threads
import com.orbin.uinext.resources.tiktok_secondary_settings
import com.orbin.uinext.tokens.NextType
import org.jetbrains.compose.resources.stringResource

/** A locally saved thread, carrying its complete source identity in [id] and [source]. */
@Immutable
data class ThreadCollectionRow(
    val id: String,
    val title: String,
    val source: String,
    val detail: String = "",
)

/** Saved uses a library grid; watched/history use compact activity rows over the same real records. */
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
                SavedCollection(rows, onOpen, padding)
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
    rows: List<ThreadCollectionRow>,
    onOpen: (ThreadCollectionRow) -> Unit,
    padding: PaddingValues,
) {
    val identities = remember(rows) { rows.map { it.boardPresentationIdentity }.distinct() }
    val sites = remember(identities) { identities.map { it.substringBefore('/') }.distinct().size }
    // Records contain titles and identities, not media URLs. Board-art covers represent those
    // records without inventing attachment thumbnails. Backend row IDs remain opaque.
    val columns = if (LocalDensity.current.fontScale > 1.3f) 2 else 3
    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = padding.calculateBottomPadding() + 16.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "saved-identity", span = { GridItemSpan(maxLineSpan) }) {
            Column(
                Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier.size(84.dp).clip(CircleShape).background(next.raised),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(ReaderIcons.SavedFilled, null, tint = next.ink, modifier = Modifier.size(38.dp))
                }
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 20.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    CollectionCount(
                        rows.size,
                        stringResource(Res.string.tiktok_collection_threads),
                        Modifier.weight(1f),
                    )
                    CollectionCount(
                        identities.size,
                        stringResource(Res.string.tiktok_collection_boards),
                        Modifier.weight(1f),
                    )
                    CollectionCount(sites, stringResource(Res.string.tiktok_collection_sites), Modifier.weight(1f))
                }
                Hairline()
                Box(Modifier.fillMaxWidth().height(44.dp), contentAlignment = Alignment.Center) {
                    Icon(ReaderIcons.Saved, null, tint = next.ink, modifier = Modifier.size(22.dp))
                }
                Box(Modifier.fillMaxWidth().height(1.5.dp).background(next.ink))
            }
        }
        if (rows.isEmpty()) {
            item(key = "saved-empty", span = { GridItemSpan(maxLineSpan) }) {
                NextEmpty(stringResource(Res.string.collection_empty))
            }
        } else {
            items(rows, key = { it.id }) { row -> SavedThreadTile(row, onOpen) }
        }
    }
}

@Composable
private fun CollectionCount(
    count: Int,
    label: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            count.toString(),
            style = NextType.titleLarge.copy(fontSize = 18.sp, lineHeight = 22.sp, fontWeight = FontWeight.Bold),
            color = next.ink,
        )
        Text(label, style = NextType.footnote, color = next.muted, textAlign = TextAlign.Center)
    }
}

@Composable
private fun SavedThreadTile(
    row: ThreadCollectionRow,
    onOpen: (ThreadCollectionRow) -> Unit,
) {
    Column(Modifier.fillMaxWidth().nextClickable(role = Role.Button) { onOpen(row) }) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(3f / 4f).background(next.raised),
            contentAlignment = Alignment.Center,
        ) {
            CollectionBoardIcon(row, Modifier.size(64.dp))
        }
        Column(Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
            Text(
                row.title,
                style = NextType.bodyMedium.copy(fontSize = 13.sp, lineHeight = 18.sp),
                color = next.ink,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                row.source,
                style = NextType.footnote.copy(fontSize = 11.sp, lineHeight = 15.sp),
                color = next.muted,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (row.detail.isNotBlank()) {
                Text(row.detail, style = NextType.footnote, color = next.muted, maxLines = 2)
            }
        }
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
