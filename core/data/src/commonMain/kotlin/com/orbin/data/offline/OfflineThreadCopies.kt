package com.orbin.data.offline

import com.orbin.core.model.BoardId
import com.orbin.core.model.InlineStyle
import com.orbin.core.model.MediaAttachment
import com.orbin.core.model.MediaType
import com.orbin.core.model.Post
import com.orbin.core.model.PostComment
import com.orbin.core.model.PostId
import com.orbin.core.model.PostNode
import com.orbin.core.model.PosterInfo
import com.orbin.core.model.ProviderId
import com.orbin.core.model.Thread
import com.orbin.core.model.ThreadId
import com.orbin.core.model.ThreadKey
import com.orbin.core.model.ThreadStats
import com.orbin.data.database.dao.OfflineThreadCopyDao
import com.orbin.data.database.entity.OfflineThreadCopyEntity
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.time.Clock

private const val DEFAULT_COPY_BYTES = 16L * 1024 * 1024
private const val DEFAULT_TOTAL_COPY_BYTES = 128L * 1024 * 1024
private const val MAX_COMMENT_DEPTH = 64

/** Last-loaded text/metadata, separate from the user's explicitly saved threads and media. */
class OfflineThreadCopies(
    private val dao: OfflineThreadCopyDao,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val maxCopies: Int = 500,
    private val maxCopyBytes: Long = DEFAULT_COPY_BYTES,
    private val maxTotalBytes: Long = DEFAULT_TOTAL_COPY_BYTES,
) {
    /** Encrypted-database atomic replacement; an oversized update leaves the previous copy intact. */
    suspend fun keep(thread: Thread) =
        withContext(dispatcher) {
            if (thread.allPosts.size > MAX_POSTS) return@withContext
            val payload = json.encodeToString(StoredThread.serializer(), thread.stored())
            val size = payload.encodeToByteArray().size.toLong()
            if (size > maxCopyBytes) return@withContext
            dao.putBounded(
                OfflineThreadCopyEntity(
                    thread.key.provider.value,
                    thread.key.board.value,
                    thread.key.thread.value,
                    payload,
                    Clock.System.now().toEpochMilliseconds(),
                    size,
                ),
                maxCopies,
                maxTotalBytes,
            )
        }

    // Optional corrupt/unavailable copies must not turn a live thread load into a platform database error.
    @Suppress("TooGenericExceptionCaught")
    suspend fun read(key: ThreadKey): Thread? =
        withContext(dispatcher) {
            try {
                val entity = dao.get(key.provider.value, key.board.value, key.thread.value) ?: return@withContext null
                if (entity.sizeBytes !in 1..maxCopyBytes ||
                    entity.payload.length > maxCopyBytes
                ) {
                    return@withContext null
                }
                val copy = json.decodeFromString(StoredThread.serializer(), entity.payload)
                require(
                    copy.provider == key.provider.value &&
                        copy.board == key.board.value &&
                        copy.thread == key.thread.value,
                )
                require(copy.posts.size in 1..MAX_POSTS && copy.posts.first().original)
                require(
                    copy.posts
                        .map { it.id }
                        .toSet()
                        .size == copy.posts.size,
                )
                copy.domain()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // SQLite engines and serializers expose different exception classes on the two platforms.
                null
            }
        }

    /** Called when either collection changes, including clear history/session end. */
    suspend fun retain(keys: Set<ThreadKey>) = withContext(dispatcher) { dao.retain(keys) }

    private companion object {
        const val MAX_POSTS = 20_000
        val json = Json { ignoreUnknownKeys = true }
    }
}

@Serializable
private data class StoredThread(
    val provider: String,
    val board: String,
    val thread: Long,
    val savedAtMillis: Long,
    val posts: List<StoredPost>,
    val replyCount: Int,
    val imageCount: Int,
    val archived: Boolean,
    val uniquePosters: Int = 0,
    val sticky: Boolean = false,
    val closed: Boolean = false,
    val lastModified: Long = 0,
) {
    fun domain(): Thread {
        val key = ThreadKey(ProviderId(provider), BoardId(board), ThreadId(thread))
        val restored = posts.map { it.domain(key) }
        return Thread(
            key,
            restored.first(),
            restored.drop(1).toImmutableList(),
            ThreadStats(
                replyCount = replyCount,
                imageCount = imageCount,
                uniquePosterCount = uniquePosters,
                isSticky = sticky,
                isClosed = closed,
                isArchived = archived,
                lastModifiedMillis = lastModified,
            ),
        )
    }
}

