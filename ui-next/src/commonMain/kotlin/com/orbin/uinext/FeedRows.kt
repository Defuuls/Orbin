package com.orbin.uinext

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.orbin.uinext.resources.Res
import com.orbin.uinext.resources.next_feed_title
import com.orbin.uinext.resources.next_row_counts
import com.orbin.uinext.resources.next_row_counts_unread
import com.orbin.uinext.resources.next_row_files
import com.orbin.uinext.resources.next_row_no_image
import com.orbin.uinext.resources.next_row_replies
import com.orbin.uinext.resources.next_search_threads_prompt
import com.orbin.uinext.resources.next_settings_title
import com.orbin.uinext.tokens.NextRadius
import com.orbin.uinext.tokens.NextSpace
import com.orbin.uinext.tokens.NextType
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

val InstagramStoryGradient =
    Brush.linearGradient(
        listOf(
            Color(0xFFFBAA47),
            Color(0xFFD91A46),
            Color(0xFFA60F93),
        ),
    )

@Composable
internal fun FeedHeader(
    subtitle: String,
    headerContent: @Composable () -> Unit = {},
    query: String = "",
    onQueryChange: (String) -> Unit = {},
    onSettings: (() -> Unit)? = null,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = NextSpace.titleTop),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ScreenTitle(
                text = stringResource(Res.string.next_feed_title),
                subtitle = subtitle,
                modifier = Modifier.weight(1f),
                inset = 0.dp,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                NextIconAction(
                    imageVector = Icons.Outlined.NotificationsNone,
                    contentDescription = "Notifications",
                    onClick = { /* activity feed */ },
                    tint = next.ink,
                )
                if (onSettings != null) {
                    NextIconAction(
                        imageVector = Icons.Outlined.Settings,
                        contentDescription = stringResource(Res.string.next_settings_title),
                        onClick = onSettings,
                        tint = next.ink,
                    )
                }
            }
        }
        InstagramStoriesTray()
        Gap(4)
        Column(Modifier.padding(horizontal = 0.dp)) {
            headerContent()
            SchematicSearch(query, onQueryChange, stringResource(Res.string.next_search_threads_prompt))
        }
        Gap(10)
        HorizontalDivider(thickness = 0.5.dp, color = next.hairline)
    }
}

@Composable
internal fun InstagramStoriesTray(
    modifier: Modifier = Modifier,
    onSelectBoard: ((String) -> Unit)? = null,
) {
    val boards =
        remember {
            listOf("/all/", "/g/", "/v/", "/a/", "/ck/", "/p/", "/mu/", "/fit/", "/wsg/")
        }
    LazyRow(
        modifier = modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(horizontal = 4.dp),
    ) {
        items(boards) { board ->
            InstagramStoryBubble(
                board = board,
                onClick = { onSelectBoard?.invoke(board) },
            )
        }
    }
}

@Composable
internal fun InstagramStoryBubble(
    board: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.clickable(onClick = onClick),
    ) {
        Box(
            modifier =
                Modifier
                    .size(60.dp)
                    .clip(CircleShape)
                    .border(2.dp, InstagramStoryGradient, CircleShape)
                    .padding(3.dp)
                    .clip(CircleShape)
                    .background(boardHue(board).copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = board.replace("/", "").take(3).uppercase(),
                style = NextType.labelMedium,
                fontWeight = FontWeight.Bold,
                color = next.ink,
            )
        }
        Gap(4)
        Text(
            text = board,
            style = NextType.labelSmall,
            color = next.muted,
            maxLines = 1,
        )
    }
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
) {
    if (row.muted) {
        CollapsedFeedRow(
            row = row,
            modifier = modifier.padding(if (tallMedia) GRID_CELL_PADDING else 4.dp),
            showBoard = showBoard,
            onClick = onClick,
        )
        return
    }
    Card(
        onClick = { onClick(row) },
        modifier =
            modifier
                .padding(if (tallMedia) GRID_CELL_PADDING else 4.dp)
                .semantics { role = Role.Button },
        shape = RoundedCornerShape(NextRadius.tight),
        border = BorderStroke(0.5.dp, next.hairline),
        colors =
            CardDefaults.cardColors(
                containerColor = next.background,
                contentColor = next.ink,
            ),
        elevation = CardDefaults.cardElevation(0.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            PostHeader(row, showBoard, activityText(row), onClick = { onClick(row) })
            PostMedia(row, seed, uniform, tallMedia, thumbnail)
            PostActionBar(row = row, seed = seed, onCommentClick = { onClick(row) })
            PostEngagement(
                row = row,
                seed = seed,
                showBoard = showBoard,
                uniform = uniform,
                excerptLines = excerptLines,
            )
            HorizontalDivider(thickness = 0.5.dp, color = next.hairline)
        }
    }
}

@Composable
private fun PostHeader(
    row: FeedRow,
    showBoard: Boolean,
    activity: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .border(1.5.dp, InstagramStoryGradient, CircleShape)
                    .padding(2.5.dp)
                    .clip(CircleShape)
                    .background(boardHue(row.board)),
            contentAlignment = Alignment.Center,
        ) {
            if (showBoard) {
                val initials = row.board.filter { it != '/' }.take(2).uppercase()
                Text(
                    text = initials,
                    style = NextType.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
            } else {
                OrbitEmblem(size = 18.dp, tint = Color.White)
            }
        }
        WidthSpacer(10)
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (showBoard) {
                    Text(
                        text = row.board,
                        style = NextType.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = next.ink,
                    )
                    WidthSpacer(6)
                    Text(text = "•", style = NextType.labelSmall, color = next.muted)
                    WidthSpacer(6)
                }
                Text(
                    text = activity,
                    style = NextType.labelSmall,
                    color = next.muted,
                )
            }
        }
        NextIconAction(
            imageVector = Icons.Outlined.MoreHoriz,
            contentDescription = "More",
            onClick = onClick,
            tint = next.ink,
        )
    }
}

