package com.orbin.feature.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.orbin.core.model.Board
import com.orbin.core.ui.state.ErrorView
import com.orbin.core.ui.state.LoadingView

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BoardSetupScreen(
    onBack: () -> Unit,
    onOpenBoard: (provider: String, board: String, title: String) -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val favoriteBoardIds by viewModel.favoriteBoardIds.collectAsStateWithLifecycle()
    val subscribedBoardIds by viewModel.subscribedBoardIds.collectAsStateWithLifecycle()
    val openBoard: (Board) -> Unit = { board ->
        onOpenBoard(viewModel.providerId, board.id.value, board.title)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Board setup") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (val state = uiState) {
                HomeUiState.Loading -> LoadingView()
                is HomeUiState.Error -> ErrorView(state.message, onRetry = viewModel::load)
                is HomeUiState.Success ->
                    BoardSetupContent(
                        providerName = state.providerName,
                        boards = state.boards,
                        favoriteBoardIds = favoriteBoardIds,
                        subscribedBoardIds = subscribedBoardIds,
                        onFavoriteChange = viewModel::setFavorite,
                        onSubscriptionChange = viewModel::setSubscribed,
                        onOpenBoard = openBoard,
                        onOpenFullSetup = null,
                        onClose = null,
                        modifier = Modifier.fillMaxSize(),
                    )
            }
        }
    }
}
