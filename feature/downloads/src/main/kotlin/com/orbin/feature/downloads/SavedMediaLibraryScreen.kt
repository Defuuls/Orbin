package com.orbin.feature.downloads

import android.content.ClipData
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.orbin.core.model.MediaLibraryRecord
import com.orbin.core.model.MediaType
import com.orbin.core.model.PlaybackScope
import com.orbin.core.model.PlaybackSettings
import com.orbin.media.image.OrbinAsyncImage
import com.orbin.media.image.ZoomableImage
import com.orbin.media.video.VideoPlayer
import com.orbin.uinext.NextConfirmDialog
import com.orbin.uinext.ReaderMediaClose
import com.orbin.uinext.ReaderMediaTitle
import com.orbin.uinext.ReaderMediaToolbar
import com.orbin.uinext.SavedMediaItem
import com.orbin.uinext.SavedMediaKind
import com.orbin.uinext.SavedMediaLibraryScreen
import java.io.File

/** Android's owned-file adapter. It never falls back to a saved record's remote source URL. */
@Composable
internal fun SavedMediaLibraryContent(viewModel: SavedMediaLibraryViewModel = hiltViewModel()) {
    val entries by viewModel.records.collectAsStateWithLifecycle()
    val operation by viewModel.operation.collectAsStateWithLifecycle()
    val preferences by viewModel.preferences.collectAsStateWithLifecycle()
    val localFile by viewModel.openFile.collectAsStateWithLifecycle()
    val recordsById = remember(entries) { entries.associateBy { it.record.id } }
    val items = remember(entries) { entries.map { it.record.toPresentation(it.covered) } }
    var selected by remember { mutableStateOf(emptySet<String>()) }
    var pendingExport by remember { mutableStateOf<List<String>?>(null) }
    val context = LocalContext.current
    LaunchedEffect(viewModel, context) {
        viewModel.shares.collect { share ->
            try {
                val uris =
                    ArrayList(
                        share.files.map {
                            FileProvider.getUriForFile(
                                context,
                                "${context.packageName}.fileprovider",
                                it,
                            )
                        },
                    )
                val clip = ClipData.newRawUri("Saved media", uris.first())
                uris.drop(1).forEach { clip.addItem(ClipData.Item(it)) }
                val send =
                    Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                        type = share.mimeType
                        putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                        clipData = clip
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                context.startActivity(Intent.createChooser(send, "Share saved media"))
            } catch (_: Exception) {
                viewModel.shareFailed()
            }
        }
    }
    LaunchedEffect(recordsById, localFile) {
        if (localFile?.record?.id?.let { it !in recordsById } == true) viewModel.close()
    }
    DisposableEffect(viewModel) { onDispose { viewModel.close() } }
    SavedMediaLibraryScreen(
        items = items,
        selectedIds = selected,
        onSelectionChange = { selected = it },
        onOpen = { viewModel.open(it.id) },
        onShare = viewModel::share,
        onExport = { ids -> if (preferences.askBeforePhotos) pendingExport = ids else viewModel.export(ids) },
        onDelete = viewModel::delete,
        operation = operation,
        thumbnail = { item, modifier ->
            recordsById[item.id]?.let { entry -> LocalLibraryThumbnail(entry.record, viewModel, modifier) }
        },
    )
    pendingExport?.let { ids ->
        NextConfirmDialog(
            title = stringResource(R.string.downloads_export_title),
            message = stringResource(R.string.downloads_export_confirmation),
            confirmLabel = stringResource(R.string.downloads_export_confirm),
            destructive = false,
            onDismiss = { pendingExport = null },
            onConfirm = {
                pendingExport = null
                viewModel.export(ids)
            },
        )
    }
    localFile?.let { saved ->
        val record = saved.record
        val playback =
            preferences.playbackPreferences.forScope(
                if (record.providerId.isEmpty()) {
                    PlaybackScope()
                } else {
                    PlaybackScope(
                        record.providerId,
                        record.boardId.orEmpty(),
                    )
                },
            )
        LocalSavedMediaViewer(saved, playback, viewModel::close)
    }
}

@Composable
private fun LocalLibraryThumbnail(
    record: MediaLibraryRecord,
    viewModel: SavedMediaLibraryViewModel,
    modifier: Modifier,
) {
    val preview by produceState<File?>(null, record) { value = viewModel.preview(record) }
    Box(modifier, contentAlignment = Alignment.Center) {
        preview?.let { OrbinAsyncImage(it.toURI().toString(), null, Modifier.fillMaxSize()) }
            ?: Text(if (record.mediaType == MediaType.VIDEO) "Video" else "Image")
    }
}

@Composable
private fun LocalSavedMediaViewer(
    saved: SavedMediaLocalFile,
    playback: PlaybackSettings,
    onClose: () -> Unit,
) {
    val uri = remember(saved.file) { saved.file.toURI().toString() }
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            when (saved.record.mediaType) {
                MediaType.VIDEO, MediaType.AUDIO ->
                    VideoPlayer(
                        uri,
                        Modifier.fillMaxSize(),
                        autoPlay = playback.autoplay,
                        muted = playback.startMuted,
                        loop = playback.loopVideos,
                    )
                MediaType.IMAGE, MediaType.ANIMATED_IMAGE ->
                    ZoomableImage(
                        uri,
                        saved.record.fileName,
                        Modifier.fillMaxSize(),
                    )
                MediaType.UNKNOWN ->
                    Text(
                        "This saved file cannot be displayed. Use Share to open it in another app.",
                        color = Color.White,
                        modifier = Modifier.align(Alignment.Center).padding(24.dp),
                    )
            }
            ReaderMediaToolbar(Modifier.align(Alignment.TopCenter)) {
                ReaderMediaClose("Close saved media", onClose)
                ReaderMediaTitle(saved.record.fileName, "Saved on this device", Modifier.weight(1f))
            }
        }
    }
}

private fun MediaLibraryRecord.toPresentation(covered: Boolean) =
    SavedMediaItem(
        id = id,
        fileName = fileName,
        source = listOf(providerId, boardId?.let { "/$it/" }).filterNotNull().joinToString(" · "),
        kind =
            when (mediaType) {
                MediaType.IMAGE, MediaType.ANIMATED_IMAGE -> SavedMediaKind.IMAGE
                MediaType.VIDEO -> SavedMediaKind.VIDEO
                else -> SavedMediaKind.OTHER
            },
        byteCount = byteCount,
        savedAtMillis = savedAtMillis,
        width = width,
        height = height,
        covered = covered,
    )
