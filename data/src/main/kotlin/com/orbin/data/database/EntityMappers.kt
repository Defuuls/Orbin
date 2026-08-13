package com.orbin.data.database

import com.orbin.core.model.Board
import com.orbin.core.model.BoardId
import com.orbin.core.model.Bookmark
import com.orbin.core.model.HistoryEntry
import com.orbin.core.model.MediaAttachment
import com.orbin.core.model.MediaType
import com.orbin.core.model.Post
import com.orbin.core.model.PostComment
import com.orbin.core.model.PostId
import com.orbin.core.model.PostNode
import com.orbin.core.model.PosterInfo
import com.orbin.core.model.ProviderId
import com.orbin.core.model.Thread
import com.orbin.core.model.ThreadId
import com.orbin.core.model.ThreadKey
import com.orbin.core.model.ThreadStats
import com.orbin.data.database.entity.BoardEntity
import com.orbin.data.database.entity.BookmarkEntity
import com.orbin.data.database.entity.HistoryEntity
import com.orbin.data.database.entity.SavedPostEntity
import com.orbin.data.database.entity.SavedThreadEntity
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

/** Mappers between Room entities and domain models. The (provider, board, thread) columns map to [ThreadKey]. */

internal fun BookmarkEntity.toDomain(): Bookmark =
    Bookmark(
        key = ThreadKey(ProviderId(provider), BoardId(board), ThreadId(thread)),
        title = title,
        thumbnailUrl = thumbnailUrl,
        createdAtMillis = createdAtMillis,
        isWatched = isWatched,
        lastSeenReplyCount = lastSeenReplyCount,
        latestReplyCount = latestReplyCount,
        isThreadDead = isThreadDead,
    )

internal fun Bookmark.toEntity(): BookmarkEntity =
    BookmarkEntity(
        provider = key.provider.value,
        board = key.board.value,
        thread = key.thread.value,
        title = title,
        thumbnailUrl = thumbnailUrl,
        createdAtMillis = createdAtMillis,
        isWatched = isWatched,
        lastSeenReplyCount = lastSeenReplyCount,
        latestReplyCount = latestReplyCount,
        isThreadDead = isThreadDead,
    )

internal fun HistoryEntity.toDomain(): HistoryEntry =
    HistoryEntry(
        key = ThreadKey(ProviderId(provider), BoardId(board), ThreadId(thread)),
        title = title,
        thumbnailUrl = thumbnailUrl,
        lastVisitedMillis = lastVisitedMillis,
        lastReadPostId = lastReadPostId?.let(::PostId),
        lastReadOffsetPx = lastReadOffsetPx,
    )

internal fun HistoryEntry.toEntity(): HistoryEntity =
    HistoryEntity(
        provider = key.provider.value,
        board = key.board.value,
        thread = key.thread.value,
        title = title,
        thumbnailUrl = thumbnailUrl,
        lastVisitedMillis = lastVisitedMillis,
        lastReadPostId = lastReadPostId?.value,
        lastReadOffsetPx = lastReadOffsetPx,
    )

internal fun BoardEntity.toDomain(): Board =
    Board(
        id = BoardId(id),
        title = title,
        description = description,
        category = category,
        isNsfw = isNsfw,
        pageCount = pageCount,
        bumpLimit = bumpLimit,
        imageLimit = imageLimit,
        maxCommentChars = maxCommentChars,
        supportsMedia = supportsMedia,
    )

internal fun Board.toEntity(
    provider: ProviderId,
    sortIndex: Int,
    cachedAtMillis: Long,
): BoardEntity =
    BoardEntity(
        provider = provider.value,
        id = id.value,
        title = title,
        description = description,
        category = category,
        isNsfw = isNsfw,
        pageCount = pageCount,
        bumpLimit = bumpLimit,
        imageLimit = imageLimit,
        maxCommentChars = maxCommentChars,
        supportsMedia = supportsMedia,
        sortIndex = sortIndex,
        cachedAtMillis = cachedAtMillis,
    )

