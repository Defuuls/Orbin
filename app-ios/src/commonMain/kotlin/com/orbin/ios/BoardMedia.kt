package com.orbin.ios

import com.orbin.core.model.CatalogThread
import com.orbin.domain.media.AllMediaItem
import com.orbin.domain.media.MediaWallCollector
import com.orbin.domain.media.sweepBoard

/** The files of [board]'s catalog as wall items, filtered and de-duplicated as Android's wall is. */
internal fun boardMediaItems(
    board: SiteBoard,
    catalog: List<CatalogThread>,
): List<AllMediaItem> {
    val collector = MediaWallCollector()
    collector.append(sweepBoard(board.board, catalog, hiddenTokens = emptySet()).items)
    return collector.items
}
