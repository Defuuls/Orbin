package com.orbin.core.reader

import com.orbin.core.model.CatalogThread
import com.orbin.core.ui.date.formatRelativeTime
import com.orbin.uinext.FeedRow
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Static catalog presentation travels with the model needed by media and navigation actions. */
data class PreparedCatalogThread(
    val thread: CatalogThread,
    val row: FeedRow,
)

suspend fun prepareCatalogThread(
    thread: CatalogThread,
    nowMillis: Long,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
): PreparedCatalogThread = withContext(dispatcher) { PreparedCatalogThread(thread, thread.catalogRow(nowMillis)) }

/** Shared pure adapter; hosts call this on a worker when preparing a catalog load. */
fun CatalogThread.catalogRow(nowMillis: Long): FeedRow {
    val excerpt = originalPost.comment.plainText()
    val preview = originalPost.attachments.firstOrNull()
    return FeedRow(
        id = "${key.provider.value}/${key.board.value}/${key.thread.value}",
        site = sourceLabel(key.provider.value),
        poster = originalPost.poster.name?.takeIf(String::isNotBlank) ?: "Anonymous",
        mediaLabel = preview?.extension.orEmpty().uppercase(),
        subject =
            originalPost.subject?.takeIf(String::isNotBlank)
                ?: excerpt.lineSequence().firstOrNull(String::isNotBlank)
                ?: "No.${key.thread.value}",
        board = key.board.value,
        activity =
            formatRelativeTime(
                if (stats.lastModifiedMillis > 0L) stats.lastModifiedMillis else originalPost.createdAtMillis,
                nowMillis,
            ).orEmpty(),
        replies = stats.replyCount.coerceAtLeast(0),
        media = stats.imageCount.coerceAtLeast(0),
        hasPreview = preview != null,
        imageSpoiler = preview?.isSpoiler == true,
        excerpt = excerpt,
        mediaAspectRatio = preview?.previewAspectRatio ?: 0f,
        sticky = stats.isSticky,
        closed = stats.isClosed,
    )
}

private fun sourceLabel(provider: String): String =
    when (provider) {
        "fourchan" -> "4chan"
        "bbwchan" -> "BBWChan"
        else -> provider
    }
