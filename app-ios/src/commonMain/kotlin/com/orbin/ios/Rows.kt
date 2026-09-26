package com.orbin.ios

import com.orbin.core.model.Bookmark
import com.orbin.core.model.CatalogThread
import com.orbin.core.model.HistoryEntry
import com.orbin.core.model.MediaAttachment
import com.orbin.core.model.Thread
import com.orbin.core.model.toSearchResult
import com.orbin.core.ui.date.formatRelativeTime
import com.orbin.provider.api.UriParts
import com.orbin.uinext.BoardTile
import com.orbin.uinext.FeedRow
import com.orbin.uinext.SearchRow
import com.orbin.uinext.Post as NextPost

// Plain-value adapters from the domain model to the ui-next rows, as the Android `Next*Screen`
// adapters do. Kept free of Compose so they are unit-tested on both platforms.

internal fun SiteBoard.toTile(followed: Boolean = false): BoardTile =
    BoardTile(
        id = tileId,
        path = "/${board.id.value}/",
        // Two sites can both have a /b/, so every title says which site it is on.
        title = "${board.title} · $siteName",
        nsfw = board.isNsfw,
        followed = followed,
    )

/**
 * A feed row, laid out as Android's feed lays it out: "/g/" as the board, so the screen groups
 * by it. The id carries the site too, since both sites can have a /g/ with the same thread number.
 */
internal fun FeedThread.toFeedRow(
    nowMillis: Long,
    read: Boolean,
): FeedRow =
    thread.toRow(nowMillis, read).copy(
        id = feedRowId,
        board = "/${thread.key.board.value}/",
        threadNumber =
            thread.key.thread.value
                .toString(),
    )

internal val FeedThread.feedRowId: String
    get() = "${provider.value}/${thread.key.board.value}/${thread.key.thread.value}"

/**
 * A search hit, titled as Android titles it (`toSearchResult` in `:core:model`), with the opening
 * post as plain text rather than the engine's markup.
 */
internal fun FeedThread.toSearchRow(): SearchRow {
    val result = thread.toSearchResult()
    return SearchRow(
        id = feedRowId,
        title = result.title,
        board = "/${thread.key.board.value}/",
        snippet =
            thread.originalPost.comment
                .plainText()
                .take(SEARCH_SNIPPET_LENGTH),
    )
}

/** As long as Android's search snippet. */
private const val SEARCH_SNIPPET_LENGTH = 160

internal val SiteBoard.tileId: String get() = "${provider.value}/${board.id.value}"

internal fun CatalogThread.toRow(
    nowMillis: Long,
    read: Boolean = false,
    unread: Int = 0,
): FeedRow =
    FeedRow(
        id = "${key.board.value}/${key.thread.value}",
        subject = originalPost.subject?.takeIf { it.isNotBlank() } ?: "No.${key.thread.value}",
        board = key.board.value,
        activity =
            formatRelativeTime(
                if (stats.lastModifiedMillis > 0L) stats.lastModifiedMillis else originalPost.createdAtMillis,
                nowMillis,
            ).orEmpty(),
        replies = stats.replyCount,
        media = stats.imageCount,
        hasPreview = originalPost.attachments.isNotEmpty(),
        excerpt = originalPost.comment.plainText(),
        mediaAspectRatio = originalPost.attachments.firstOrNull()?.previewAspectRatio ?: 0f,
        read = read,
        unread = unread,
    )

/** What a thread's bookmark stores, the same fields Android's thread screen writes. */
internal fun Thread.toBookmark(nowMillis: Long): Bookmark =
    Bookmark(
        key = key,
        title = displayTitle,
        thumbnailUrl = originalPost.attachments.firstOrNull()?.thumbnailUrl,
        createdAtMillis = nowMillis,
        isWatched = true,
        lastSeenReplyCount = stats.replyCount,
        latestReplyCount = stats.replyCount,
    )

/** The reading-history entry for opening a thread, as Android records it. */
internal fun Thread.toHistoryEntry(nowMillis: Long): HistoryEntry =
    HistoryEntry(
        key = key,
        title = displayTitle,
        thumbnailUrl = originalPost.attachments.firstOrNull()?.thumbnailUrl,
        lastVisitedMillis = nowMillis,
        lastReadPostId = originalPost.id,
    )

private val Thread.displayTitle: String get() = subject?.takeIf { it.isNotBlank() } ?: "/${key.board.value}/"

internal fun Thread.toPosts(nowMillis: Long): List<NextPost> =
    allPosts.map { post ->
        NextPost(
            id = post.id.value.toString(),
            number = "No.${post.id.value}",
            time = formatRelativeTime(post.createdAtMillis, nowMillis).orEmpty(),
            body = post.comment.plainText(),
            hasMedia = post.attachments.isNotEmpty(),
            replies = post.backlinks.size,
            // A spoilered file is covered where it is drawn; `spoiler` here would hide the post's
            // text instead, which is not what a spoilered file means.
        )
    }

/** Every file in the thread, in reading order: what the viewer pages through. */
internal val Thread.files: List<MediaAttachment> get() = allPosts.flatMap { it.attachments }

/** Where the post with [postId]'s first file sits in [files], or null when it has none. */
internal fun Thread.firstFileIndex(postId: String): Int? {
    var index = 0
    for (post in allPosts) {
        if (post.id.value.toString() == postId) return index.takeIf { post.attachments.isNotEmpty() }
        index += post.attachments.size
    }
    return null
}

/**
 * [url] if it may be handed to the browser, else null: https with a host, nothing else. The same
 * rule as Android's `SafeExternalLinks.sanitizeHttps`, decided by the same URI grammar ([UriParts]
 * is checked against `java.net.URI`), so a post link cannot open anything on iOS that it could not
 * open on Android.
 */
internal fun safeExternalLink(url: String): String? {
    val trimmed = url.trim()
    if (!trimmed.startsWith("https://", ignoreCase = true)) return null
    val parts = UriParts.parse(trimmed) ?: return null
    return trimmed.takeIf { parts.scheme.equals("https", ignoreCase = true) && !parts.host.isNullOrBlank() }
}