@Composable
private fun PostMedia(
    row: FeedRow,
    seed: Int,
    uniform: Boolean,
    tallMedia: Boolean,
    thumbnail: (@Composable (FeedRow, Modifier) -> Unit)?,
) {
    val tile =
        Modifier.fillMaxWidth().let {
            if (uniform) it.aspectRatio(UNIFORM_TILE_ASPECT) else it.mediaTileSize(row, tallMedia)
        }
    Box(modifier = tile) {
        if (row.hasPreview && thumbnail != null) {
            thumbnail(row, Modifier.matchParentSize())
        } else if (row.hasPreview) {
            MediaTile(modifier = Modifier.matchParentSize(), seed = seed, radius = NextRadius.tight)
        } else {
            Box(
                modifier =
                    Modifier
                        .matchParentSize()
                        .background(next.ink.copy(alpha = 0.04f)),
                contentAlignment = Alignment.Center,
            ) {
                MetaLine(stringResource(Res.string.next_row_no_image), color = next.faint)
            }
        }
    }
}

@Composable
private fun PostActionBar(
    row: FeedRow,
    seed: Int,
    onCommentClick: () -> Unit,
) {
    var isLiked by rememberSaveable(row.id) { mutableStateOf(false) }
    var isBookmarked by rememberSaveable(row.id) { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NextIconAction(
            imageVector = if (isLiked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
            contentDescription = if (isLiked) "Unlike" else "Like",
            onClick = { isLiked = !isLiked },
            tint = if (isLiked) Color(0xFFED4956) else next.ink,
        )
        WidthSpacer(8)
        NextIconAction(
            imageVector = Icons.Outlined.ChatBubbleOutline,
            contentDescription = "Comment",
            onClick = onCommentClick,
            tint = next.ink,
        )
        WidthSpacer(8)
        NextIconAction(
            imageVector = Icons.AutoMirrored.Outlined.Send,
            contentDescription = "Share",
            onClick = onCommentClick,
            tint = next.ink,
        )
        Spacer(Modifier.weight(1f))
        NextIconAction(
            imageVector = if (isBookmarked) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
            contentDescription = if (isBookmarked) "Saved" else "Save",
            onClick = { isBookmarked = !isBookmarked },
            tint = next.ink,
        )
    }
}

@Composable
private fun PostEngagement(
    row: FeedRow,
    seed: Int,
    showBoard: Boolean,
    uniform: Boolean,
    excerptLines: Int,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 12.dp, bottom = 10.dp),
    ) {
        val likeCount = row.replies * 3 + seed * 7 + 14
        Text(
            text = "$likeCount likes",
            style = NextType.labelMedium,
            fontWeight = FontWeight.Bold,
            color = next.ink,
        )
        Gap(4)
        Text(
            text =
                buildAnnotatedString {
                    if (showBoard) {
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = next.ink)) {
                            append("${row.board} ")
                        }
                    }
                    withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = next.ink)) {
                        append(row.subject)
                    }
                    if (row.excerpt.isNotBlank()) {
                        append(" ")
                        withStyle(SpanStyle(color = next.ink.copy(alpha = 0.85f))) {
                            append(row.excerpt)
                        }
                    }
                },
            style = NextType.bodySmall,
            maxLines = if (uniform) 2 else excerptLines,
            overflow = TextOverflow.Ellipsis,
        )
        Gap(4)
        Text(
            text = "View all ${row.replies} comments",
            style = NextType.labelSmall,
            color = next.muted,
        )
        Gap(2)
        Text(
            text = rowCounts(row),
            style = NextType.labelSmall,
            color = next.faint,
        )
    }
}

/**
 * Sizes a grid tile to its media's own aspect ratio, so the thumbnail can be drawn whole instead of
 * centre-cropped into a fixed-height box.
 */
private fun Modifier.mediaTileSize(
    row: FeedRow,
    tallMedia: Boolean,
): Modifier =
    if (row.hasPreview && row.mediaAspectRatio > 0f) {
        val minAspect = if (tallMedia) MIN_TALL_TILE_ASPECT else MIN_TILE_ASPECT
        aspectRatio(row.mediaAspectRatio.coerceIn(minAspect, MAX_TILE_ASPECT))
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
        shape = RoundedCornerShape(NextRadius.control),
        border = BorderStroke(1.dp, next.hairline),
        colors =
            CardDefaults.cardColors(
                containerColor = next.background,
                contentColor = next.ink,
            ),
        elevation = CardDefaults.cardElevation(0.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(next.muted),
            )
            Text(
                text = "Hidden Post",
                style = NextType.labelSmall,
                color = next.muted,
            )
            if (showBoard) {
                Text(
                    text = row.board,
                    style = NextType.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = next.muted,
                )
            }
            Text(
                text = row.subject,
                modifier = Modifier.weight(1f),
                style = NextType.bodySmall,
                fontWeight = FontWeight.Normal,
                color = next.muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
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
