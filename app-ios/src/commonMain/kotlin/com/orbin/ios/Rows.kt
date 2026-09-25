package com.orbin.ios

import com.orbin.core.model.CatalogThread
import com.orbin.core.model.PostComment
import com.orbin.core.model.PostNode
import com.orbin.core.model.Thread
import com.orbin.uinext.BoardTile
import com.orbin.uinext.FeedRow
import com.orbin.uinext.Post as NextPost

// Plain-value adapters from the domain model to the ui-next rows, as the Android `Next*Screen`
// adapters do. Kept free of Compose so they are unit-tested on both platforms.

internal fun SiteBoard.toTile(): BoardTile =
    BoardTile(
        id = tileId,
        path = "/${board.id.value}/",
        // Two sites can both have a /b/, so every title says which site it is on.
        title = "${board.title} · $siteName",
        nsfw = board.isNsfw,
    )

internal val SiteBoard.tileId: String get() = "${provider.value}/${board.id.value}"

internal fun CatalogThread.toRow(nowMillis: Long): FeedRow =
    FeedRow(
        id = "${key.board.value}/${key.thread.value}",
        subject = originalPost.subject?.takeIf { it.isNotBlank() } ?: "No.${key.thread.value}",
        board = key.board.value,
        activity =
            relativeTime(
                if (stats.lastModifiedMillis > 0L) stats.lastModifiedMillis else originalPost.createdAtMillis,
                nowMillis,
            ),
        replies = stats.replyCount,
        media = stats.imageCount,
        hasPreview = originalPost.attachments.isNotEmpty(),
        excerpt = originalPost.comment.plainText(),
        mediaAspectRatio = originalPost.attachments.firstOrNull()?.previewAspectRatio ?: 0f,
    )

internal fun Thread.toPosts(nowMillis: Long): List<NextPost> =
    allPosts.map { post ->
        NextPost(
            id = post.id.value.toString(),
            number = "No.${post.id.value}",
            time = relativeTime(post.createdAtMillis, nowMillis),
            body = post.comment.plainText(),
            hasMedia = post.attachments.isNotEmpty(),
            replies = post.backlinks.size,
            spoiler = post.attachments.any { it.isSpoiler },
        )
    }

/**
 * The comment as readable text: quotes as `>>123`, links as their text, line breaks kept. Styling
 * (greentext, spoilers, bold) is the rich renderer's job, which iOS does not have yet.
 */
internal fun PostComment.plainText(): String = buildString { appendNodes(nodes) }.trim()

private fun StringBuilder.appendNodes(nodes: List<PostNode>) {
    nodes.forEach { node ->
        when (node) {
            is PostNode.Text -> append(node.text)
            PostNode.LineBreak -> append('\n')
            is PostNode.Styled -> appendNodes(node.children)
            is PostNode.Link -> if (node.children.isEmpty()) append(node.url) else appendNodes(node.children)
            is PostNode.QuoteLink -> {
                append(">>")
                node.board?.let { append(">/").append(it.value).append('/') }
                append(node.target.value)
            }
        }
    }
}

private const val MINUTE_MILLIS = 60_000L
private const val HOUR_MILLIS = 60 * MINUTE_MILLIS
private const val DAY_MILLIS = 24 * HOUR_MILLIS
private const val WEEK_MILLIS = 7 * DAY_MILLIS
private const val YEAR_MILLIS = 365 * DAY_MILLIS

/**
 * "just now", "5m", "3h", "2d", "4w", "1y" — the Android app's short relative times, with weeks and
 * years in place of its locale-formatted dates. Empty when the time is unknown or in the future.
 */
internal fun relativeTime(
    epochMillis: Long,
    nowMillis: Long,
): String {
    val delta = nowMillis - epochMillis
    return when {
        epochMillis <= 0L || delta < 0L -> ""
        delta < MINUTE_MILLIS -> "just now"
        delta < HOUR_MILLIS -> "${delta / MINUTE_MILLIS}m"
        delta < DAY_MILLIS -> "${delta / HOUR_MILLIS}h"
        delta < WEEK_MILLIS -> "${delta / DAY_MILLIS}d"
        delta < YEAR_MILLIS -> "${delta / WEEK_MILLIS}w"
        else -> "${delta / YEAR_MILLIS}y"
    }
}
