package com.orbin.ios

import com.orbin.core.model.DownloadRecord
import com.orbin.core.model.DownloadStatus
import com.orbin.core.model.MediaAttachment
import com.orbin.core.model.PermanentContentFilter
import com.orbin.core.model.ThreadKey
import com.orbin.data.database.dao.DownloadDao
import com.orbin.data.database.entity.DownloadEntity
import io.ktor.client.HttpClient
import io.ktor.client.plugins.onDownload
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsBytes
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Clock

/** Where a saved file lands on the device. */
enum class SaveTarget {
    /** The Photos library, as a photo (still or animated). */
    PHOTO,

    /** The Photos library, as a video. */
    VIDEO,

    /** The app's folder in the Files app: everything Photos does not take, WebM above all. */
    FILE,
}

/**
 * The platform half of saving. iOS puts photos and videos in the Photos library and the rest in
 * the app's folder in Files ([folder] is the subfolder there); it throws when it cannot.
 */
fun interface MediaStore {
    suspend fun save(
        bytes: ByteArray,
        fileName: String,
        folder: String,
        target: SaveTarget,
    )
}

/** Fetches [url]'s body, reporting bytes received and the total when known; throws on failure. */
typealias MediaFetch = suspend (url: String, onProgress: (received: Long, total: Long?) -> Unit) -> ByteArray

/**
 * Saving files from threads, and the list the Downloads tab shows: the iOS counterpart of Android's
 * `DownloadRepositoryImpl`, over the same `downloads` table in the shared database.
 *
 * Android hands a file to the system download manager; iOS has none, so the file is fetched here
 * through the app's own client and handed to [store]. The rules are Android's: https only, nothing
 * the permanent filter catches is saved, and a remote file name can never leave its folder.
 */
class MediaDownloads(
    private val dao: DownloadDao,
    private val store: MediaStore,
    private val fetch: MediaFetch,
    private val scope: CoroutineScope,
    private val now: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) {
    // Bytes so far of the saves in flight, by record id.
    private val progress = MutableStateFlow<Map<Long, Pair<Long, Long?>>>(emptyMap())

    /** Every save, most recent first, with live progress for the ones still running. */
    val records: StateFlow<List<DownloadRecord>> =
        combine(dao.observeAll(), progress) { entities, live -> entities.map { it.toRecord(live[it.id]) } }
            .stateIn(scope, SharingStarted.Eagerly, emptyList())

    private var lastId = 0L

    init {
        // A save still running when the app last closed never finished: offer it for retry.
        scope.launch {
            dao.all().filter { it.status in UNFINISHED }.forEach { dao.updateStatus(it.id, DownloadStatus.FAILED.name) }
        }
    }

    /**
     * Saves [file] from the thread [thread] (titled [threadTitle]). Returns false, saving nothing,
     * when the file may not be saved: not https, or caught by the permanent filter.
     */
    fun save(
        file: MediaAttachment,
        thread: ThreadKey,
        threadTitle: String?,
    ): Boolean {
        val url = safeExternalLink(file.sourceUrl) ?: return false
        val name = downloadFileName(file)
        if (PermanentContentFilter.matchesAny(listOf(name, file.originalFileName, threadTitle))) return false
        val id = nextId()
        val folder = downloadFolder(thread.board.value, thread.thread.value, threadTitle)
        scope.launch {
            dao.upsert(
                DownloadEntity(
                    id = id,
                    url = url,
                    fileName = name,
                    status = DownloadStatus.RUNNING.name,
                    createdAtMillis = now(),
                    relativeDir = folder,
                ),
            )
            transfer(id, url, name, folder)
        }
        return true
    }

    /** Tries a failed save again, into the folder it was first meant for. */
    fun retry(id: Long) {
        scope.launch {
            val entry = dao.getById(id) ?: return@launch
            dao.updateStatus(id, DownloadStatus.RUNNING.name)
            transfer(id, entry.url, entry.fileName, entry.relativeDir)
        }
    }

    /** Forgets the list. The saved files stay where they were saved. */
    fun clear() {
        scope.launch { dao.clear() }
    }

    private suspend fun transfer(
        id: Long,
        url: String,
        name: String,
        folder: String,
    ) {
        val saved =
            runCatching {
                val bytes = fetch(url) { received, total -> progress.update { it + (id to (received to total)) } }
                store.save(bytes, name, folder, saveTargetOf(name))
            }.onFailure { if (it is CancellationException) throw it }
        progress.update { it - id }
        dao.updateStatus(id, if (saved.isSuccess) DownloadStatus.COMPLETED.name else DownloadStatus.FAILED.name)
    }

    // Negative, like Android's direct transfers, and never repeated even within a millisecond.
    private fun nextId(): Long {
        lastId = minOf(-now(), lastId - 1)
        return lastId
    }
}

