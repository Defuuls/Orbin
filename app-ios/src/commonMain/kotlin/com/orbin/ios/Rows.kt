package com.orbin.ios

import com.orbin.core.model.Bookmark
import com.orbin.core.model.CatalogThread
import com.orbin.core.model.HistoryEntry
import com.orbin.core.model.MediaAttachment
import com.orbin.core.model.MediaType
import com.orbin.core.model.Thread
import com.orbin.core.model.toSearchResult
import com.orbin.core.reader.catalogRow
import com.orbin.core.reader.firstMediaIndex
import com.orbin.core.reader.mediaPostId
import com.orbin.core.reader.preparePresentation
import com.orbin.core.reader.threadFiles
import com.orbin.uinext.BoardTile
import com.orbin.uinext.FeedRow
import com.orbin.uinext.SearchRow
import kotlinx.collections.immutable.toImmutableList
import com.orbin.core.reader.safeExternalLink as sharedSafeExternalLink
import com.orbin.uinext.Post as NextPost

// Plain-value adapters from the domain model to the ui-next rows, as the Android `Next*Screen`
// adapters do. Kept free of Compose so they are unit-tested on both platforms.

/**
 * The image a feed or catalog card shows: the full picture for a still image, which a card is
 * too big for a thumbnail to fill sharply; the thumbnail for anything else (a GIF can run to many
 * megabytes, and a video has no picture of its own).
 */
internal fun MediaAttachment.cardImageUrl(): String =
    if (type == MediaType.IMAGE && sourceUrl.isNotBlank()) sourceUrl else thumbnailUrl

internal fun SiteBoard.toTile(followed: Boolean = false): BoardTile =
    BoardTile(
        id = tileId,
        path = "/${board.id.value}/",
        // The source selector establishes context; the board keeps its full title.
        title = board.title,
        nsfw = board.isNsfw,
        followed = followed,
        site = provider.value,
    )

/**
 * Every feed row names its site and board. Its stable key includes the source because two sites
 * can have the same board code and thread number.
 */
internal fun FeedThread.toFeedRow(
    nowMillis: Long,
    read: Boolean,
): FeedRow =
    thread.toRow(nowMillis, read).copy(
        id = feedRowId,
        site = sourceLabel(provider.value),
        board = "/${thread.key.board.value}/",
        threadNumber =
            thread.key.thread.value
                .toString(),
    )

internal val FeedThread.feedRowId: String
    get() = "${provider.value}/${thread.key.board.value}/${thread.key.thread.value}"

/** A catalog can attach an image before a clip; the video feed always previews an actual video. */
internal fun FeedThread.videoFeedPreview(): FeedThread? {
    val clips = thread.originalPost.attachments.filter { it.type == MediaType.VIDEO && it.sourceUrl.isNotBlank() }
    if (clips.isEmpty()) return null
    return copy(thread = thread.copy(originalPost = thread.originalPost.copy(attachments = clips.toImmutableList())))
}

/**
 * A search hit, titled as Android titles it (`toSearchResult` in `:core:model`), with the opening
 * post as plain text rather than the engine's markup.
 */
internal fun FeedThread.toSearchRow(): SearchRow {
    val result = thread.toSearchResult()
    return SearchRow(
        id = feedRowId,
        title = result.title,
        board = "${sourceLabel(provider.value)} · /${thread.key.board.value}/",
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
): FeedRow = catalogRow(nowMillis).copy(read = read, unread = unread)

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

/** Compatibility adapter; both hosts use the same prepared presentation mapper. */
internal suspend fun Thread.toPosts(nowMillis: Long): List<NextPost> = preparePresentation(nowMillis).rowModels

/** Every file in the thread, in reading order: what the viewer pages through. */
internal val Thread.files: List<MediaAttachment> get() = threadFiles

/** The owning post of a viewer page, counting every attachment and skipping text-only posts. */
internal fun Thread.postIdForFileIndex(index: Int): String? = mediaPostId(index)?.value?.toString()

/** Where the post with [postId]'s first file sits in [files], or null when it has none. */
internal fun Thread.firstFileIndex(postId: String): Int? = firstMediaIndex(postId)

/** The shared HTTPS-only rule is used by both platform readers. */
internal fun safeExternalLink(url: String): String? = sharedSafeExternalLink(url)

private fun sourceLabel(provider: String): String =
    when (provider) {
        "fourchan" -> "4chan"
        "bbwchan" -> "BBWChan"
        else -> provider
    }
