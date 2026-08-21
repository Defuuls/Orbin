package com.orbin.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.orbin.core.designsystem.component.ModernSmallTopAppBar
import com.orbin.core.model.Board
import com.orbin.core.model.hiddenTagTokens
import com.orbin.core.model.matchesFilterTokens
import com.orbin.core.ui.state.EmptyView
import com.orbin.core.ui.state.ErrorView
import com.orbin.core.ui.state.LoadingView

/**
 * Full-screen board gallery: large, media-style tiles for every board the provider exposes.
 * Tapping a tile opens that board; the top bar offers a random pick. Replaces the old board-setup
 * panel now that subscriptions are managed from Settings.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BoardGalleryScreen(
    onBack: () -> Unit,
    onOpenBoard: (provider: String, board: String, title: String) -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val providerId by viewModel.providerId.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val openBoard: (Board) -> Unit = { board ->
        onOpenBoard(providerId, board.id.value, board.title)
    }

    // The reader's board filters, applied here rather than only on the home list: this screen is
    // reachable from Settings now, and a gallery of every board would otherwise be the one place
    // where "Hide NSFW boards" and hidden tags quietly do not apply.
    val visibleBoards =
        remember(uiState, settings.hideNsfwBoards, settings.hiddenTags) {
            (uiState as? HomeUiState.Success)
                ?.boards
                ?.filterNot { board -> settings.hideNsfwBoards && board.isNsfw }
                ?.filterNot { board -> board.matchesFilterTokens(settings.hiddenTagTokens()) }
                .orEmpty()
        }

    Scaffold(
        topBar = {
            ModernSmallTopAppBar(
                title = "Boards",
                navigationIcon = Icons.AutoMirrored.Filled.ArrowBack,
                onNavigationClick = onBack,
                actions = {
                    IconButton(
                        onClick = { visibleBoards.randomOrNull()?.let(openBoard) },
                        enabled = visibleBoards.isNotEmpty(),
                    ) {
                        Icon(Icons.Filled.Casino, contentDescription = "Open a random board")
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
                    if (visibleBoards.isEmpty()) {
                        // Distinguished so a reader who has filtered everything out is told that,
                        // rather than being left to think the provider returned nothing.
                        EmptyView(
                            if (state.boards.isEmpty()) {
                                "No boards available"
                            } else {
                                "Every board is hidden by your board filters"
                            },
                        )
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = 160.dp),
                            contentPadding = PaddingValues(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            items(visibleBoards, key = { it.id.value }) { board ->
                                BoardTile(board = board, onClick = { openBoard(board) })
                            }
                        }
                    }
            }
        }
    }
}

@Composable
private fun BoardTile(
    board: Board,
    onClick: () -> Unit,
) {
    val boardId = board.id.value
    val base = boardColor(boardId)
    val letter = boardId.take(1).uppercase()
    Card(
        shape = MaterialTheme.shapes.large,
        modifier =
            Modifier
                .aspectRatio(1f)
                .clickable(onClick = onClick),
    ) {
        Box(modifier = Modifier.fillMaxSize().background(base)) {
            // Large letter "avatar" stands in for board artwork.
            Text(
                text = letter,
                color = Color.White.copy(alpha = 0.9f),
                fontSize = 56.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.Center),
            )

            if (board.isNsfw) {
                Surface(
                    color = Color.Black.copy(alpha = 0.45f),
                    contentColor = Color.White,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
                ) {
                    Text(
                        text = "NSFW",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }

            // Bottom scrim keeps the label legible over the tile colour.
            Column(
                modifier =
                    Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f)),
                            ),
                        ).padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                Text(
                    text = "/$boardId/",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = board.title,
                    color = Color.White.copy(alpha = 0.85f),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private const val HUE_DEGREES = 360

/** Stable per-board accent colour derived from the board id, so tiles look varied but consistent. */
private fun boardColor(id: String): Color {
    val hue = (((id.hashCode() % HUE_DEGREES) + HUE_DEGREES) % HUE_DEGREES).toFloat()
    return Color.hsv(hue, saturation = 0.45f, value = 0.55f)
}
