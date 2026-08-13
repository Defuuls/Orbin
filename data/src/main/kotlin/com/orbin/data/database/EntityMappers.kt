package com.orbin.data.database

import com.orbin.core.model.Board
import com.orbin.core.model.BoardId
import com.orbin.core.model.Bookmark
import com.orbin.core.model.HistoryEntry
import com.orbin.core.model.PostId
import com.orbin.core.model.ProviderId
import com.orbin.core.model.ThreadId
import com.orbin.core.model.ThreadKey
import com.orbin.data.database.entity.BoardEntity
import com.orbin.data.database.entity.BookmarkEntity
import com.orbin.data.database.entity.HistoryEntity

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
