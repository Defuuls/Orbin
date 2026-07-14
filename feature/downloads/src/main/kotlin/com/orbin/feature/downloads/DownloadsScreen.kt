package com.orbin.feature.downloads

import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Downloads") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::clear) {
                        Icon(Icons.Filled.Delete, contentDescription = "Clear")
                    }
                },
            )
        },
    ) { padding ->
        if (downloads.isEmpty()) {
            EmptyView("No downloads yet", Modifier.padding(padding))
            return@Scaffold
        }
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
            items(downloads, key = { it.id }) { record ->
                ListItem(
                    headlineContent = { Text(record.fileName) },
                    supportingContent = { Text(record.status.label()) },
                    trailingContent = {
                        if (record.status == DownloadStatus.FAILED) {
                            IconButton(onClick = { viewModel.retry(record.id) }) {
                                Icon(Icons.Filled.Refresh, contentDescription = "Retry download")
                            }
                        }
                    },
                )
                HorizontalDivider()
            }
        }
    }
}

private fun DownloadStatus.label(): String =
    when (this) {
        DownloadStatus.QUEUED -> "Queued"
        DownloadStatus.RUNNING -> "Downloading…"
        DownloadStatus.COMPLETED -> "Completed"
        DownloadStatus.FAILED -> "Failed"
    }
