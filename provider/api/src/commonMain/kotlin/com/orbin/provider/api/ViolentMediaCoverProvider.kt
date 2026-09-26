package com.orbin.provider.api

import com.orbin.core.model.BoardId
import com.orbin.core.model.CatalogRequest
import com.orbin.core.model.CatalogThread
import com.orbin.core.model.Thread
import com.orbin.core.model.ThreadId
import com.orbin.core.model.withViolentMediaCovered

/**
 * Spoiler-covers the media of posts labelled as violent ([com.orbin.core.model.ViolentMediaCover])
 * while [enabled] says so, in every catalog and thread the provider returns.
 *
 * Doing it at this seam means every surface that draws a thumbnail — feed, catalog, thread,
 * gallery, media wall — shows the cover it already draws for a spoilered file, and the feed's
 * no-autoplay-for-spoilers rule applies too, without any of them knowing this exists.
 */
class ViolentMediaCoverProvider(
    private val delegate: ImageBoardProvider,
    private val enabled: suspend () -> Boolean,
) : ImageBoardProvider by delegate {
    override suspend fun getCatalog(request: CatalogRequest): List<CatalogThread> {
        val catalog = delegate.getCatalog(request)
        return if (enabled()) catalog.map { it.withViolentMediaCovered() } else catalog
    }

    override suspend fun getThread(
        board: BoardId,
        thread: ThreadId,
    ): Thread {
        val loaded = delegate.getThread(board, thread)
        return if (enabled()) loaded.withViolentMediaCovered() else loaded
    }
}
