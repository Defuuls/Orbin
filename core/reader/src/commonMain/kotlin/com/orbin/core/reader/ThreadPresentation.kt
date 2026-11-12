package com.orbin.core.reader

import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import com.orbin.core.model.MediaAttachment
import com.orbin.core.model.Post
import com.orbin.core.model.PostId
import com.orbin.core.model.Thread
import com.orbin.core.model.attachmentDetails
import com.orbin.core.ui.date.formatPostDateTime
import com.orbin.core.ui.post.CommentColors
import com.orbin.core.ui.post.PreparedPostComment
import com.orbin.core.ui.post.PreparedPostCommentText
import com.orbin.core.ui.post.preparePostComment
import com.orbin.domain.filter.ContentPolicy
import com.orbin.domain.thread.replyCounts
import com.orbin.domain.thread.replyDepths
import com.orbin.provider.api.UriParts
import com.orbin.uinext.LocalReaderPostBodyStyle
import com.orbin.uinext.MediaCell
import com.orbin.uinext.next
import com.orbin.uinext.reader
import com.orbin.uinext.tokens.ReaderAmoledColors
import com.orbin.uinext.tokens.ReaderDarkColors
import com.orbin.uinext.tokens.ReaderLightColors
import com.orbin.uinext.withReaderFont
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.time.Clock
import com.orbin.uinext.Post as ReaderPost

/** The complete model stays beside its presentation, including poster identity and every file. */
data class ThreadRow(
    val post: Post,
    val row: ReaderPost,
    val comment: PreparedPostComment,
)

/** Files are indexed by unique presentation keys rather than provider-supplied file ids. */
data class ThreadMediaIndex(
    val attachments: List<MediaAttachment>,
    val attachmentsById: Map<String, MediaAttachment>,
    val mediaIndex: Map<String, Int>,
    val fileCells: List<MediaCell>,
    val owners: List<PostId>,
    val firstByPost: Map<String, Int>,
)

/** One immutable snapshot shared by both platform hosts, prepared before content is published. */
class ThreadPresentationIndex(
    val thread: Thread,
    val rows: List<ThreadRow>,
    private val media: ThreadMediaIndex,
    val fromSavedCopy: Boolean,
    val preparedAtMillis: Long,
    val lastUpdateText: String,
) {
    val replyCount: Int = thread.stats.replyCount.coerceAtLeast(0)
    val rowModels: List<ReaderPost> = rows.map { it.row }
    val rowsById: Map<String, ThreadRow> = rows.associateBy { it.row.id }
    val attachments: List<MediaAttachment> get() = media.attachments
    val attachmentsById: Map<String, MediaAttachment> get() = media.attachmentsById
    val mediaIndex: Map<String, Int> get() = media.mediaIndex
    val fileCells: List<MediaCell> get() = media.fileCells

    fun postIdForMediaIndex(index: Int): PostId? = media.owners.getOrNull(index)

    fun firstMediaIndex(postId: String): Int? = media.firstByPost[postId]
}

/** All model walking, date formatting and rich text preparation happens on [dispatcher]. */
suspend fun Thread.preparePresentation(
    nowMillis: Long = Clock.System.now().toEpochMilliseconds(),
    fromSavedCopy: Boolean = false,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
): ThreadPresentationIndex = withContext(dispatcher) { mapThreadPresentation(nowMillis, fromSavedCopy) }

/** Reuses a published snapshot if a repository re-emits the same immutable thread. */
class ThreadPresentationCache {
    private var prepared: ThreadPresentationIndex? = null
    private var visiblePrepared: VisiblePreparation? = null

    suspend fun prepare(
        thread: Thread,
        fromSavedCopy: Boolean = false,
        dispatcher: CoroutineDispatcher = Dispatchers.Default,
    ): ThreadPresentationIndex {
        val previous = prepared
        val next =
            withContext(dispatcher) {
                if (previous?.thread == thread && previous.fromSavedCopy == fromSavedCopy) {
                    previous
                } else {
                    thread.mapThreadPresentation(Clock.System.now().toEpochMilliseconds(), fromSavedCopy)
                }
            }
        // withContext checks cancellation before returning to the owner, so an obsolete load
        // cannot replace the cached revision even if its worker completed at the same moment.
        return next.also { prepared = it }
    }

