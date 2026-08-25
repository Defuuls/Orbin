package com.orbin.minimal

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.orbin.core.model.BoardId
import com.orbin.core.ui.state.EmptyView
import com.orbin.core.ui.state.ErrorView
import com.orbin.core.ui.state.LoadingView

/** Tick the boards you want in the feed. That is the whole of this build's configuration. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MinimalBoardsScreen(
    onBack: () -> Unit,
    viewModel: MinimalBoardsViewModel = hiltViewModel(),
) {
    val boards by viewModel.boards.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()

    MinimalBoardsContent(
        boards = boards,
        isLoading = isLoading,
        errorMessage = errorMessage,
        onBack = onBack,
        onRefresh = viewModel::refresh,
        onToggle = { id, subscribed -> viewModel.setSubscribed(id, subscribed) },
    )
}

/** The board picker's rendering, detached from its view model so it can be screenshot-tested. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MinimalBoardsContent(
    boards: List<SubscribableBoard>,
    isLoading: Boolean,
    errorMessage: String?,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onToggle: (BoardId, Boolean) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.minimal_boards_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.minimal_back),
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onRefresh, enabled = !isLoading) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = stringResource(R.string.minimal_refresh),
                        )
                    }
                },
            )
        },
    ) { padding ->
        // An empty list is not the same as a pending one. Showing a spinner for both left a fetch
        // that failed, or a provider with no boards, turning forever with no way to retry.
        if (boards.isEmpty()) {
            when {
                isLoading -> LoadingView(Modifier.padding(padding))
                errorMessage != null ->
                    ErrorView(
                        message = errorMessage,
                        onRetry = onRefresh,
                        modifier = Modifier.padding(padding),
                    )
                else ->
                    EmptyView(
                        message = stringResource(R.string.minimal_no_boards_available),
                        modifier = Modifier.padding(padding),
                    )
            }
            return@Scaffold
        }
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
            items(boards, key = { it.board.id.value }) { entry ->
                BoardRow(
                    entry = entry,
                    onToggle = { onToggle(entry.board.id, !entry.isSubscribed) },
                )
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun BoardRow(
    entry: SubscribableBoard,
    onToggle: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "/${entry.board.id.value}/",
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = entry.board.title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Checkbox(checked = entry.isSubscribed, onCheckedChange = { onToggle() })
    }
}
