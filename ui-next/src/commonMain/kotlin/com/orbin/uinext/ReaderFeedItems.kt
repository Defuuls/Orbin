package com.orbin.uinext

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import com.orbin.uinext.resources.Res
import com.orbin.uinext.resources.reader_feed_open_media
import com.orbin.uinext.resources.reader_reveal_spoiler
import com.orbin.uinext.resources.social_feed_more
import com.orbin.uinext.resources.social_feed_post_metadata
import com.orbin.uinext.resources.social_feed_save
import com.orbin.uinext.resources.social_feed_saved_while_watching
import com.orbin.uinext.resources.social_feed_share
import com.orbin.uinext.resources.social_feed_unsave
import com.orbin.uinext.resources.social_feed_unwatch
import com.orbin.uinext.resources.social_feed_view_replies
import com.orbin.uinext.resources.social_feed_view_videos
import com.orbin.uinext.resources.social_feed_watch
import com.orbin.uinext.resources.tiktok_feed_open_board
import com.orbin.uinext.tokens.ReaderType
import org.jetbrains.compose.resources.stringResource

@Immutable
internal class ReaderFeedRowActions(
    val onOpenRow: (FeedRow) -> Unit,
    val onOpenMedia: ((FeedRow) -> Unit)?,
    val onOpenReplies: ((FeedRow) -> Unit)?,
    val repliesOpenComments: Boolean,
    val onOpenBoard: ((FeedRow) -> Unit)?,
    val onToggleWatch: ((FeedRow) -> Unit)?,
    val onToggleSaved: ((FeedRow) -> Unit)?,
    val onShare: ((FeedRow) -> Unit)?,
)

/** Home shares the catalog cells; only cross-board metadata and optional row actions are added. */
@Composable
internal fun ReaderFeedItem(
    row: FeedRow,
    mode: CatalogMode,
    wideWindow: Boolean,
    thumbnail: (@Composable (FeedRow, Modifier) -> Unit)?,
    activity: String,
    actions: ReaderFeedRowActions,
) {
    var revealed by rememberSaveable(row.id) { mutableStateOf(false) }
    val covered = row.imageSpoiler && !revealed && row.hasPreview && !row.muted
    val mediaLabel = stringResource(Res.string.reader_feed_open_media, row.subject)
    val revealLabel = stringResource(Res.string.reader_reveal_spoiler)
    val menu = readerFeedRowMenu(row, actions, covered) { revealed = true }
    var expanded by rememberSaveable(row.id, menu.isNotEmpty()) { mutableStateOf(false) }
    val optionsLabel = stringResource(Res.string.social_feed_more, row.subject)
    val openOptions: (() -> Unit)? = if (menu.isEmpty()) null else ({ expanded = true })
    val customActions =
        menu.filter { it.enabled && it.label != mediaLabel && it.label != revealLabel }.map { item ->
            CustomAccessibilityAction(item.label) {
                item.onClick()
                true
            }
        }
    val metadata =
        if (row.threadNumber.isNotEmpty()) {
            stringResource(Res.string.social_feed_post_metadata, row.sourceAndBoard, row.threadNumber, activity)
        } else {
            listOf(row.sourceAndBoard, activity).filter(String::isNotBlank).joinToString(" · ")
        }
    val mediaAction = actions.onOpenMedia?.takeIf { row.hasPreview && !row.muted }
    if (mode == CatalogMode.GRID) {
        Box(Modifier.fillMaxWidth()) {
            CatalogCard(
                row = row,
                selected = false,
                onOpen = actions.onOpenRow,
                thumbnail = thumbnail,
                itemTag = ReaderFeedTags.row(row.id),
                thumbnailTag = ReaderFeedTags.thumbnail(row.id),
                metadata = metadata,
                onOpenThumbnail = mediaAction,
                thumbnailClickLabel = mediaLabel,
                spoilerRevealed = revealed,
                onRevealSpoiler = { revealed = true },
                accessibilityActions = customActions,
                wideWindow = wideWindow,
                onLongClick = openOptions,
                onLongClickLabel = optionsLabel,
            )
            ReaderFeedMenu(row, menu, expanded, onDismiss = { expanded = false })
        }
    } else {
        Column(Modifier.fillMaxWidth().background(reader.background)) {
            Box(Modifier.fillMaxWidth()) {
                CatalogRow(
                    row = row,
                    selected = false,
                    wideWindow = wideWindow,
                    onOpen = actions.onOpenRow,
                    thumbnail = thumbnail,
                    itemTag = ReaderFeedTags.row(row.id),
                    thumbnailTag = ReaderFeedTags.thumbnail(row.id),
                    metadata = metadata,
                    onOpenThumbnail = mediaAction,
                    thumbnailClickLabel = mediaLabel,
                    spoilerRevealed = revealed,
                    onRevealSpoiler = { revealed = true },
                    accessibilityActions = customActions,
                    showDivider = false,
                    onLongClick = openOptions,
                    onLongClickLabel = optionsLabel,
                )
                ReaderFeedMenu(row, menu, expanded, onDismiss = { expanded = false })
            }
            ReaderHairline()
        }
    }
}

