package com.orbin.provider.vichan

import com.orbin.core.model.Board
import com.orbin.core.model.BoardId
import com.orbin.core.model.CatalogThread
import com.orbin.core.model.MediaAttachment
import com.orbin.core.model.MediaType
import com.orbin.core.model.Post
import com.orbin.core.model.PostId
import com.orbin.core.model.PosterInfo
import com.orbin.core.model.Thread
import com.orbin.core.model.ThreadId
import com.orbin.core.model.ThreadKey
import com.orbin.core.model.ThreadStats
import com.orbin.provider.vichan.api.VichanBoard
import com.orbin.provider.vichan.api.VichanBoardsResponse
import com.orbin.provider.vichan.api.VichanCatalogPage
import com.orbin.provider.vichan.api.VichanFile
import com.orbin.provider.vichan.api.VichanPost
import com.orbin.provider.vichan.api.VichanThreadResponse
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

/**
 * Maps vichan/4chan wire DTOs to Orbin domain models for a specific [site]. All URL construction
 * and engine-specific quirks live here, so the rest of the app stays engine-agnostic.
 */
class VichanMapper(
    private val site: VichanSite,
) {
    fun mapBoards(response: VichanBoardsResponse): List<Board> = response.boards.map(::mapBoard)

    private fun mapBoard(dto: VichanBoard): Board =
        Board(
            id = BoardId(dto.board),
            title = dto.title,
            description = dto.metaDescription,
            isNsfw = dto.workSafe == 0,
            pageCount = dto.pages,
            bumpLimit = dto.bumpLimit,
            imageLimit = dto.imageLimit,
            maxCommentChars = dto.maxCommentChars,
        )

    fun mapCatalog(
        board: BoardId,
        pages: List<VichanCatalogPage>,
    ): List<CatalogThread> =
        pages.flatMap { page ->
            page.threads.map { op ->
                val threadId = ThreadId(op.no)
                CatalogThread(
                    key = ThreadKey(site.providerId, board, threadId),
                    originalPost = mapPost(board, threadId, op, isOp = true),
                    stats = mapStats(op),
                )
            }
        }

    fun mapThread(
        board: BoardId,
        response: VichanThreadResponse,
    ): Thread {
        val posts = response.posts
        require(posts.isNotEmpty()) { "Thread response had no posts" }
        val opDto = posts.first()
        val threadId = ThreadId(opDto.no)
        val op = mapPost(board, threadId, opDto, isOp = true)
        val replies = posts.drop(1).map { mapPost(board, threadId, it, isOp = false) }
        return Thread(
            key = ThreadKey(site.providerId, board, threadId),
            originalPost = op,
            replies = replies.toImmutableList(),
            stats = mapStats(opDto),
        )
    }

    private fun mapStats(op: VichanPost): ThreadStats =
        ThreadStats(
            replyCount = op.replies,
            imageCount = op.images,
            uniquePosterCount = op.uniqueIps,
            isSticky = op.sticky == 1,
            isClosed = op.closed == 1 || op.locked == 1,
            isArchived = op.archived == 1,
            lastModifiedMillis = (if (op.lastModified > 0) op.lastModified else op.time) * MILLIS_PER_SECOND,
        )

    fun mapPost(
        board: BoardId,
        threadId: ThreadId,
        dto: VichanPost,
        isOp: Boolean,
    ): Post {
        val comment = VichanCommentParser.parse(dto.com)
        return Post(
            id = PostId(dto.no),
            board = board,
            threadId = threadId,
            isOriginalPost = isOp,
            subject = dto.sub?.takeIf { it.isNotBlank() },
            comment = comment,
            poster =
                PosterInfo(
                    name = dto.name?.takeIf { it.isNotBlank() },
                    tripcode = dto.trip,
                    posterId = dto.id,
                    capcode = dto.capcode,
                    countryCode = dto.country,
                    countryName = dto.countryName,
                ),
            createdAtMillis = dto.time * MILLIS_PER_SECOND,
            attachments = buildAttachments(board, dto).toImmutableList(),
            repliesTo = comment.quotedPosts.toImmutableList(),
            backlinks = persistentListOf(),
        )
    }

    private fun buildAttachments(
        board: BoardId,
        dto: VichanPost,
    ): List<MediaAttachment> {
        val primary =
            dto.tim?.let { tim ->
                attachment(
                    board = board,
                    tim = tim,
                    filename = dto.filename,
                    ext = dto.ext,
                    sizeBytes = dto.fsize,
                    width = dto.w,
                    height = dto.h,
                    thumbWidth = dto.tnW,
                    thumbHeight = dto.tnH,
                    spoiler = dto.spoiler == 1,
                )
            }
        val extras = dto.extraFiles.mapNotNull { it.toAttachment(board) }
        return listOfNotNull(primary) + extras
    }

    private fun VichanFile.toAttachment(board: BoardId): MediaAttachment? {
        val tim = this.tim ?: return null
        return attachment(board, tim, filename, ext, fsize, w, h, tnW, tnH, spoiler == 1)
    }

    @Suppress("LongParameterList") // mirrors the flat DTO; grouping would add ceremony, not clarity.
    private fun attachment(
        board: BoardId,
        tim: String,
        filename: String?,
        ext: String?,
        sizeBytes: Long,
        width: Int,
        height: Int,
        thumbWidth: Int,
        thumbHeight: Int,
        spoiler: Boolean,
    ): MediaAttachment {
        val safeExt = ext.orEmpty()
        val type = mediaType(safeExt)
        return MediaAttachment(
            id = tim,
            originalFileName = (filename ?: tim) + safeExt,
            extension = safeExt.removePrefix("."),
            type = type,
            sourceUrl = fullUrl(board.value, tim, safeExt),
            thumbnailUrl = thumbUrl(board.value, tim, safeExt, type),
            width = width,
            height = height,
            thumbnailWidth = thumbWidth,
            thumbnailHeight = thumbHeight,
            sizeBytes = sizeBytes,
            isSpoiler = spoiler,
        )
    }

    private fun fullUrl(
        board: String,
        tim: String,
        ext: String,
    ): String =
        when (site.mediaUrlStyle) {
            MediaUrlStyle.FOURCHAN -> "${site.mediaBaseUrl}/$board/$tim$ext"
            MediaUrlStyle.VICHAN -> "${site.mediaBaseUrl}/$board/src/$tim$ext"
        }

    private fun thumbUrl(
        board: String,
        tim: String,
        ext: String,
        type: MediaType,
    ): String {
        // Video/audio thumbnails are always still images on these engines.
        val thumbExt = if (type == MediaType.VIDEO || type == MediaType.AUDIO) ".jpg" else ext
        return when (site.mediaUrlStyle) {
            MediaUrlStyle.FOURCHAN -> "${site.thumbBaseUrl}/$board/${tim}s.jpg"
            MediaUrlStyle.VICHAN -> "${site.thumbBaseUrl}/$board/thumb/$tim$thumbExt"
        }
    }

    private fun mediaType(ext: String): MediaType =
        when (ext.removePrefix(".").lowercase()) {
            "jpg", "jpeg", "png", "webp", "bmp" -> MediaType.IMAGE
            "gif", "apng" -> MediaType.ANIMATED_IMAGE
            "webm", "mp4", "mov", "mkv" -> MediaType.VIDEO
            "mp3", "ogg", "opus", "flac", "wav", "m4a" -> MediaType.AUDIO
            else -> MediaType.UNKNOWN
        }

    private companion object {
        const val MILLIS_PER_SECOND = 1000L
    }
}
