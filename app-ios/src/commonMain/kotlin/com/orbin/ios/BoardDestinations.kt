package com.orbin.ios

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.orbin.core.model.CatalogThread
import com.orbin.core.model.FormFactor
import com.orbin.core.model.MediaAttachment
import com.orbin.core.model.MediaType
import com.orbin.core.model.catalogColumns
import com.orbin.core.model.comparator
import com.orbin.ios.resources.Res
import com.orbin.ios.resources.ios_media_play_video
import com.orbin.uinext.BoardScreen
import com.orbin.uinext.MediaCell
import com.orbin.uinext.MediaWallScreen
import com.orbin.uinext.NextPullToRefresh
import com.orbin.uinext.next
import kotlinx.coroutines.flow.map
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Clock

// A board: its catalog, its media wall, the iPad split beside a thread, and catalog tiles.

@Composable
internal fun CatalogDestination(
    browser: Browser,
    board: SiteBoard,
    formFactor: FormFactor,
) {
    val catalog by browser.catalog.collectAsState()
    val settings by browser.settings.current.collectAsState()
    val sort by browser.catalogSort.collectAsState()
    val visited by remember(board) { browser.visitedThreads(board) }.collectAsState(emptySet())
    val unread by remember(board) { browser.watched.unread(board) }.collectAsState(emptyMap())
    NextPullToRefresh(
        isRefreshing = catalog is Load.Loading,
        onRefresh = browser::retry,
        modifier = Modifier.fillMaxSize(),
    ) {
        Loaded(catalog, onRetry = browser::retry) { threads ->
            val now = remember(threads) { Clock.System.now().toEpochMilliseconds() }
            val sorted = remember(threads, sort) { threads.sortedWith(sort.comparator()) }
            val rows =
                remember(sorted, visited, unread) {
                    sorted.map { thread ->
                        val number = thread.key.thread.value
                        thread.toRow(now, read = number in visited, unread = unread[number] ?: 0)
                    }
                }
            val byRow = remember(threads) { threads.associateBy { "${it.key.board.value}/${it.key.thread.value}" } }
            // An iPad's chosen column count on its full width; elsewhere the catalog fits its tiles.
            BoxWithConstraints {
                BoardScreen(
                    columns = catalogColumns(formFactor, maxWidth.value.toInt(), maxHeight.value.toInt(), settings),
                    board = "/${board.board.id.value}/",
                    description = board.board.title,
                    itemCount = rows.size,
                    rowAt = { index -> rows.getOrNull(index) },
                    sortLabel = sort.label,
                    onSort = browser::cycleCatalogSort,
                    showRail = false,
                    onOpenRow = { row -> byRow[row.id]?.let { browser.openThread(it.key) } },
                    onOpenMedia = { browser.open(Route.BoardMedia(board)) },
                    thumbnail = { row, modifier -> byRow[row.id]?.let { CatalogThumbnail(it, modifier) } },
                )
            }
        }
    }
}

/** A board's media wall: every file its catalog carries, through the same sweep Android's wall runs. */
@Composable
internal fun BoardMediaDestination(
    browser: Browser,
    board: SiteBoard,
) {
    val catalog by browser.catalog.collectAsState()
    NextPullToRefresh(
        isRefreshing = catalog is Load.Loading,
        onRefresh = browser::retry,
        modifier = Modifier.fillMaxSize(),
    ) {
        Loaded(catalog, onRetry = browser::retry) { threads ->
            val items = remember(threads) { boardMediaItems(board, threads) }
            val byId = remember(items) { items.associateBy { it.id } }
            val label = "/${board.board.id.value}/"
            MediaWallScreen(
                scanned = 1,
                total = 1,
                failed = 0,
                cells = remember(items) { items.map { MediaCell(it.id, label) } },
                showRail = false,
                title = label,
                onOpen = { cell -> byId[cell.id]?.let { browser.openThread(it.key) } },
                tile = { cell, modifier ->
                    byId[cell.id]?.let { item ->
                        SharpImage(item.attachment, contentDescription = null, modifier, fill = Modifier.fillMaxSize())
                    }
                },
            )
        }
    }
}

