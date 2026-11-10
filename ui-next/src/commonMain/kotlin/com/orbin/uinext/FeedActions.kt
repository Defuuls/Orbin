package com.orbin.uinext

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.orbin.uinext.resources.Res
import com.orbin.uinext.resources.social_feed_save
import com.orbin.uinext.resources.social_feed_saved_while_watching
import com.orbin.uinext.resources.social_feed_share
import com.orbin.uinext.resources.social_feed_unsave
import com.orbin.uinext.resources.social_feed_unwatch
import com.orbin.uinext.resources.social_feed_view_replies
import com.orbin.uinext.resources.social_feed_watch
import org.jetbrains.compose.resources.stringResource

/** Actions are independent touch targets; providers remain read-only when a thread is watched. */
@Composable
internal fun FeedActionStrip(
    row: FeedRow,
    onReply: () -> Unit,
    onToggleWatch: ((FeedRow) -> Unit)?,
    onToggleSaved: ((FeedRow) -> Unit)?,
    onShareRow: ((FeedRow) -> Unit)?,
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onToggleWatch != null) {
            NextIconAction(
                imageVector = if (row.watched) Icons.Filled.NotificationsActive else Icons.Outlined.NotificationsNone,
                contentDescription =
                    stringResource(
                        if (row.watched) Res.string.social_feed_unwatch else Res.string.social_feed_watch,
                        row.subject,
                    ),
                onClick = { onToggleWatch(row) },
                tint = next.ink,
                modifier = Modifier.semantics { selected = row.watched },
            )
        }
        NextIconAction(
            Icons.Outlined.ChatBubbleOutline,
            stringResource(Res.string.social_feed_view_replies, row.subject),
            onReply,
            tint = next.ink,
        )
        if (onShareRow != null) {
            NextIconAction(
                Icons.AutoMirrored.Outlined.Send,
                stringResource(Res.string.social_feed_share, row.subject),
                { onShareRow(row) },
                tint = next.ink,
            )
        }
        Spacer(Modifier.weight(1f))
        if (onToggleSaved != null && row.watched) {
            Box(
                modifier =
                    Modifier.size(48.dp).semantics(mergeDescendants = true) {
                        selected = true
                        disabled()
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.Bookmark,
                    stringResource(Res.string.social_feed_saved_while_watching, row.subject),
                    tint = next.ink,
                )
            }
        } else if (onToggleSaved != null) {
            NextIconAction(
                imageVector = if (row.bookmarked) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                contentDescription =
                    stringResource(
                        if (row.bookmarked) Res.string.social_feed_unsave else Res.string.social_feed_save,
                        row.subject,
                    ),
                onClick = { onToggleSaved(row) },
                tint = next.ink,
                modifier = Modifier.semantics { selected = row.bookmarked },
            )
        }
    }
}