@Serializable
private data class StoredPost(
    val id: Long,
    val original: Boolean,
    val subject: String?,
    val raw: String,
    val nodes: List<StoredNode>,
    val name: String?,
    val tripcode: String?,
    val posterId: String?,
    val capcode: String?,
    val countryCode: String?,
    val countryName: String?,
    val createdAtMillis: Long,
    val media: List<StoredMedia>,
    val repliesTo: List<Long>,
    val backlinks: List<Long>,
) {
    fun domain(key: ThreadKey): Post =
        Post(
            PostId(id),
            key.board,
            key.thread,
            original,
            subject,
            PostComment(raw, nodes.map { it.domain() }.toImmutableList()),
            PosterInfo(name, tripcode, posterId, capcode, countryCode, countryName),
            createdAtMillis,
            media.map { it.domain() }.toImmutableList(),
            repliesTo.map(::PostId).toImmutableList(),
            backlinks.map(::PostId).toImmutableList(),
        )
}

@Serializable
private data class StoredNode(
    val kind: String,
    val text: String = "",
    val children: List<StoredNode> = emptyList(),
    val target: Long = 0,
    val board: String? = null,
    val op: Boolean = false,
    val dead: Boolean = false,
) {
    fun domain(depth: Int = 0): PostNode {
        require(depth < MAX_COMMENT_DEPTH) { "Stored comment nesting is too deep" }
        return when (kind) {
            "text" -> PostNode.Text(text)
            "break" -> PostNode.LineBreak
            "link" -> PostNode.Link(text, children.map { it.domain(depth + 1) }.toImmutableList())
            "style" ->
                PostNode.Styled(
                    InlineStyle.valueOf(text),
                    children.map { it.domain(depth + 1) }.toImmutableList(),
                )
            "quote" -> PostNode.QuoteLink(PostId(target), board?.let(::BoardId), op, dead)
            else -> error("Unknown stored comment node")
        }
    }
}

@Serializable
private data class StoredMedia(
    val id: String,
    val name: String,
    val extension: String,
    val type: String,
    val source: String,
    val thumbnail: String,
    val width: Int,
    val height: Int,
    val thumbnailWidth: Int,
    val thumbnailHeight: Int,
    val bytes: Long,
    val spoiler: Boolean,
    val duration: Long?,
    val sourceSpoiler: Boolean? = null,
    val coverReasons: Set<String> = emptySet(),
) {
    fun domain(): MediaAttachment =
        MediaAttachment(
            id,
            name,
            extension,
            MediaType.valueOf(type),
            source,
            thumbnail,
            width,
            height,
            thumbnailWidth,
            thumbnailHeight,
            bytes,
            spoiler,
            duration,
            sourceIsSpoiler = sourceSpoiler,
            appCoverReasons = coverReasons,
        )
}

private fun Thread.stored(): StoredThread =
    StoredThread(
        key.provider.value,
        key.board.value,
        key.thread.value,
        Clock.System.now().toEpochMilliseconds(),
        allPosts.map { post ->
            StoredPost(
                post.id.value,
                post.isOriginalPost,
                post.subject,
                post.comment.raw,
                post.comment.nodes.map { it.stored() },
                post.poster.name,
                post.poster.tripcode,
                post.poster.posterId,
                post.poster.capcode,
                post.poster.countryCode,
                post.poster.countryName,
                post.createdAtMillis,
                post.attachments.map { file ->
                    StoredMedia(
                        file.id,
                        file.originalFileName,
                        file.extension,
                        file.type.name,
                        file.sourceUrl,
                        file.thumbnailUrl,
                        file.width,
                        file.height,
                        file.thumbnailWidth,
                        file.thumbnailHeight,
                        file.sizeBytes,
                        file.isSpoiler,
                        file.durationMs,
                        file.sourceIsSpoiler,
                        file.appCoverReasons,
                    )
                },
                post.repliesTo.map { it.value },
                post.backlinks.map { it.value },
            )
        },
        stats.replyCount,
        stats.imageCount,
        stats.isArchived,
        stats.uniquePosterCount,
        stats.isSticky,
        stats.isClosed,
        stats.lastModifiedMillis,
    )

private fun PostNode.stored(): StoredNode =
    when (this) {
        is PostNode.Text -> StoredNode("text", text)
        PostNode.LineBreak -> StoredNode("break")
        is PostNode.Link -> StoredNode("link", url, children.map { it.stored() })
        is PostNode.Styled -> StoredNode("style", style.name, children.map { it.stored() })
        is PostNode.QuoteLink ->
            StoredNode(
                "quote",
                target = target.value,
                board = board?.value,
                op = isOp,
                dead = isDead,
            )
    }
