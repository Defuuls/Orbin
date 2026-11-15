package com.orbin.provider.api

import com.orbin.core.model.APP_COVER_NSFW
import com.orbin.core.model.Board
import com.orbin.core.model.BoardId
import com.orbin.core.model.CatalogRequest
import com.orbin.core.model.CatalogThread
import com.orbin.core.model.SearchQuery
import com.orbin.core.model.SearchResult
import com.orbin.core.model.Thread
import com.orbin.core.model.ThreadId
import com.orbin.core.model.withGeneratedMediaCover
import com.orbin.core.model.withNsfwMediaCovered
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** NSFW media uses the existing revealable spoiler cover on every provider-backed surface. */
class NsfwMediaCoverProvider(
    private val delegate: ImageBoardProvider,
    private val enabled: suspend () -> Boolean,
) : ImageBoardProvider by delegate {
    private val boardLock = Mutex()
    private var nsfwBoards: Map<BoardId, Boolean>? = null

    override suspend fun getBoards(): List<Board> = delegate.getBoards().also { rememberBoards(it) }

    override suspend fun getCatalog(request: CatalogRequest): List<CatalogThread> {
        val catalog = delegate.getCatalog(request)
        val cover = enabled() && isNsfw(request.board)
        return catalog.map { entry ->
            entry.copy(
                originalPost = entry.originalPost.withGeneratedMediaCover(APP_COVER_NSFW, cover),
                previewReplies =
                    entry.previewReplies
                        .map {
                            it.withGeneratedMediaCover(
                                APP_COVER_NSFW,
                                cover,
                            )
                        }.toImmutableList(),
            )
        }
    }

    override suspend fun getThread(
        board: BoardId,
        thread: ThreadId,
    ): Thread {
        val loaded = delegate.getThread(board, thread)
        return loaded.withNsfwMediaCovered(enabled() && isNsfw(board))
    }

    override suspend fun search(query: SearchQuery): List<SearchResult> =
        delegate.search(query).let { results ->
            if (!enabled()) results else results.map { if (isNsfw(it.key.board)) it.copy(thumbnailUrl = null) else it }
        }

    private suspend fun rememberBoards(boards: List<Board>) =
        boardLock.withLock {
            nsfwBoards = boards.associate { it.id to it.isNsfw }
        }

    private suspend fun isNsfw(board: BoardId): Boolean =
        boardLock.withLock {
            if (nsfwBoards == null) {
                try {
                    nsfwBoards = delegate.getBoards().associate { it.id to it.isNsfw }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    // Unknown board classification must not expose media while its cover is enabled.
                    return@withLock true
                }
            }
            nsfwBoards?.get(board) ?: true
        }
}