/** Fetching through the app's own client, so a save sends the same headers as the app's reads. */
fun ktorMediaFetch(client: HttpClient): MediaFetch =
    { url, onProgress ->
        val response = client.get(url) { onDownload { received, total -> onProgress(received, total) } }
        check(response.status.isSuccess()) { "HTTP ${response.status.value}" }
        response.bodyAsBytes()
    }

/** Photos takes stills, GIFs and the videos iOS plays; everything else goes to Files. */
internal fun saveTargetOf(fileName: String): SaveTarget =
    when (fileName.substringAfterLast('.', "").lowercase()) {
        "jpg", "jpeg", "png", "gif", "heic" -> SaveTarget.PHOTO
        "mp4", "m4v", "mov" -> SaveTarget.VIDEO
        else -> SaveTarget.FILE
    }

/**
 * A safe basename for [file]: its uploaded name (with its extension), never a path, a traversal or
 * a control character. The same cleanup as Android's `sanitizeFileName`.
 */
internal fun downloadFileName(file: MediaAttachment): String {
    val uploaded = file.originalFileName.ifBlank { file.id }
    val withExtension =
        if (file.extension.isNotBlank() &&
            !uploaded.contains('.')
        ) {
            "$uploaded.${file.extension.trimStart('.')}"
        } else {
            uploaded
        }
    val cleaned =
        withExtension
            .substringAfterLast('/')
            .substringAfterLast('\\')
            .filterNot { it.isISOControl() }
            .replace(UNSAFE_PATH_CHARS, "_")
            .replace("..", "_")
            .trim(' ', '.')
            .takeLast(MAX_FILE_NAME_LENGTH)
    return cleaned.ifBlank { "download" }
}

/** "g/123 - Title/": the board, then the thread, as Android organises its downloads by default. */
internal fun downloadFolder(
    board: String,
    thread: Long,
    threadTitle: String?,
): String {
    val title = threadTitle?.trim().orEmpty()
    return pathSegment(board) + "/" + pathSegment(if (title.isNotBlank()) "$thread - $title" else "$thread") + "/"
}

private fun pathSegment(raw: String): String =
    raw
        .filterNot { it.isISOControl() }
        .replace(UNSAFE_PATH_CHARS, "_")
        .replace("..", "_")
        .trim(' ', '.')
        .take(MAX_PATH_SEGMENT_LENGTH)
        .ifBlank { "misc" }

private fun DownloadEntity.toRecord(live: Pair<Long, Long?>?): DownloadRecord =
    DownloadRecord(
        id = id,
        url = url,
        fileName = fileName,
        status = DownloadStatus.entries.firstOrNull { it.name == status } ?: DownloadStatus.FAILED,
        createdAtMillis = createdAtMillis,
        downloadedBytes = live?.first ?: 0L,
        totalBytes = live?.second,
    )

private val UNFINISHED = setOf(DownloadStatus.QUEUED.name, DownloadStatus.RUNNING.name)
private val UNSAFE_PATH_CHARS = Regex("""[/\\:*?"<>|]""")
private const val MAX_FILE_NAME_LENGTH = 200
private const val MAX_PATH_SEGMENT_LENGTH = 80
