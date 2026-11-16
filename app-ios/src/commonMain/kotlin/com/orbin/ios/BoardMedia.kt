package com.orbin.ios

import com.orbin.core.model.CatalogThread
import com.orbin.core.model.attachmentDetails
import com.orbin.domain.media.AllMediaItem
import com.orbin.domain.media.MediaWallCollector
import com.orbin.domain.media.sweepBoard
import com.orbin.uinext.components.mediaKindOf
import com.orbin.uinext.screens.MediaCell

/** The files of [board]'s catalog as wall items, filtered and de-duplicated as Android's wall is. */
internal fun boardMediaItems(
    board: SiteBoard,
    catalog: List<CatalogThread>,
): List<AllMediaItem> {
    val collector = MediaWallCollector()
    collector.append(sweepBoard(board.board, catalog, hiddenTokens = emptySet()).items)
    return collector.items
}

/** Preserve the attachment's real type for media counts and filters, alongside its source key. */
internal fun AllMediaItem.toBoardMediaCell(label: String): MediaCell =
    MediaCell(
        id = "${key.provider.value}/$id",
        board = "${key.provider.value} $label",
        spoiler = attachment.isSpoiler,
        details = attachment.attachmentDetails(),
        kind = mediaKindOf(attachment.type.name),
    )
