package com.orbin.domain.media

import com.orbin.core.model.AppSettings
import com.orbin.core.model.Board
import com.orbin.core.model.CatalogThread
import com.orbin.core.model.MediaAttachment
import com.orbin.core.model.Post
import com.orbin.core.model.ThreadKey
import com.orbin.core.model.isPermanentlyFiltered
import com.orbin.core.model.matchesFilterTokens
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

// The media wall's sweep, shared by Android's AllMediaViewModel and iOS's board media wall: which
// boards it visits, what each catalog and thread contributes, and how the wall grows.

/**
 * One file on the wall, carrying enough of where it came from to open it and to label the tile.
 */
data class AllMediaItem(
    val attachment: MediaAttachment,
    val key: ThreadKey,
    val boardTitle: String,
    val threadTitle: String,
) {
    /** Stable across rescans and unique within a scan, so the grid can key its items by it. */
    val id: String get() = "${key.board.value}/${key.thread.value}/${attachment.id}"
}

data class AllMediaUiState(
    val items: ImmutableList<AllMediaItem> = persistentListOf(),
    val boardsScanned: Int = 0,
    val boardsTotal: Int = 0,
    val isScanning: Boolean = false,
    /** Boards whose catalog could not be fetched this sweep; the rest of the wall still stands. */
    val failedBoards: Int = 0,
    /** True while the deep pass is walking threads for reply media, after the catalog sweep. */
    val isDeepScanning: Boolean = false,
    val threadsScanned: Int = 0,
    val threadsTotal: Int = 0,
) {
    val isInitialLoad: Boolean get() = isScanning && items.isEmpty()
}

/** A thread the deep scan can walk, with the labels its files will carry onto the wall. */
data class DeepScanTarget(
    val key: ThreadKey,
    val boardTitle: String,
    val threadTitle: String,
)

/** What one board's catalog yielded: files for the wall now, threads for the deep scan later. */
data class BoardSweep(
    val items: List<AllMediaItem>,
    val targets: List<DeepScanTarget>,
)

/**
 * The settings a sweep actually depends on. Isolated from [AppSettings] so that changing an
 * unrelated preference — a theme, a download folder — does not throw away the whole wall and
 * re-fetch every board.
 */
data class ScanSettings(
    val hideNsfwBoards: Boolean,
    val hiddenTokens: Set<String>,
    val coverViolentMedia: Boolean = true,
) {
    // Reader tags are gone, so only the permanent filter applies on top of the NSFW choice.
    constructor(settings: AppSettings) : this(
        hideNsfwBoards = settings.hideNsfwBoards,
        hiddenTokens = emptySet(),
        coverViolentMedia = settings.coverViolentMedia,
    )
}

/** The boards a sweep should visit: the reader's board filters, applied as the feed applies them. */
fun List<Board>.visibleUnder(scanSettings: ScanSettings): List<Board> =
    filterNot { board -> scanSettings.hideNsfwBoards && board.isNsfw }
        // Catches the permanent filter too, so a board it hides is never even fetched.
        .filterNot { board -> board.matchesFilterTokens(scanSettings.hiddenTokens) }
        .sortedBy { it.id.value }

/**
 * Every attachment a board's catalog carries, as wall items, plus the threads behind them.
 *
 * Teaser replies count as well as opening posts: they cost nothing extra, and they are the only
 * reply-level media a catalog sweep can reach on its own.
 *
 * A thread the filters reject yields no files *and* no deep-scan target — otherwise the deep scan
 * would walk straight back into a thread the wall had deliberately dropped.
 */
fun sweepBoard(
    board: Board,
    catalog: List<CatalogThread>,
    hiddenTokens: Set<String>,
): BoardSweep {
    val visible = catalog.filterNot { thread -> thread.matchesFilterTokens(hiddenTokens) }
    val targets =
        visible.map { thread ->
            DeepScanTarget(key = thread.key, boardTitle = board.id.value, threadTitle = thread.title())
        }
    val items =
        visible.flatMapIndexed { index, thread ->
            (listOf(thread.originalPost) + thread.previewReplies).mediaItemsFor(targets[index], hiddenTokens)
        }
    return BoardSweep(items = items, targets = targets)
}

/** The wall items a set of posts contributes, with every filter the wall applies. */
fun List<Post>.mediaItemsFor(
    target: DeepScanTarget,
    hiddenTokens: Set<String>,
): List<AllMediaItem> =
    filterNot { post -> post.matchesFilterTokens(hiddenTokens) }
        .flatMap { post -> post.attachments }
        .filterNot { attachment -> attachment.isPermanentlyFiltered() }
        .map { attachment ->
            AllMediaItem(
                attachment = attachment,
                key = target.key,
                boardTitle = target.boardTitle,
                threadTitle = target.threadTitle,
            )
        }

/** A short label for the tile: the subject if the thread has one, otherwise the start of the OP. */
private fun CatalogThread.title(): String {
    val subject = originalPost.subject?.trim().orEmpty()
    if (subject.isNotEmpty()) return subject
    val comment = originalPost.comment.plainText().trim()
    return if (comment.length > TITLE_MAX_CHARS) comment.take(TITLE_MAX_CHARS) + "…" else comment
}

private const val TITLE_MAX_CHARS = 60

/**
 * The wall's contents as they grow: files are appended in the order they arrive, a file already
 * on the wall (the same source URL reposted, or an OP file the deep scan finds again) is dropped,
 * and growth stops at [limit] so an unbounded deep scan cannot hold every file on a large site.
 */
class MediaWallCollector(
    private val limit: Int = MAX_WALL_ITEMS,
) {
    private val seenUrls = mutableSetOf<String>()
    private var collected: List<AllMediaItem> = emptyList()

    val items: List<AllMediaItem> get() = collected

    val isFull: Boolean get() = collected.size >= limit

    /** Adds [items]; returns true when anything new was added. */
    fun append(items: List<AllMediaItem>): Boolean {
        if (isFull) return false
        val fresh =
            items
                .asSequence()
                .filter { seenUrls.add(it.attachment.sourceUrl) }
                .take(limit - collected.size)
                .toList()
        if (fresh.isEmpty()) return false
        collected = collected + fresh
        return true
    }

    fun clear() {
        seenUrls.clear()
        collected = emptyList()
    }

    companion object {
        /**
         * Hard cap on wall growth so an unbounded deep scan cannot retain every attachment on a
         * large provider in Compose state / lazy-grid keys.
         */
        const val MAX_WALL_ITEMS = 4_000

        /**
         * The deep scan's pace: one thread request a second, sequentially, the rate the major
         * engines ask clients to stay under.
         */
        const val DEEP_SCAN_REQUEST_INTERVAL_MS = 1_000L
    }
}
