package com.orbin.uinext

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.orbin.uinext.resources.Res
import com.orbin.uinext.resources.next_row_counts
import com.orbin.uinext.resources.next_row_counts_unread
import com.orbin.uinext.resources.next_row_files
import com.orbin.uinext.resources.next_row_replies
import com.orbin.uinext.resources.next_search_threads_prompt
import com.orbin.uinext.resources.next_search_title
import com.orbin.uinext.resources.next_settings_title
import com.orbin.uinext.resources.reader_feed_choose_source
import com.orbin.uinext.resources.reader_feed_home
import com.orbin.uinext.resources.reader_feed_more
import com.orbin.uinext.resources.reader_feed_muted
import com.orbin.uinext.resources.reader_feed_open_thread
import com.orbin.uinext.resources.reader_feed_post_metadata
import com.orbin.uinext.resources.reader_feed_poster_time
import com.orbin.uinext.resources.reader_feed_save
import com.orbin.uinext.resources.reader_feed_share
import com.orbin.uinext.resources.reader_feed_unsave
import com.orbin.uinext.resources.reader_feed_unwatch
import com.orbin.uinext.resources.reader_feed_watch
import com.orbin.uinext.tokens.OrbinRadius
import com.orbin.uinext.tokens.OrbinType
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun FeedHeader(
    subtitle: String,
    headerContent: @Composable () -> Unit = {},
    query: String = "",
    onQueryChange: (String) -> Unit = {},
    onSettings: (() -> Unit)? = null,
    onSearch: (() -> Unit)? = null,
    sourceLabels: List<String> = emptyList(),
    selectedSource: Int = 0,
    onSelectSource: (Int) -> Unit = {},
    statusMessage: String? = null,
) {
    var searchVisible by rememberSaveable { mutableStateOf(false) }
    var sourcePickerVisible by rememberSaveable { mutableStateOf(false) }
    val source = sourceLabels.getOrNull(selectedSource).orEmpty()
    val chooseSource = stringResource(Res.string.reader_feed_choose_source)
    Column {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp).heightIn(min = 48.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NextIconAction(
                imageVector = ReaderIcons.Search,
                contentDescription = stringResource(Res.string.next_search_title),
                onClick = { if (onSearch != null) onSearch() else searchVisible = !searchVisible },
                tint = next.ink,
            )
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Row(
                    modifier =
                        Modifier
                            .heightIn(min = 48.dp)
                            .then(
                                if (sourceLabels.isNotEmpty()) {
                                    Modifier
                                        .semantics {
                                            contentDescription = chooseSource
                                            stateDescription =
                                                listOf(source, subtitle).filter(String::isNotBlank).joinToString(", ")
                                        }.nextClickable(role = Role.Button) { sourcePickerVisible = true }
                                } else {
                                    Modifier
                                },
                            ),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        source.takeIf { selectedSource > 0 && it.isNotBlank() }
                            ?: stringResource(Res.string.reader_feed_home),
                        style = OrbinType.titleLarge.copy(fontSize = 24.sp, lineHeight = 29.sp),
                        color = next.ink,
                        modifier =
                            Modifier.testTag(NextTitleTags.LARGE).semantics {
                                heading()
                                if (subtitle.isNotBlank()) stateDescription = subtitle
                            },
                    )
                    if (sourceLabels.isNotEmpty()) {
                        Icon(ReaderIcons.ChevronDown, null, tint = next.ink, modifier = Modifier.size(16.dp))
                    }
                }
            }
            if (onSettings != null) {
                NextIconAction(
                    imageVector = ReaderIcons.Menu,
                    contentDescription = stringResource(Res.string.next_settings_title),
                    onClick = onSettings,
                    tint = next.ink,
                )
            } else {
                Spacer(Modifier.size(48.dp))
            }
        }
        Column(Modifier.padding(horizontal = GUTTER)) {
            headerContent()
            if (searchVisible || query.isNotEmpty()) {
                Gap(8)
                SchematicSearch(query, onQueryChange, stringResource(Res.string.next_search_threads_prompt))
                Gap(8)
            }
            if (!statusMessage.isNullOrBlank()) {
                Text(
                    statusMessage,
                    style = OrbinType.footnote,
                    color = next.muted,
                    modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
                )
            }
        }
    }
    if (sourcePickerVisible) {
        FeedSourceSheet(
            labels = sourceLabels,
            selected = selectedSource,
            onSelect = { index ->
                onSelectSource(index)
                sourcePickerVisible = false
            },
            onDismiss = { sourcePickerVisible = false },
        )
    }
}

