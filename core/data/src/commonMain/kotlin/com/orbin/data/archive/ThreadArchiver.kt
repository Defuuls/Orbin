package com.orbin.data.archive

import com.orbin.core.model.Thread
import com.orbin.data.repository.downloadRequestHeaders
import com.orbin.domain.repository.ThreadArchive
import com.orbin.domain.repository.ThreadArchiveRepository
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.statement.bodyAsBytes
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import okio.FileSystem
import okio.Path.Companion.toPath
import okio.SYSTEM
import okio.buffer
import kotlin.time.Clock

/**
 * [ThreadArchiveRepository] over the app's HTTP client, shared by Android and iOS. Files are
 * fetched one at a time and written straight into the zip, so memory holds one file at most.
 */
class ThreadArchiver(
    private val client: HttpClient,
    private val dispatcher: CoroutineDispatcher,
    private val fileSystem: FileSystem = FileSystem.SYSTEM,
    private val now: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) : ThreadArchiveRepository {
    override suspend fun archive(
        thread: Thread,
        directory: String?,
        onProgress: (done: Int, total: Int) -> Unit,
    ): ThreadArchive =
        withContext(dispatcher) {
            val folder = directory?.toPath() ?: FileSystem.SYSTEM_TEMPORARY_DIRECTORY
            fileSystem.createDirectories(folder)
            val root = archiveRoot(thread)
            val target = folder / "$root.zip"
            val attachments = thread.allPosts.flatMap { it.attachments }.distinctBy { it.id }
            val saved = mutableMapOf<String, SavedFiles>()
            onProgress(0, attachments.size)
            fileSystem.sink(target).buffer().use { sink ->
                val zip = ZipWriter(sink)
                attachments.forEachIndexed { index, file ->
                    val fullName = "media/${safeName(file.id)}${extensionOf(file.extension, file.sourceUrl)}"
                    val full =
                        fetch(file.sourceUrl)?.let {
                            zip.add("$root/$fullName", it)
                            fullName
                        }
                    val thumbName = "thumbs/${safeName(file.id)}${extensionOf("", file.thumbnailUrl)}"
                    val thumbnail =
                        if (file.thumbnailUrl.isBlank() || file.thumbnailUrl == file.sourceUrl) {
                            null
                        } else {
                            fetch(file.thumbnailUrl)?.let {
                                zip.add("$root/$thumbName", it)
                                thumbName
                            }
                        }
                    saved[file.id] = SavedFiles(full, thumbnail)
                    onProgress(index + 1, attachments.size)
                }
                zip.add("$root/style.css", ARCHIVE_CSS.encodeToByteArray())
                zip.add(
                    "$root/index.html",
                    threadPage(thread, saved, sourceUrl = null, savedAtMillis = now()).encodeToByteArray(),
                )
                zip.finish()
            }
            ThreadArchive(
                path = target.toString(),
                fileName = target.name,
                savedMedia = saved.values.count { it.full != null },
                totalMedia = attachments.size,
            )
        }

    /** The file at [url], or null when it isn't https or can't be fetched. */
    private suspend fun fetch(url: String): ByteArray? {
        if (!url.startsWith("https://", ignoreCase = true)) return null
        return try {
            val response =
                client.get(url) {
                    headers {
                        downloadRequestHeaders(url).forEach { (k, v) ->
                            append(k, v)
                        }
                    }
                }
            if (response.status.isSuccess()) response.bodyAsBytes() else null
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (
            // Ktor and the engines throw their own unrelated types; any of them is a file not saved.
            @Suppress("TooGenericExceptionCaught") _: Exception,
        ) {
            null
        }
    }
}

/** The archive's top folder and file name: "orbin-g-123456", from the board and thread. */
internal fun archiveRoot(thread: Thread): String =
    "orbin-${safeName(thread.key.board.value)}-${thread.key.thread.value}"

/** [raw] reduced to letters, digits, '-' and '_', so no name can leave its folder. */
internal fun safeName(raw: String): String =
    raw
        .map {
            if (it.isLetterOrDigit() ||
                it == '-' ||
                it == '_'
            ) {
                it
            } else {
                '_'
            }
        }.joinToString("")
        .ifEmpty { "file" }

/** ".jpg" from [extension], or from [url]'s path when the extension isn't known. */
internal fun extensionOf(
    extension: String,
    url: String,
): String {
    val raw =
        extension.trimStart('.').ifBlank {
            url.substringBefore('?').substringAfterLast('/').substringAfterLast('.', "")
        }
    val clean = safeName(raw).take(MAX_EXTENSION)
    return if (raw.isBlank()) "" else ".${clean.lowercase()}"
}

private const val MAX_EXTENSION = 8
