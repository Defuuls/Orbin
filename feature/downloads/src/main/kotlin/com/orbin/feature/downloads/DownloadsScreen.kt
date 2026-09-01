package com.orbin.feature.downloads

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.orbin.core.designsystem.component.ModernConfirmDialog
import com.orbin.core.model.DownloadRecord
import com.orbin.core.model.DownloadStatus
import com.orbin.core.ui.state.EmptyView

/** Download history. The actual transfer + notifications are owned by the platform download manager. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(
    onBack: () -> Unit,
    viewModel: DownloadsViewModel = hiltViewModel(),
) {
    val downloads by viewModel.downloads.collectAsStateWithLifecycle()
    var showClearDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.downloads_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.downloads_back),
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showClearDialog = true }) {
                        Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.downloads_clear_action))
                    }
                },
            )
        },
    ) { padding ->
        if (downloads.isEmpty()) {
            EmptyView(stringResource(R.string.downloads_empty), Modifier.padding(padding))
            return@Scaffold
        }
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
            items(downloads, key = { it.id }) { record ->
                ListItem(
                    headlineContent = { Text(record.fileName) },
                    supportingContent = { DownloadProgress(record) },
                    trailingContent = {
                        if (record.status == DownloadStatus.FAILED) {
                            IconButton(onClick = { viewModel.retry(record.id) }) {
                                Icon(
                                    Icons.Filled.Refresh,
                                    contentDescription = stringResource(R.string.downloads_retry),
                                )
                            }
                        }
                    },
                )
                HorizontalDivider()
            }
        }
    }

    if (showClearDialog) {
        ModernConfirmDialog(
            title = stringResource(R.string.downloads_clear_dialog_title),
            text = stringResource(R.string.downloads_clear_dialog_text),
            onConfirm = {
                viewModel.clear()
                showClearDialog = false
            },
            onDismiss = { showClearDialog = false },
        )
    }
}

@Composable
private fun DownloadProgress(record: DownloadRecord) {
    val fraction = record.progressFraction
    Column {
        Text(
            if (fraction != null && record.status in ACTIVE_DOWNLOAD_STATUSES) {
                "${stringResource(record.status.labelRes())} · ${(fraction * 100).toInt()}%"
            } else {
                stringResource(record.status.labelRes())
            },
        )
        if (record.status in ACTIVE_DOWNLOAD_STATUSES) {
            if (fraction != null) {
                LinearProgressIndicator(
                    progress = { fraction },
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                )
            } else {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
            }
        }
    }
}

private val ACTIVE_DOWNLOAD_STATUSES = setOf(DownloadStatus.QUEUED, DownloadStatus.RUNNING)

@StringRes
private fun DownloadStatus.labelRes(): Int =
    when (this) {
        DownloadStatus.QUEUED -> R.string.downloads_status_queued
        DownloadStatus.RUNNING -> R.string.downloads_status_running
        DownloadStatus.COMPLETED -> R.string.downloads_status_completed
        DownloadStatus.FAILED -> R.string.downloads_status_failed
    }
