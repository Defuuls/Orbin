package com.orbin.feature.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.orbin.core.designsystem.component.ModernConfirmDialog
import com.orbin.core.ui.state.EmptyView

/** Reading-history screen. Tapping an entry reopens the thread; the toolbar clears history. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    onOpenThread: (provider: String, board: String, thread: Long, title: String) -> Unit,
    viewModel: HistoryViewModel = hiltViewModel(),
) {
    val history by viewModel.history.collectAsStateWithLifecycle()
    var showClearDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.history_title)) },
                actions = {
                    IconButton(onClick = { showClearDialog = true }) {
                        Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.history_clear_action))
                    }
                },
            )
        },
    ) { padding ->
        if (history.isEmpty()) {
            EmptyView(stringResource(R.string.history_empty), Modifier.padding(padding))
            return@Scaffold
        }
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
            items(history, key = { "${it.key.provider.value}/${it.key.board.value}/${it.key.thread.value}" }) { entry ->
                ListItem(
                    modifier =
                        Modifier.clickable {
                            onOpenThread(
                                entry.key.provider.value,
                                entry.key.board.value,
                                entry.key.thread.value,
                                entry.title,
                            )
                        },
                    headlineContent = { Text(entry.title) },
                    supportingContent = { Text(stringResource(R.string.history_board_label, entry.key.board.value)) },
                )
                HorizontalDivider()
            }
        }
    }

    if (showClearDialog) {
        ModernConfirmDialog(
            title = stringResource(R.string.history_clear_dialog_title),
            text = stringResource(R.string.history_clear_dialog_text),
            onConfirm = {
                viewModel.clear()
                showClearDialog = false
            },
            onDismiss = { showClearDialog = false },
        )
    }
}
