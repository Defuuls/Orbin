package com.orbin.ios

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import com.orbin.core.model.Board
import com.orbin.core.model.MediaAttachment
import com.orbin.core.model.MediaLibraryRecord
import com.orbin.core.model.MediaType
import com.orbin.core.model.PlaybackScope
import com.orbin.core.model.isCoveredInLibrary
import com.orbin.core.model.isVisibleInLibrary
import com.orbin.ios.resources.Res
import com.orbin.ios.resources.ios_saved_cancel
import com.orbin.ios.resources.ios_saved_continue
import com.orbin.ios.resources.ios_saved_delete
import com.orbin.ios.resources.ios_saved_export_confirm
import com.orbin.ios.resources.ios_saved_export_title
import com.orbin.ios.resources.ios_saved_prepare
import com.orbin.ios.resources.ios_saved_unavailable
import com.orbin.uinext.SavedMediaItem
import com.orbin.uinext.SavedMediaKind
import com.orbin.uinext.SavedMediaLibraryScreen
import com.orbin.uinext.SavedMediaOperation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Owned originals are opened and shared from protected staging, never re-fetched by URL. */
@Composable
internal fun SavedMediaLibraryContent(
    browser: Browser,
    downloads: MediaDownloads,
) {
    val repository = downloads.mediaLibrary
    val files = downloads.managedMediaFiles
    if (repository == null || files == null) {
        Text(stringResource(Res.string.ios_saved_unavailable))
        return
    }
    val records by remember(repository) { repository.observeAll() }.collectAsState(emptyList())
    val settings by browser.settings.current.collectAsState()
    val directory by browser.boardDirectory.collectAsState()
    var boards by remember { mutableStateOf<Map<Pair<String, String>, Board?>>(emptyMap()) }
    LaunchedEffect(records, directory) {
        boards =
            records
                .mapNotNull { record ->
                    record.boardId?.let { board ->
                        (record.providerId to board) to
                            downloads.resolveSavedBoard(record.providerId, board)
                    }
                }.toMap()
    }
    val visible =
        records.filter {
            it.isVisibleInLibrary(
                settings,
                it.boardId?.let { id ->
                    boards[it.providerId to id]
                },
            )
        }
    val byId = visible.associateBy { it.id }
    var selection by remember { mutableStateOf<Set<String>>(emptySet()) }
    var operation by remember { mutableStateOf<SavedMediaOperation?>(null) }
    var opened by remember { mutableStateOf<MediaLibraryRecord?>(null) }
    var previewPath by remember { mutableStateOf<String?>(null) }
    var pendingExport by remember { mutableStateOf<List<String>?>(null) }
    val scope = rememberCoroutineScope()
    val staging = remember(files) { Semaphore(2) }
    val preparing = stringResource(Res.string.ios_saved_prepare)
    val deleting = stringResource(Res.string.ios_saved_delete)
    LaunchedEffect(byId.keys) {
        if (opened?.id?.let { it !in byId } == true) opened = null
        selection = selection.intersect(byId.keys)
    }
    LaunchedEffect(opened?.id, opened?.storageKey) {
        val record = opened ?: return@LaunchedEffect
        var staged: String? = null
        try {
            staged = staging.withPermit { stageSavedMedia(files, record) }
            previewPath = staged
            awaitCancellation()
        } catch (failure: CancellationException) {
            throw failure
        } catch (
            // Platform file/export boundaries report per-item failures without swallowing cancellation.
            @Suppress("TooGenericExceptionCaught") failure: Exception,
        ) {
            operation = SavedMediaOperation(preparing, 0, 1, failure.message, running = false)
            opened = null
        } finally {
            previewPath = null
            withContext(NonCancellable) { staged?.let { releaseSavedMedia(it) } }
        }
    }

    suspend fun resolveAllowed(id: String): MediaLibraryRecord? {
        val record = repository.get(id) ?: return null
        val board = record.boardId?.let { downloads.resolveSavedBoard(record.providerId, it) }
        return record.takeIf { it.isVisibleInLibrary(browser.settings.current.value, board) }
    }

    fun summary(result: SavedMediaBatchResult): String? =
        result.failures
            .takeIf { it.isNotEmpty() }
            ?.let { "${it.size} failed · ${it.first()}" }

    fun export(
        ids: List<String>,
        sharing: Boolean,
    ) {
        if (operation?.running == true) return
        scope.launch {
            val staged = mutableListOf<String>()
            operation = SavedMediaOperation(preparing, 0, ids.distinct().size)
            try {
                val result =
                    runSavedMediaBatch(ids, ::resolveAllowed, { completed, total ->
                        operation = SavedMediaOperation(preparing, completed, total)
                    }) { record ->
                        val path = staging.withPermit { stageSavedMedia(files, record) }
                        staged += path
                        if (!sharing) exportSavedMedia(path, record)
                    }
                if (sharing && staged.isNotEmpty()) {
                    suspendCancellableCoroutine<Unit> { continuation ->
                        val presented =
                            shareSavedMedia(staged.toList()) {
                                if (continuation.isActive) continuation.resume(Unit)
                            }
                        if (!presented && continuation.isActive) {
                            continuation.resumeWithException(IllegalStateException("Could not present the share sheet"))
                        }
                    }
                }
                operation =
                    SavedMediaOperation(preparing, result.completed, result.total, summary(result), running = false)
            } catch (failure: CancellationException) {
                throw failure
            } catch (
                // Platform file/export boundaries report per-item failures without swallowing cancellation.
                @Suppress("TooGenericExceptionCaught") failure: Exception,
            ) {
                operation = SavedMediaOperation(preparing, 0, ids.distinct().size, failure.message, running = false)
            } finally {
                withContext(NonCancellable) { staged.forEach { releaseSavedMedia(it) } }
            }
        }
    }

    SavedMediaLibraryScreen(
        items =
            visible.map { record ->
                SavedMediaItem(
                    record.id,
                    record.fileName,
                    listOfNotNull(record.providerId, record.boardId?.let { "/$it/" }).joinToString(" · "),
                    when (record.mediaType) {
                        MediaType.IMAGE, MediaType.ANIMATED_IMAGE -> SavedMediaKind.IMAGE
                        MediaType.VIDEO -> SavedMediaKind.VIDEO
                        else -> SavedMediaKind.OTHER
                    },
                    record.byteCount,
                    record.savedAtMillis,
                    record.width,
                    record.height,
                    covered =
                        record.isCoveredInLibrary(
                            settings,
                            record.boardId?.let { boards[record.providerId to it] },
                        ),
                )
            },
        selectedIds = selection,
        onSelectionChange = { selection = it },
        onOpen = { item -> opened = byId[item.id] },
        onShare = { export(it, sharing = true) },
        onExport = { ids -> if (settings.askBeforePhotos) pendingExport = ids else export(ids, sharing = false) },
        onDelete = { ids ->
            if (operation?.running != true) {
                scope.launch {
                    operation = SavedMediaOperation(deleting, 0, ids.distinct().size)
                    val result =
                        runSavedMediaBatch(ids, ::resolveAllowed, { completed, total ->
                            operation = SavedMediaOperation(deleting, completed, total)
                        }) { record -> deleteSavedMediaRecord(repository, files, record) }
                    selection = selection.intersect(repository.observeAll().first().mapTo(mutableSetOf()) { it.id })
                    operation =
                        SavedMediaOperation(deleting, result.completed, result.total, summary(result), running = false)
                }
            }
        },
        operation = operation,
        thumbnail = { item, modifier ->
            byId[item.id]?.let { record ->
                var path by remember(record.id, record.storageKey) { mutableStateOf<String?>(null) }
                LaunchedEffect(record.id, record.storageKey) {
                    var staged: String? = null
                    try {
                        staged =
                            staging.withPermit {
                                stageSavedMedia(
                                    files,
                                    record.thumbnailStorageKey?.let { thumbnail ->
                                        record.copy(storageKey = thumbnail, fileName = "thumbnail.jpg")
                                    } ?: record,
                                )
                            }
                        path = staged
                        awaitCancellation()
                    } catch (failure: CancellationException) {
                        throw failure
                    } catch (_: Exception) {
                        // A restored record can precede its cloud asset; keep a local placeholder.
                    } finally {
                        path = null
                        withContext(NonCancellable) { staged?.let { releaseSavedMedia(it) } }
                    }
                }
                path?.let { staged ->
                    if (record.mediaType == MediaType.VIDEO &&
                        record.thumbnailStorageKey == null
                    ) {
                        NativeVideoFrame(savedMediaFileUrl(staged), modifier)
                    } else {
                        AsyncImage(
                            savedMediaFileUrl(staged),
                            record.fileName,
                            modifier,
                            contentScale = ContentScale.Crop,
                        )
                    }
                }
            }
        },
    )
    pendingExport?.let { ids ->
        AlertDialog(
            onDismissRequest = { pendingExport = null },
            title = { Text(stringResource(Res.string.ios_saved_export_title)) },
            text = { Text(stringResource(Res.string.ios_saved_export_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    pendingExport = null
                    export(ids, false)
                }) { Text(stringResource(Res.string.ios_saved_continue)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingExport = null }) { Text(stringResource(Res.string.ios_saved_cancel)) }
            },
        )
    }
    val record = opened?.takeIf { it.id in byId }
    val path = previewPath
    if (record != null && path != null) {
        val url = savedMediaFileUrl(path)
        val file =
            MediaAttachment(
                record.attachmentId,
                record.fileName,
                record.fileName.substringAfterLast('.', ""),
                record.mediaType,
                url,
                url,
                width = record.width,
                height = record.height,
                sizeBytes = record.byteCount,
                isSpoiler =
                    record.isCoveredInLibrary(
                        settings,
                        record.boardId?.let { boards[record.providerId to it] },
                    ),
            )
        Dialog(onDismissRequest = { opened = null }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Box(Modifier.fillMaxSize()) {
                MediaViewer(
                    listOf(file),
                    0,
                    onClose = { opened = null },
                    trustedLocalFiles = setOf(url),
                    playback =
                        settings.playbackPreferences.forScope(
                            PlaybackScope(record.providerId, record.boardId.orEmpty()),
                        ),
                )
            }
        }
    }
}
