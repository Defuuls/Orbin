package com.orbin.feature.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.orbin.core.ui.state.EmptyView
import com.orbin.core.ui.state.ErrorView
import com.orbin.core.ui.state.LoadingView

/** Search screen: board-scoped catalog search with recent-query chips. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SearchScreen(
    onOpenThread: (provider: String, board: String, thread: Long, title: String) -> Unit,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val recents by viewModel.recentQueries.collectAsStateWithLifecycle()
    var board by remember { mutableStateOf("") }
    var query by remember { mutableStateOf("") }

    Scaffold(topBar = { TopAppBar(title = { Text("Search") }) }) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
            OutlinedTextField(
                value = board,
                onValueChange = { board = it },
                label = { Text("Board (e.g. g)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Search query") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { viewModel.search(query, board) }),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )

            if (recents.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 8.dp),
                ) {
                    recents.forEach { recent ->
                        AssistChip(
                            onClick = {
                                query = recent
                                viewModel.search(recent, board)
                            },
                            label = { Text(recent) },
                        )
                    }
                }
            }

            when (val s = state) {
                SearchUiState.Idle -> EmptyView("Search a board's catalog")
                SearchUiState.Loading -> LoadingView()
                is SearchUiState.Error -> ErrorView(s.message)
                is SearchUiState.Results ->
                    if (s.results.isEmpty()) {
                        EmptyView("No matches")
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            items(s.results, key = { it.key.thread.value }) { result ->
                                ListItem(
                                    modifier =
                                        Modifier.clickable {
                                            onOpenThread(
                                                result.key.provider.value,
                                                result.key.board.value,
                                                result.key.thread.value,
                                                result.title,
                                            )
                                        },
                                    headlineContent = { Text(result.title) },
                                    supportingContent = { Text(result.snippet, maxLines = 2) },
                                )
                                HorizontalDivider()
                            }
                        }
                    }
            }
        }
    }
}