    /** Filters and formats the actual host load together, caching even a blocked result. */
    suspend fun prepareVisible(
        thread: Thread,
        fromSavedCopy: Boolean = false,
        dispatcher: CoroutineDispatcher = Dispatchers.Default,
    ): ThreadPresentationIndex? {
        val previous = visiblePrepared
        val next =
            withContext(dispatcher) {
                if (previous?.source == thread && previous.fromSavedCopy == fromSavedCopy) {
                    previous
                } else {
                    VisiblePreparation(
                        source = thread,
                        fromSavedCopy = fromSavedCopy,
                        index =
                            ContentPolicy
                                .visibleThread(thread)
                                ?.mapThreadPresentation(Clock.System.now().toEpochMilliseconds(), fromSavedCopy),
                    )
                }
            }
        visiblePrepared = next
        return next.index
    }

    private data class VisiblePreparation(
        val source: Thread,
        val fromSavedCopy: Boolean,
        val index: ThreadPresentationIndex?,
    )
}

private fun Thread.mapThreadPresentation(
    nowMillis: Long,
    fromSavedCopy: Boolean,
): ThreadPresentationIndex {
    val posts = allPosts
    val rows = mapRows(posts)
    val attachments = mutableListOf<MediaAttachment>()
    val attachmentsById = mutableMapOf<String, MediaAttachment>()
    val mediaIndex = mutableMapOf<String, Int>()
    val cells = mutableListOf<MediaCell>()
    val owners = mutableListOf<PostId>()
    val firstMediaByPost = mutableMapOf<String, Int>()
    posts.forEach { post ->
        post.attachments.forEachIndexed { offset, attachment ->
            // Attachment ids are provider values and need not be unique across posts.
            val fileKey = "${key.provider.value}/${key.board.value}/${post.id.value}/file/$offset"
            firstMediaByPost.getOrPut(post.id.value.toString()) { attachments.size }
            mediaIndex[fileKey] = attachments.size
            attachmentsById[fileKey] = attachment
            attachments.add(attachment)
            cells.add(
                MediaCell(
                    id = fileKey,
                    board = "/${key.board.value}/",
                    spoiler = attachment.isSpoiler,
                    details = attachment.attachmentDetails(),
                ),
            )
            owners.add(post.id)
        }
    }
    return ThreadPresentationIndex(
        thread = this,
        rows = rows,
        media = ThreadMediaIndex(attachments, attachmentsById, mediaIndex, cells, owners, firstMediaByPost),
        fromSavedCopy = fromSavedCopy,
        preparedAtMillis = nowMillis,
        lastUpdateText = formatPostDateTime(stats.lastModifiedMillis).orEmpty(),
    )
}

private fun Thread.mapRows(posts: List<Post>): List<ThreadRow> {
    val depths = replyDepths(posts)
    val counts = replyCounts(posts)
    val replies = mutableMapOf<PostId, MutableList<String>>()
    val known = posts.mapTo(mutableSetOf()) { it.id }
    posts.forEach { post ->
        post.repliesTo.toSet().filter { it in known && it != post.id }.forEach { target ->
            replies.getOrPut(target) { mutableListOf() }.add(post.id.value.toString())
        }
    }
    return posts.map { post ->
        ThreadRow(
            post = post,
            row =
                ReaderPost(
                    id = post.id.value.toString(),
                    stableKey = "${key.provider.value}/${key.board.value}/${post.id.value}",
                    number = "No. ${post.id.value}",
                    author = post.authorLabel(),
                    time = formatPostDateTime(post.createdAtMillis).orEmpty(),
                    body = post.comment.plainText(),
                    depth = depths[post.id] ?: 0,
                    hasMedia = post.attachments.isNotEmpty(),
                    mediaCount = post.attachments.size,
                    imageSpoiler = post.attachments.firstOrNull()?.isSpoiler == true,
                    subject = post.subject.orEmpty(),
                    fileLabel =
                        post.attachments
                            .firstOrNull()
                            ?.fileLabel()
                            .orEmpty(),
                    replies = counts[post.id] ?: 0,
                    replyNumbers = replies[post.id].orEmpty(),
                ),
            comment = preparePostComment(post.comment, commentPalettes, isolateQuoteLinks = true),
        )
    }
}

