package com.orbin.feature.downloads

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.orbin.core.model.DownloadRecord
import com.orbin.core.model.DownloadStatus
import com.orbin.uinext.DownloadRow
import com.orbin.uinext.DownloadTags
import com.orbin.uinext.NextConfirmDialog
import com.orbin.uinext.NextDestination
import com.orbin.uinext.NextTheme
import com.orbin.uinext.ReaderEmptyState
import com.orbin.uinext.ReaderMenuItem
import com.orbin.uinext.ReaderScaffold
import com.orbin.uinext.tokens.ReaderSpace

/**
 * Download history on the Next language. Transfer + notifications stay with the platform download
 * manager; this screen only lists what Orbin asked for.
 *
 * Back returns to the previous screen; Clear is available from the shared app bar's overflow.
 * A supplied [bottomBar] owns its system-bottom safe inset.
 */
@Composable
fun DownloadsScreen(
    onBack: (() -> Unit)? = null,
    onDestination: ((NextDestination) -> Unit)? = null,
    viewModel: DownloadsViewModel = hiltViewModel(),
    bottomBar: (@Composable () -> Unit)? = null,
) {
    val downloads by viewModel.downloads.collectAsStateWithLifecycle()
    var showClearDialog by remember { mutableStateOf(false) }
    val clearLabel = stringResource(R.string.downloads_clear_action)
    val title = stringResource(R.string.downloads_title)

    NextTheme {
        Column(Modifier.fillMaxSize()) {
            ReaderScaffold(
                title = title,
                modifier = Modifier.weight(1f),
                destination = NextDestination.DOWNLOADS,
                onDestination = onDestination,
                onBack = onBack,
                overflow =
                    if (downloads.isEmpty()) {
                        emptyList()
                    } else {
                        listOf(
                            ReaderMenuItem(clearLabel) { showClearDialog = true },
                        )
                    },
            ) { bottomPad ->
                DownloadsContent(
                    downloads = downloads,
                    // A supplied navigation bar consumes its own system-bottom safe inset.
                    bottomPad = if (bottomBar == null) bottomPad else PaddingValues(),
                    onRetry = { viewModel.retry(it) },
                )
            }
            bottomBar?.invoke()
        }
    }

    if (showClearDialog) {
        NextConfirmDialog(
            title = stringResource(R.string.downloads_clear_dialog_title),
            message = stringResource(R.string.downloads_clear_dialog_text),
            onConfirm = {
                viewModel.clear()
                showClearDialog = false
            },
            onDismiss = { showClearDialog = false },
        )
    }
}

@Composable
private fun DownloadsContent(
    downloads: List<DownloadRecord>,
    bottomPad: PaddingValues,
    onRetry: (Long) -> Unit,
) {
    if (downloads.isEmpty()) {
        ReaderEmptyState(
            title = stringResource(R.string.downloads_empty),
            message = stringResource(R.string.downloads_empty_hint),
        )
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize().testTag(DownloadTags.LIST),
            contentPadding =
                PaddingValues(
                    start = ReaderSpace.gutter,
                    end = ReaderSpace.gutter,
                    top = 8.dp,
                    bottom = LIST_END_SPACE + bottomPad.calculateBottomPadding(),
                ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(downloads, key = { it.id }, contentType = { "download" }) { record ->
                DownloadRow(record = record, onRetry = { onRetry(record.id) })
            }
        }
    }
}

@Composable
private fun DownloadRow(
    record: DownloadRecord,
    onRetry: () -> Unit,
) {
    DownloadRow(
        fileName = record.fileName,
        modifier = Modifier.testTag(DownloadTags.row(record.id)),
        status = stringResource(record.status.labelRes()),
        progress = record.progressFraction,
        active = record.status.isActive,
        retryLabel = stringResource(R.string.downloads_retry_label),
        retryDescription = stringResource(R.string.downloads_retry),
        onRetry = onRetry.takeIf { record.status == DownloadStatus.FAILED },
    )
}

/** A final card remains clear of the navigation bar or system gesture area. */
private val LIST_END_SPACE = 16.dp

@StringRes
private fun DownloadStatus.labelRes(): Int =
    when (this) {
        DownloadStatus.QUEUED -> R.string.downloads_status_queued
        DownloadStatus.RUNNING -> R.string.downloads_status_running
        DownloadStatus.COMPLETED -> R.string.downloads_status_completed
        DownloadStatus.FAILED -> R.string.downloads_status_failed
    }
