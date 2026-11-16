package com.orbin.core.reader

import com.orbin.core.model.CatalogThread
import com.orbin.uinext.FeedRow

/**
 * The stable key of a thread's feed or catalog row. It includes the source because two sites can
 * have the same board code and thread number.
 */
fun threadRowId(
    provider: String,
    board: String,
    thread: Long,
): String = "$provider/$board/$thread"

/** A source's reader-facing name, from its provider ID. Unknown providers show their ID. */
fun providerLabel(provider: String): String =
    when (provider) {
        "fourchan" -> "4chan"
        "bbwchan" -> "BBWChan"
        else -> provider
    }

/**
 * A Home feed row: the shared catalog row, which also names its site, writes its board as
 * [boardLabel] does and carries the thread number, keyed by [provider] (the source the feed
 * loaded it from).
 */
fun CatalogThread.feedRow(
    provider: String,
    nowMillis: Long,
    read: Boolean = false,
): FeedRow =
    catalogRow(nowMillis).copy(
        id = threadRowId(provider, key.board.value, key.thread.value),
        site = providerLabel(provider),
        board = boardLabel(key.board.value),
        threadNumber = key.thread.value.toString(),
        read = read,
    )
