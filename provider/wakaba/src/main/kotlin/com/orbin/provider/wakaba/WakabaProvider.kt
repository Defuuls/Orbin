package com.orbin.provider.wakaba

import com.orbin.core.model.Board
import com.orbin.core.model.BoardId
import com.orbin.core.model.CatalogRequest
import com.orbin.core.model.CatalogSort
import com.orbin.core.model.CatalogThread
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
import com.orbin.provider.api.EngineKind
import com.orbin.provider.api.ImageBoardProvider
import com.orbin.provider.api.ProviderCapabilities
import com.orbin.provider.api.ProviderException
import com.orbin.provider.api.ProviderMetadata
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.io.IOException
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

class WakabaProvider(
    private val client: OkHttpClient,
    private val ioDispatcher: CoroutineDispatcher,
) : ImageBoardProvider {
    override val metadata =
        ProviderMetadata(
            id = ID,
            displayName = "Tranchan",
            baseUrl = BASE_URL,
            description = "Tranchan (Wakaba++, read-only)",
            engine = EngineKind.WAKABA,
            isNsfwByDefault = true,
        )
    override val capabilities =
        ProviderCapabilities(
            supportsSearch = false,
            supportsArchive = false,
            supportsCatalogPaging = true,
            supportedSortOptions = setOf(CatalogSort.BUMP_ORDER, CatalogSort.CREATION_DATE, CatalogSort.REPLY_COUNT),
        )

    override suspend fun getBoards() = BOARDS

    override suspend fun getCatalog(request: CatalogRequest): List<CatalogThread> =
        call {
            val suffix = if (request.page <= 0) "" else "${request.page}.php"
            val doc = Jsoup.parse(fetch("$BASE_URL/${request.board.value}/$suffix"), BASE_URL)
            doc.select("div.op-post").mapNotNull { op ->
                val id =
                    op
                        .selectFirst("a.reflink")
                        ?.text()
                        ?.filter(Char::isDigit)
                        ?.toLongOrNull() ?: return@mapNotNull null
                val threadId = ThreadId(id)
                val post = mapPost(op, request.board, threadId, true)
                val omitted = op.selectFirst("p.omittedposts")?.text().orEmpty()
                CatalogThread(
                    key = ThreadKey(ID, request.board, threadId),
                    originalPost = post,
                    stats =
                        ThreadStats(
                            replyCount =
                                Regex("(\\d+) posts?")
                                    .find(omitted)
                                    ?.groupValues
                                    ?.get(1)
                                    ?.toIntOrNull() ?: 0,
                            imageCount =
                                Regex("(\\d+) images?")
                                    .find(omitted)
                                    ?.groupValues
                                    ?.get(1)
                                    ?.toIntOrNull() ?: 0,
                            lastModifiedMillis = post.createdAtMillis,
                        ),
                )
            }
        }

    override suspend fun getThread(
        board: BoardId,
        thread: ThreadId,
    ): Thread =
        call {
            val doc = Jsoup.parse(fetch("$BASE_URL/${board.value}/res/${thread.value}.php"), BASE_URL)
            val opElement = doc.selectFirst("div.op-post") ?: throw ProviderException.NotFound("Thread not found")
            val op = mapPost(opElement, board, thread, true)
            val replies = doc.select("div.reply-post").map { mapPost(it, board, thread, false) }
            Thread(
                key = ThreadKey(ID, board, thread),
                originalPost = op,
                replies = replies.toImmutableList(),
                stats =
                    ThreadStats(
                        replyCount = replies.size,
                        imageCount = op.attachments.size + replies.sumOf { it.attachments.size },
                        lastModifiedMillis = replies.lastOrNull()?.createdAtMillis ?: op.createdAtMillis,
                    ),
            )
        }

    private fun mapPost(
        element: Element,
        board: BoardId,
        thread: ThreadId,
        isOp: Boolean,
    ): Post {
        val id =
            element
                .selectFirst("a.reflink")
                ?.text()
                ?.filter(Char::isDigit)
                ?.toLongOrNull()
                ?: element
                    .selectFirst("[id^=reply]")
                    ?.id()
                    ?.removePrefix("reply")
                    ?.toLongOrNull()
                ?: thread.value
        val commentElement = element.selectFirst("div.comment")
        val text =
            commentElement
                ?.text()
                .orEmpty()
                .replace(
                    Regex("\\d+ posts?( and \\d+ images?)? omitted.*$"),
                    "",
                ).trim()
        val attachment =
            element.selectFirst("a.thumb")?.let { thumb ->
                val source = thumb.absUrl("href").takeIf(String::isNotBlank) ?: return@let null
                val thumbnail =
                    thumb
                        .selectFirst("img")
                        ?.absUrl("src")
                        .orEmpty()
                        .ifBlank { source }
                val name = source.substringAfterLast('/')
                val ext = name.substringAfterLast('.', "bin").lowercase()
                MediaAttachment(
                    id = source,
                    originalFileName = name,
                    extension = ext,
                    type =
                        when (ext) {
                            "gif" -> MediaType.ANIMATED_IMAGE
                            "webm", "mp4" -> MediaType.VIDEO
                            "jpg", "jpeg", "png", "webp" -> MediaType.IMAGE
                            else -> MediaType.UNKNOWN
                        },
                    sourceUrl = source,
                    thumbnailUrl = thumbnail,
                )
            }
        return Post(
            id = PostId(id),
            board = board,
            threadId = thread,
            isOriginalPost = isOp,
            comment =
                if (text.isBlank()) {
                    PostComment.Empty
                } else {
                    PostComment(
                        commentElement?.html().orEmpty(),
                        listOf(PostNode.Text(text)).toImmutableList(),
                    )
                },
            poster = PosterInfo(name = element.selectFirst("span.postername")?.text()?.takeIf(String::isNotBlank)),
            createdAtMillis = parseDate(element.selectFirst("span.thedate")?.text()),
            attachments = listOfNotNull(attachment).toImmutableList(),
        )
    }

    private fun fetch(url: String): String {
        val response =
            client
                .newCall(
                    Request
                        .Builder()
                        .url(url)
                        .get()
                        .build(),
                ).execute()
        response.use {
            if (it.code == HTTP_NOT_FOUND) throw ProviderException.NotFound("Resource not found")
            if (!it.isSuccessful) throw ProviderException.Http(it.code, it.message)
            return requireResponseBody(it.body?.string())
        }
    }

    private fun requireResponseBody(body: String?): String = body ?: throw ProviderException.Parse("Empty response")

    private suspend fun <T> call(block: () -> T): T =
        withContext(ioDispatcher) {
            try {
                block()
            } catch (e: ProviderException) {
                throw e
            } catch (e: IOException) {
                throw ProviderException.Network("Network error: ${e.message}", e)
            } catch (e: IllegalArgumentException) {
                throw ProviderException.Parse("Failed to parse Tranchan", e)
            }
        }

    private fun parseDate(value: String?): Long =
        runCatching {
            LocalDateTime
                .parse(value?.trim('[', ']'), DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
                .toInstant(ZoneOffset.UTC)
                .toEpochMilli()
        }.getOrDefault(0L)

    private companion object {
        const val HTTP_NOT_FOUND = 404
        val ID = ProviderId("tranchan")
        const val BASE_URL = "https://www.tranchan.net"
        val BOARDS =
            listOf(
                Board(BoardId("cam/cross-dressing"), "Cross-dressing", isNsfw = true),
                Board(BoardId("cam/trap"), "Traps", isNsfw = true),
                Board(BoardId("cam/trans"), "Transsexual and Transgender", isNsfw = true),
                Board(BoardId("cam/whores"), "Camwhores / General", isNsfw = true),
                Board(BoardId("porn/general"), "Porn / General", isNsfw = true),
            )
    }
}
