package com.orbin.data.database

import com.orbin.core.model.BoardId
import com.orbin.core.model.Bookmark
import com.orbin.core.model.HistoryEntry
import com.orbin.core.model.PostId
import com.orbin.core.model.ProviderId
import com.orbin.core.model.ThreadId
import com.orbin.core.model.ThreadKey
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
    )