@Composable
private fun readerFeedRowMenu(
    row: FeedRow,
    actions: ReaderFeedRowActions,
    covered: Boolean,
    onReveal: () -> Unit,
): List<ReaderMenuItem> =
    buildList {
        val media = actions.onOpenMedia?.takeIf { row.hasPreview && !row.muted }
        val videoReplies = actions.onOpenReplies != null && !actions.repliesOpenComments
        val opensMedia = media != null || videoReplies
        if (covered && opensMedia) {
            add(ReaderMenuItem(stringResource(Res.string.reader_reveal_spoiler), onClick = onReveal))
        } else if (media != null) {
            add(ReaderMenuItem(stringResource(Res.string.reader_feed_open_media, row.subject)) { media(row) })
        }
        actions.onOpenReplies?.takeIf { actions.repliesOpenComments || !covered }?.let { open ->
            val label =
                if (actions.repliesOpenComments) {
                    Res.string.social_feed_view_replies
                } else {
                    Res.string.social_feed_view_videos
                }
            add(ReaderMenuItem(stringResource(label, row.subject)) { open(row) })
        }
        actions.onOpenBoard?.let { open ->
            add(ReaderMenuItem(stringResource(Res.string.tiktok_feed_open_board, row.board)) { open(row) })
        }
        actions.onToggleWatch?.let { toggle ->
            val label = if (row.watched) Res.string.social_feed_unwatch else Res.string.social_feed_watch
            add(ReaderMenuItem(stringResource(label, row.subject)) { toggle(row) })
        }
        actions.onToggleSaved?.let { toggle ->
            val lockedSaved = row.bookmarked && row.watched
            val label =
                when {
                    lockedSaved -> Res.string.social_feed_saved_while_watching
                    row.bookmarked -> Res.string.social_feed_unsave
                    else -> Res.string.social_feed_save
                }
            add(ReaderMenuItem(stringResource(label, row.subject), enabled = !lockedSaved) { toggle(row) })
        }
        actions.onShare?.let { share ->
            add(ReaderMenuItem(stringResource(Res.string.social_feed_share, row.subject)) { share(row) })
        }
    }

@Composable
private fun ReaderFeedMenu(
    row: FeedRow,
    items: List<ReaderMenuItem>,
    expanded: Boolean,
    onDismiss: () -> Unit,
) {
    val watchingLabel = stringResource(Res.string.social_feed_unwatch, row.subject)
    val savedLabel = stringResource(Res.string.social_feed_unsave, row.subject)
    val lockedSavedLabel = stringResource(Res.string.social_feed_saved_while_watching, row.subject)
    DropdownMenu(
        expanded = expanded && items.isNotEmpty(),
        onDismissRequest = onDismiss,
        containerColor = reader.background,
    ) {
        items.forEach { item ->
            val savedSelected = row.bookmarked && (item.label == savedLabel || item.label == lockedSavedLabel)
            val selected = row.watched && item.label == watchingLabel || savedSelected
            DropdownMenuItem(
                modifier = Modifier.semantics { this.selected = selected },
                text = {
                    Text(
                        item.label,
                        style = ReaderType.stateBody.withReaderFont(),
                        color =
                            when {
                                !item.enabled -> reader.inkMuted
                                selected -> reader.accent
                                else -> reader.ink
                            },
                    )
                },
                enabled = item.enabled,
                onClick = {
                    onDismiss()
                    item.onClick()
                },
            )
        }
    }
}