/**
 * Flattens a thread for [SavedThreadEntity] / [SavedPostEntity].
 *
 * Attachment URLs and names are stored positionally in two newline-separated columns rather than a
 * child table: a saved attachment is a link, not an entity anything joins against, and two columns
 * keep the whole snapshot one insert per post.
 */
internal fun Thread.toSavedEntities(savedAtMillis: Long): Pair<SavedThreadEntity, List<SavedPostEntity>> {
    val posts = allPosts
    val savedThread =
        SavedThreadEntity(
            provider = key.provider.value,
            board = key.board.value,
            thread = key.thread.value,
            title = originalPost.subject ?: originalPost.comment.raw.take(SAVED_TITLE_LENGTH),
            savedAtMillis = savedAtMillis,
            postCount = posts.size,
        )
    val savedPosts =
        posts.mapIndexed { index, post ->
            SavedPostEntity(
                provider = key.provider.value,
                board = key.board.value,
                thread = key.thread.value,
                postId = post.id.value,
                isOriginalPost = post.isOriginalPost,
                subject = post.subject,
                comment = post.comment.raw,
                posterName = post.poster.name,
                posterTripcode = post.poster.tripcode,
                posterIdentifier = post.poster.posterId,
                posterCapcode = post.poster.capcode,
                createdAtMillis = post.createdAtMillis,
                sortIndex = index,
                attachmentUrls = post.attachments.joinToString("\n") { it.sourceUrl },
                attachmentNames = post.attachments.joinToString("\n") { it.originalFileName },
            )
        }
    return savedThread to savedPosts
}

/**
 * Rebuilds a readable thread from a saved copy.
 *
 * The comment comes back as a single [PostNode.Text] run: the node tree was produced by a
 * provider's parser and is not re-derived here, so a saved thread reads as plain text. Quote links
 * and formatting are lost; the words are not.
 */
internal fun SavedThreadEntity.toDomain(posts: List<SavedPostEntity>): Thread {
    val key = ThreadKey(ProviderId(provider), BoardId(board), ThreadId(thread))
    val domainPosts = posts.map { it.toDomain(key) }
    return Thread(
        key = key,
        originalPost = domainPosts.firstOrNull { it.isOriginalPost } ?: domainPosts.first(),
        replies = domainPosts.filterNot { it.isOriginalPost }.toImmutableList(),
        stats = ThreadStats(replyCount = (postCount - 1).coerceAtLeast(0), isArchived = true),
    )
}

private fun SavedPostEntity.toDomain(key: ThreadKey): Post =
    Post(
        id = PostId(postId),
        board = key.board,
        threadId = key.thread,
        isOriginalPost = isOriginalPost,
        subject = subject,
        comment = PostComment(raw = comment, nodes = persistentListOf(PostNode.Text(comment))),
        poster =
            PosterInfo(
                name = posterName,
                tripcode = posterTripcode,
                posterId = posterIdentifier,
                capcode = posterCapcode,
            ),
        createdAtMillis = createdAtMillis,
        attachments = savedAttachments().toImmutableList(),
    )

/** Rebuilds the attachment links kept alongside a saved post, paired by position. */
private fun SavedPostEntity.savedAttachments(): List<MediaAttachment> {
    val urls = attachmentUrls.split("\n").filter { it.isNotBlank() }
    val names = attachmentNames.split("\n").filter { it.isNotBlank() }
    return urls.mapIndexed { index, url ->
        val name = names.getOrElse(index) { url.substringAfterLast('/') }
        MediaAttachment(
            id = "$postId-$index",
            originalFileName = name,
            extension = name.substringAfterLast('.', ""),
            type = MediaType.IMAGE,
            sourceUrl = url,
            thumbnailUrl = url,
        )
    }
}

/** Enough of an opening comment to recognise the thread by, when it has no subject. */
private const val SAVED_TITLE_LENGTH = 80