/** Source names are formatted by adapters; a board always keeps its familiar slash notation. */
internal val FeedRow.sourceAndBoard: String
    get() {
        val path = "/${board.trim('/')}/"
        return if (site.isBlank()) path else "$site · $path"
    }

@Composable
internal fun FeedGridCell(
    row: FeedRow,
    seed: Int,
    onClick: (FeedRow) -> Unit,
    thumbnail: (@Composable (FeedRow, Modifier) -> Unit)?,
    modifier: Modifier = Modifier,
    activityText: @Composable (FeedRow) -> String = { it.activity },
    showBoard: Boolean = true,
    excerptLines: Int = 2,
    tallMedia: Boolean = false,
    uniform: Boolean = false,
    onToggleWatch: ((FeedRow) -> Unit)? = null,
    onToggleSaved: ((FeedRow) -> Unit)? = null,
    onShareRow: ((FeedRow) -> Unit)? = null,
) {
    if (row.muted) {
        CollapsedFeedRow(row, modifier, showBoard, onClick)
        return
    }
    var optionsVisible by rememberSaveable(row.id) { mutableStateOf(false) }
    val inset = if (tallMedia) GRID_TEXT_INSET else 8.dp
    Card(
        onClick = { onClick(row) },
        modifier = modifier.padding(if (tallMedia) 0.dp else 4.dp).semantics { role = Role.Button },
        shape = RoundedCornerShape(if (tallMedia) 0.dp else OrbinRadius.compactCard),
        border = if (tallMedia) null else BorderStroke(1.dp, next.hairline),
        colors = CardDefaults.cardColors(containerColor = next.background, contentColor = next.ink),
        elevation = CardDefaults.cardElevation(0.dp),
    ) {
        Column(Modifier.fillMaxWidth()) {
            if (showBoard || row.site.isNotBlank()) {
                Row(
                    modifier =
                        Modifier.fillMaxWidth().padding(
                            horizontal = if (tallMedia) 12.dp else inset,
                            vertical = if (tallMedia) 0.dp else 6.dp,
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    BoardIcon(
                        siteId = row.site,
                        boardId = row.board,
                        title = row.boardTitle,
                        modifier = Modifier.size(32.dp),
                    )
                    Column(Modifier.weight(1f)) {
                        Text(
                            row.sourceAndBoard,
                            style = OrbinType.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = next.ink,
                        )
                        Text(
                            if (row.threadNumber.isNotBlank()) {
                                stringResource(
                                    Res.string.reader_feed_post_metadata,
                                    row.poster,
                                    row.threadNumber,
                                    activityText(row),
                                )
                            } else {
                                stringResource(Res.string.reader_feed_poster_time, row.poster, activityText(row))
                            },
                            style = OrbinType.footnote.copy(fontSize = 11.sp, lineHeight = 14.sp),
                            color = next.muted,
                        )
                    }
                    if (tallMedia) {
                        NextIconAction(
                            ReaderIcons.More,
                            stringResource(Res.string.reader_feed_more, row.subject),
                            onClick = { optionsVisible = true },
                            tint = next.ink,
                        )
                    }
                }
            }
            // Text posts have no media frame. Players and file-specific controls remain in the
            // production thumbnail slot, where the actual attachment type is known.
            if (row.hasPreview) {
                val tile =
                    Modifier.fillMaxWidth().let {
                        if (uniform) it.aspectRatio(UNIFORM_TILE_ASPECT) else it.mediaTileSize(row, tallMedia)
                    }
                Box(tile) {
                    if (thumbnail != null) {
                        thumbnail(row, Modifier.matchParentSize())
                    } else {
                        MediaTile(modifier = Modifier.matchParentSize(), seed = seed, radius = 0.dp)
                    }
                    if (row.mediaLabel.isNotBlank()) {
                        Text(
                            row.mediaLabel,
                            style = OrbinType.caption1,
                            color = next.ink,
                            modifier =
                                Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(8.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(next.background.copy(alpha = 0.9f))
                                    .padding(horizontal = 6.dp, vertical = 3.dp),
                        )
                    }
                }
            }
            if (tallMedia) {
                FeedActionStrip(
                    row = row,
                    onReply = { onClick(row) },
                    onToggleWatch = onToggleWatch,
                    onToggleSaved = onToggleSaved,
                    onShareRow = onShareRow,
                )
            }
            Column(Modifier.padding(horizontal = if (tallMedia) 12.dp else inset, vertical = 4.dp)) {
                Text(
                    row.subject,
                    style = OrbinType.bodyMedium,
                    fontWeight = if (row.read) FontWeight.Medium else FontWeight.SemiBold,
                    color = if (row.read) next.muted else next.ink,
                    maxLines = if (tallMedia && !uniform) 3 else 2,
                    minLines = if (uniform) 2 else 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (row.excerpt.isNotBlank() || uniform) {
                    Gap(4)
                    Text(
                        row.excerpt,
                        style = OrbinType.bodyMedium,
                        color = next.ink,
                        maxLines = excerptLines,
                        minLines = if (uniform) excerptLines else 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Gap(8)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    MetaLine(rowCounts(row), maxLines = if (uniform) 1 else 2)
                    if (!showBoard && row.site.isBlank()) {
                        Spacer(Modifier.weight(1f))
                        MetaLine(activityText(row), color = next.faint)
                    }
                }
            }
            Gap(10)
        }
    }
    if (optionsVisible) {
        val actions =
            buildList {
                add(
                    NextSheetAction(
                        label = stringResource(Res.string.reader_feed_open_thread),
                        onClick = { onClick(row) },
                    ),
                )
                onToggleWatch?.let { action ->
                    add(
                        NextSheetAction(
                            label =
                                stringResource(
                                    if (row.watched) Res.string.reader_feed_unwatch else Res.string.reader_feed_watch,
                                    row.subject,
                                ),
                            onClick = { action(row) },
                        ),
                    )
                }
                onShareRow?.let { action ->
                    add(
                        NextSheetAction(
                            label = stringResource(Res.string.reader_feed_share, row.subject),
                            onClick = { action(row) },
                        ),
                    )
                }
                if (!row.watched) {
                    onToggleSaved?.let { action ->
                        add(
                            NextSheetAction(
                                label =
                                    stringResource(
                                        if (row.bookmarked) {
                                            Res.string.reader_feed_unsave
                                        } else {
                                            Res.string.reader_feed_save
                                        },
                                        row.subject,
                                    ),
                                onClick = { action(row) },
                            ),
                        )
                    }
                }
            }
        NextActionSheet(row.subject, actions, onDismiss = { optionsVisible = false })
    }
}

/** Keep an attachment whole, with a bounded frame for unusually tall or wide files. */
private fun Modifier.mediaTileSize(
    row: FeedRow,
    tallMedia: Boolean,
): Modifier =
    if (row.mediaAspectRatio > 0f) {
        aspectRatio(
            row.mediaAspectRatio.coerceIn(if (tallMedia) MIN_TALL_TILE_ASPECT else MIN_TILE_ASPECT, MAX_TILE_ASPECT),
        )
    } else {
        height(FALLBACK_TILE_HEIGHT)
    }

private const val MIN_TILE_ASPECT = 0.8f
private const val MIN_TALL_TILE_ASPECT = 0.5625f
private const val MAX_TILE_ASPECT = 3f
private const val UNIFORM_TILE_ASPECT = 4f / 3f
private val FALLBACK_TILE_HEIGHT = 240.dp

@Composable
private fun CollapsedFeedRow(
    row: FeedRow,
    modifier: Modifier = Modifier,
    showBoard: Boolean = true,
    onClick: (FeedRow) -> Unit,
) {
    Card(
        onClick = { onClick(row) },
        modifier = modifier.fillMaxWidth().semantics { role = Role.Button },
        shape = RoundedCornerShape(0.dp),
        colors = CardDefaults.cardColors(containerColor = next.background, contentColor = next.ink),
        elevation = CardDefaults.cardElevation(0.dp),
    ) {
        Column(Modifier.padding(horizontal = GUTTER, vertical = 12.dp)) {
            if (showBoard ||
                row.site.isNotBlank()
            ) {
                Text(row.sourceAndBoard, style = OrbinType.titleMedium, color = next.ink)
            }
            Text(
                row.subject,
                style = OrbinType.body,
                color = next.muted,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(stringResource(Res.string.reader_feed_muted), style = OrbinType.footnote, color = next.muted)
        }
    }
}

@Composable
internal fun rowCounts(row: FeedRow): String {
    val replies = pluralStringResource(Res.plurals.next_row_replies, row.replies, row.replies)
    val files = pluralStringResource(Res.plurals.next_row_files, row.media, row.media)
    return if (row.unread > 0) {
        stringResource(Res.string.next_row_counts_unread, replies, row.unread)
    } else {
        stringResource(Res.string.next_row_counts, replies, files)
    }
}