/**
 * On iPad, keep the board catalog beside its selected thread. The browser stack still owns
 * navigation, so the system back gesture returns to the catalog as it does on Android.
 */
@Composable
internal fun ThreadSplitDestination(
    browser: Browser,
    board: SiteBoard,
    formFactor: FormFactor,
    downloads: MediaDownloads,
    pages: ThreadPages?,
) {
    Row(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f).fillMaxSize()) {
            CatalogDestination(browser, board, formFactor)
        }
        Box(Modifier.width(0.5.dp).fillMaxSize().background(next.hairline))
        Box(Modifier.weight(1.4f).fillMaxSize()) {
            ThreadDestination(browser, downloads, pages)
        }
    }
}

@Composable
internal fun CatalogThumbnail(
    thread: CatalogThread,
    modifier: Modifier,
    // Whether a video or GIF loops in place, muted; otherwise it shows its picture and a play badge.
    playsPreview: Boolean = true,
) {
    val attachment = thread.originalPost.attachments.firstOrNull() ?: return
    Box(modifier) {
        val canLoop = playsPreview && attachment.isPlayable && (!attachment.isWebM || supportsWebM)
        // One preview loops at a time, as on Android: each is a web view and a video decoder.
        val loops = rememberInlineLoopSlot(thread.key.toString(), canLoop)
        SharpImage(
            attachment,
            contentDescription = null,
            Modifier.fillMaxSize(),
            fill = Modifier.fillMaxSize(),
            // A looping preview covers the picture anyway, so it needs no still frame under it.
            videoFrame = !loops,
        )
        if (loops) NativeInlineLoop(attachment.sourceUrl, Modifier.matchParentSize())
        if (attachment.isSpoiler) {
            SpoilerCover(Modifier.matchParentSize())
        } else if (!loops && attachment.isPlayable) {
            PlayBadge(Modifier.align(Alignment.Center))
        }
    }
}

/** A video whose frames this device can decode: WebM needs iOS 17.4, MP4 and MOV play anywhere. */
private val MediaAttachment.hasDecodableVideo: Boolean
    get() = type == MediaType.VIDEO && (!isWebM || supportsWebM)

/** The mark on a video that is not playing: white on a dark disc, readable over any picture. */
@Composable
private fun PlayBadge(modifier: Modifier) {
    Icon(
        imageVector = Icons.Filled.PlayArrow,
        contentDescription = stringResource(Res.string.ios_media_play_video),
        tint = Color.White,
        modifier =
            modifier
                .size(PLAY_BADGE_SIZE)
                .background(Color.Black.copy(alpha = PLAY_BADGE_SCRIM), CircleShape)
                .padding(PLAY_BADGE_PADDING),
    )
}

private val PLAY_BADGE_SIZE = 40.dp
private val PLAY_BADGE_PADDING = 6.dp
private const val PLAY_BADGE_SCRIM = 0.5f

/**
 * A file's picture at the size it is shown: the site's thumbnail first, so the space fills at
 * once, then, for a still image, the full picture over it. A card or a post's image is far wider
 * than a thumbnail (about 250 pixels), which stretched looks blurry; Android loads full resolution
 * there too. The thumbnail sets the size, so the full picture only ever sharpens it.
 */
@Composable
internal fun SharpImage(
    attachment: MediaAttachment,
    contentDescription: String?,
    modifier: Modifier,
    // How the thumbnail takes the space: a card's whole fixed box, or a post's width at the
    // picture's own proportions.
    fill: Modifier = Modifier.fillMaxWidth(),
    videoFrame: Boolean = true,
) {
    // Hovering the Apple Pencil over a picture shows it larger, without opening it.
    PencilHoverPreview(attachment, modifier) {
        AsyncImage(
            model = attachment.thumbnailUrl,
            contentDescription = contentDescription,
            contentScale = ContentScale.Crop,
            modifier = fill,
        )
        val sharp = attachment.cardImageUrl()
        if (sharp != attachment.thumbnailUrl) {
            AsyncImage(
                model = sharp,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
            )
        }
        // A video has no full-size picture of its own: show its first frame, decoded from the file.
        if (videoFrame && attachment.hasDecodableVideo) {
            NativeVideoFrame(attachment.sourceUrl, Modifier.matchParentSize())
        }
    }
}
