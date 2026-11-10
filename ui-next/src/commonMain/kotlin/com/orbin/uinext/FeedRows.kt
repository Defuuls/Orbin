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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.orbin.uinext.resources.Res
import com.orbin.uinext.resources.next_row_counts
import com.orbin.uinext.resources.next_row_counts_unread
import com.orbin.uinext.resources.next_row_files
import com.orbin.uinext.resources.next_row_replies
import com.orbin.uinext.resources.next_search_threads_prompt
import com.orbin.uinext.resources.next_search_title
import com.orbin.uinext.resources.next_settings_title
import com.orbin.uinext.resources.social_feed_home
import com.orbin.uinext.resources.social_feed_muted
import com.orbin.uinext.resources.social_feed_post_metadata
import com.orbin.uinext.resources.social_feed_poster_time
import com.orbin.uinext.tokens.NextRadius
import com.orbin.uinext.tokens.NextType
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
) {
    var searchVisible by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.padding(horizontal = GUTTER)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(Res.string.social_feed_home),
                style = NextType.titleLarge,
                color = next.ink,
                modifier = Modifier.weight(1f).testTag(NextTitleTags.LARGE).semantics { heading() },
            )
            NextIconAction(
                imageVector = Icons.Outlined.Search,
                contentDescription = stringResource(Res.string.next_search_title),
                onClick = { if (onSearch != null) onSearch() else searchVisible = !searchVisible },
                tint = next.ink,
            )
            if (onSettings != null) {
                NextIconAction(
                    imageVector = Icons.Outlined.Menu,
                    contentDescription = stringResource(Res.string.next_settings_title),
                    onClick = onSettings,
                    tint = next.ink,
                )
            }
        }
        headerContent()
        if (searchVisible || query.isNotEmpty()) {
            Gap(8)
            SchematicSearch(query, onQueryChange, stringResource(Res.string.next_search_threads_prompt))
        }
        if (subtitle.isNotBlank()) {
            Text(
                subtitle,
                style = NextType.footnote,
                color = next.muted,
                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
            )
        }
        Gap(8)
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
    val inset = if (tallMedia) GRID_TEXT_INSET else 8.dp
    Card(
        onClick = { onClick(row) },
        modifier = modifier.padding(if (tallMedia) 0.dp else 4.dp).semantics { role = Role.Button },
        shape = RoundedCornerShape(if (tallMedia) 0.dp else NextRadius.card),
        border = if (tallMedia) null else BorderStroke(1.dp, next.hairline),
        colors = CardDefaults.cardColors(containerColor = next.background, contentColor = next.ink),
        elevation = CardDefaults.cardElevation(0.dp),
    ) {
        Column(Modifier.fillMaxWidth()) {
            if (showBoard || row.site.isNotBlank()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = inset, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Box(
                        Modifier.size(32.dp).clip(CircleShape).background(next.raised),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(row.board.trim('/').take(2), style = NextType.labelLarge, color = next.ink)
                    }
                    Column(Modifier.weight(1f)) {
                        Text(
                            row.sourceAndBoard,
                            style = NextType.titleMedium,
                            color = next.ink,
                        )
                        Text(
                            if (row.threadNumber.isNotBlank()) {
                                stringResource(
                                    Res.string.social_feed_post_metadata,
                                    row.poster,
                                    row.threadNumber,
                                    activityText(row),
                                )
                            } else {
                                stringResource(Res.string.social_feed_poster_time, row.poster, activityText(row))
                            },
                            style = NextType.footnote,
                            color = next.muted,
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
                            style = NextType.caption1,
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
            Column(Modifier.padding(horizontal = inset, vertical = 8.dp)) {
                Text(
                    row.subject,
                    style = NextType.titleMedium,
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
                        style = NextType.body,
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
            Hairline()
        }
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
                Text(row.sourceAndBoard, style = NextType.titleMedium, color = next.ink)
            }
            Text(row.subject, style = NextType.body, color = next.muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(stringResource(Res.string.social_feed_muted), style = NextType.footnote, color = next.muted)
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