private val commentPalettes =
    listOf(ReaderLightColors, ReaderDarkColors, ReaderAmoledColors).map {
        CommentColors(quote = it.quote, link = it.quoteLink, spoiler = it.surfaceVariant)
    }

@Composable
fun ReaderPostComment(
    row: ThreadRow,
    modifier: Modifier = Modifier,
    onQuoteClick: (PostId) -> Unit = {},
    onLinkClick: (String) -> Unit = {},
) {
    val palette =
        if (next.dark) {
            if (next.amoled) {
                2
            } else {
                1
            }
        } else {
            0
        }
    val style = LocalReaderPostBodyStyle.current.withReaderFont().copy(color = reader.ink)
    // Replace the surrounding Material line height as well as its size: Clover uses natural
    // font metrics, including when a rich comment is rendered outside a ThreadScreen.
    CompositionLocalProvider(LocalTextStyle provides style) {
        PreparedPostCommentText(
            prepared = row.comment,
            palette = palette,
            modifier = modifier,
            selectable = true,
            style = style,
            onQuoteClick = onQuoteClick,
            onLinkClick = { safeExternalLink(it)?.let(onLinkClick) },
        )
    }
}

/** The common HTTPS grammar is also checked against java.net.URI in provider SPI tests. */
fun safeExternalLink(url: String): String? {
    val trimmed = url.trim()
    if (!trimmed.startsWith("https://", ignoreCase = true)) return null
    val parts = UriParts.parse(trimmed) ?: return null
    return trimmed.takeIf { parts.scheme.equals("https", ignoreCase = true) && !parts.host.isNullOrBlank() }
}

/** Files and ownership helpers also serve navigation before a reader snapshot is subscribed. */
val Thread.threadFiles: List<MediaAttachment> get() = allPosts.flatMap { it.attachments }

fun Thread.mediaPostId(index: Int): PostId? {
    if (index < 0) return null
    var remaining = index
    for (post in allPosts) {
        if (remaining < post.attachments.size) return post.id
        remaining -= post.attachments.size
    }
    return null
}

fun Thread.firstMediaIndex(postId: String): Int? {
    var index = 0
    for (post in allPosts) {
        if (post.id.value.toString() == postId) return index.takeIf { post.attachments.isNotEmpty() }
        index += post.attachments.size
    }
    return null
}

private fun Post.authorLabel(): String =
    listOfNotNull(
        poster.name?.takeIf(String::isNotBlank),
        poster.tripcode?.takeIf(String::isNotBlank),
        poster.posterId?.takeIf(String::isNotBlank)?.let { "ID: $it" },
        poster.capcode?.takeIf(String::isNotBlank),
        poster.countryName?.takeIf(String::isNotBlank) ?: poster.countryCode?.takeIf(String::isNotBlank),
    ).joinToString(" · ")

private fun MediaAttachment.fileLabel(): String {
    val name = originalFileName.ifBlank { id }
    if (sizeBytes <= 0) return name
    return "$name (${readableFileSize(sizeBytes)})"
}

private fun readableFileSize(bytes: Long): String =
    when {
        bytes < BYTES_PER_KIB -> "$bytes B"
        bytes < BYTES_PER_MIB -> "${bytes / BYTES_PER_KIB} KB"
        else -> "${bytes / BYTES_PER_MIB} MB"
    }

private const val BYTES_PER_KIB = 1_024L
private const val BYTES_PER_MIB = 1_048_576L
