package com.orbin.provider.lynxchan

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
import com.orbin.provider.lynxchan.api.LynxChanBoard
import com.orbin.provider.lynxchan.api.LynxChanBoardsResponse
import com.orbin.provider.lynxchan.api.LynxChanCatalogThread
import com.orbin.provider.lynxchan.api.LynxChanFile
import com.orbin.provider.lynxchan.api.LynxChanPost
import com.orbin.provider.lynxchan.api.LynxChanThreadResponse
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import java.time.Instant

/**
 * Maps LynxChan wire DTOs to Orbin domain models for a specific [site]. All URL construction and
 * engine-specific quirks live here, so the rest of the app stays engine-agnostic.
 */
class LynxChanMapper(
    private val site: LynxChanSite,
) {
    fun mapBoards(response: LynxChanBoardsResponse): List<Board> = response.data.boards.map(::mapBoard)

    private fun mapBoard(dto: LynxChanBoard): Board =
        Board(
            id = BoardId(dto.boardUri),
            title = dto.boardName,
            description = dto.boardDescription,
            isNsfw = site.nsfwByDefault,
        )

    fun mapCatalog(
        board: BoardId,
        threads: List<LynxChanCatalogThread>,
    ): List<CatalogThread> =
        threads.map { dto ->
            val threadId = ThreadId(dto.threadId)
            CatalogThread(
                key = ThreadKey(site.providerId, board, threadId),
                originalPost = mapCatalogPost(board, threadId, dto),
                stats = mapCatalogStats(dto),
            )
        }

    private fun mapCatalogStats(dto: LynxChanCatalogThread): ThreadStats =
        ThreadStats(
            replyCount = dto.postCount,
            imageCount = dto.fileCount,
            isSticky = dto.pinned,
            isClosed = dto.locked,
            lastModifiedMillis = dto.lastBump.parseIsoOrZero(),
        )

    private fun mapCatalogPost(
        board: BoardId,
        threadId: ThreadId,
        dto: LynxChanCatalogThread,
    ): Post {
        val comment = LynxChanCommentParser.parse(dto.markdown, board.value, site.siteUrl)
        return Post(
            id = PostId(dto.threadId),
            board = board,
            threadId = threadId,
            isOriginalPost = true,
            subject = dto.subject?.takeIf { it.isNotBlank() },
            comment = comment,
            createdAtMillis = dto.lastBump.parseIsoOrZero(),
            attachments = listOfNotNull(catalogThumbAttachment(dto)).toImmutableList(),
            repliesTo = comment.quotedPosts.toImmutableList(),
            backlinks = persistentListOf(),
        )
    }

    private fun catalogThumbAttachment(dto: LynxChanCatalogThread): MediaAttachment? {
        val thumb = dto.thumb.toSafeSitePath() ?: return null
        val mime = dto.mime.orEmpty()
        val url = site.resolveSitePath(thumb)
        return MediaAttachment(
            id = thumb,
            // Catalog previews don't expose the original filename or a full-resolution source; the
            // thumbnail is the only URL available until the thread itself is opened.
            originalFileName = thumb.substringAfterLast('/'),
            extension = mime.toExtension(),
            type = mime.toMediaType(),
            sourceUrl = url,
            thumbnailUrl = url,
        )
    }

    fun mapThread(
        board: BoardId,
        response: LynxChanThreadResponse,
    ): Thread {
        val threadId = ThreadId(response.threadId)
        val op = mapOriginalPost(board, threadId, response)
        val replies = response.posts.map { mapReplyPost(board, threadId, it) }
        return Thread(
            key = ThreadKey(site.providerId, board, threadId),
            originalPost = op,
            replies = replies.toImmutableList(),
            stats =
                ThreadStats(
                    replyCount = replies.size,
                    imageCount = response.files.size + response.posts.sumOf { it.files.size },
                    uniquePosterCount = response.uniquePosters ?: 0,
                    isSticky = response.pinned,
                    isClosed = response.locked,
                    isArchived = response.archived,
                    lastModifiedMillis = response.creation.parseIsoOrZero(),
                ),
        )
    }

    private fun mapOriginalPost(
        board: BoardId,
        threadId: ThreadId,
        response: LynxChanThreadResponse,
    ): Post {
        val comment = LynxChanCommentParser.parse(response.markdown, board.value, site.siteUrl)
        return Post(
            id = PostId(response.threadId),
            board = board,
            threadId = threadId,
            isOriginalPost = true,
            subject = response.subject?.takeIf { it.isNotBlank() },
            comment = comment,
            poster =
                PosterInfo(
                    name = response.name?.takeIf { it.isNotBlank() },
                    posterId = response.id,
                    capcode = response.signedRole,
                ),
            createdAtMillis = response.creation.parseIsoOrZero(),
            attachments = response.files.mapNotNull(::mapFile).toImmutableList(),
            repliesTo = comment.quotedPosts.toImmutableList(),
            backlinks = persistentListOf(),
        )
    }

    private fun mapReplyPost(
        board: BoardId,
        threadId: ThreadId,
        dto: LynxChanPost,
    ): Post {
        val comment = LynxChanCommentParser.parse(dto.markdown, board.value, site.siteUrl)
        return Post(
            id = PostId(dto.postId),
            board = board,
            threadId = threadId,
            isOriginalPost = false,
            subject = dto.subject?.takeIf { it.isNotBlank() },
            comment = comment,
            poster =
                PosterInfo(
                    name = dto.name?.takeIf { it.isNotBlank() },
                    posterId = dto.id,
                    capcode = dto.signedRole,
                ),
            createdAtMillis = dto.creation.parseIsoOrZero(),
            attachments = dto.files.mapNotNull(::mapFile).toImmutableList(),
            repliesTo = comment.quotedPosts.toImmutableList(),
            backlinks = persistentListOf(),
        )
    }

    private fun mapFile(file: LynxChanFile): MediaAttachment? {
        val type = file.mime.toMediaType()
        val sourcePath = file.path.toSafeSitePath() ?: return null
        val thumbnailPath = (file.thumb ?: file.path).toSafeSitePath() ?: sourcePath
        return MediaAttachment(
            id = sourcePath,
            originalFileName = file.originalName ?: sourcePath.substringAfterLast('/'),
            extension =
                sourcePath
                    .substringAfterLast(
                        '.',
                        missingDelimiterValue = "",
                    ).ifBlank { file.mime.toExtension() },
            type = type,
            sourceUrl = site.resolveSitePath(sourcePath),
            thumbnailUrl = site.resolveSitePath(thumbnailPath),
            width = file.width,
            height = file.height,
            sizeBytes = file.size,
        )
    }

    private fun String?.parseIsoOrZero(): Long =
        this?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() } ?: 0L

    private fun String.toMediaType(): MediaType =
        when {
            startsWith("image/gif") -> MediaType.ANIMATED_IMAGE
            startsWith("image/") -> MediaType.IMAGE
            startsWith("video/") -> MediaType.VIDEO
            startsWith("audio/") -> MediaType.AUDIO
            else -> MediaType.UNKNOWN
        }

    private fun String.toExtension(): String =
        when (this) {
            "image/jpeg" -> "jpg"
            "image/png" -> "png"
            "image/gif" -> "gif"
            "image/webp" -> "webp"
            "video/webm" -> "webm"
            "video/mp4" -> "mp4"
            "audio/mpeg" -> "mp3"
            "audio/ogg" -> "ogg"
            else -> substringAfter('/', missingDelimiterValue = "bin")
        }

    private fun String?.toSafeSitePath(): String? {
        val path = this?.trim().orEmpty()
        val isSafe =
            path.isNotEmpty() &&
                path.startsWith('/') &&
                !path.startsWith("//") &&
                path.none { it.isISOControl() || it.isWhitespace() } &&
                !path.contains("\\") &&
                path.split('/').none { it == ".." }
        return path.takeIf { isSafe }
    }

    private fun LynxChanSite.resolveSitePath(path: String): String = siteUrl.trimEnd('/') + path
}
