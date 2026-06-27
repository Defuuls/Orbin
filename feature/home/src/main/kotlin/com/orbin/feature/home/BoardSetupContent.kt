package com.orbin.feature.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.orbin.core.model.Board

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BoardSetupContent(
    providerName: String,
    boards: List<Board>,
    favoriteBoardIds: Set<String>,
    subscribedBoardIds: Set<String>,
    onFavoriteChange: (board: String, favorite: Boolean) -> Unit,
    onSubscriptionChange: (board: String, subscribed: Boolean) -> Unit,
    onOpenBoard: (Board) -> Unit,
    onOpenFullSetup: (() -> Unit)?,
    onClose: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val favoriteBoards = boards.filter { it.id.value in favoriteBoardIds }
    val subscribedBoards = boards.filter { it.id.value in subscribedBoardIds }
    val randomBoard =
        remember(boards, favoriteBoardIds, subscribedBoardIds) {
            (favoriteBoards + subscribedBoards)
                .distinctBy { it.id.value }
                .randomOrNull() ?: boards.randomOrNull()
        }

    Column(
        modifier = modifier.verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text("Board setup", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(providerName, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (onClose != null) {
                IconButton(onClick = onClose) {
                    Icon(Icons.Filled.Close, contentDescription = "Close")
                }
            }
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { randomBoard?.let(onOpenBoard) },
                enabled = randomBoard != null,
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Filled.Casino, contentDescription = null)
                Text(text = "Random", modifier = Modifier.padding(start = 8.dp))
            }
            if (onOpenFullSetup != null) {
                TextButton(onClick = onOpenFullSetup) {
                    Text("Full setup")
                }
            }
        }

        BoardChipSection(
            title = "Favorites",
            emptyText = "No favorite boards yet",
            boards = favoriteBoards,
            onBoardClick = onOpenBoard,
            onRemove = { board -> onFavoriteChange(board.id.value, false) },
        )

        BoardChipSection(
            title = "Subscriptions",
            emptyText = "No subscribed boards yet",
            boards = subscribedBoards,
            onBoardClick = onOpenBoard,
            onRemove = { board -> onSubscriptionChange(board.id.value, false) },
        )

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Pick favorites", fontWeight = FontWeight.Bold)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                boards.forEach { board ->
                    val isFavorite = board.id.value in favoriteBoardIds
                    FilterChip(
                        selected = isFavorite,
                        onClick = { onFavoriteChange(board.id.value, !isFavorite) },
                        label = { Text("/${board.id.value}/") },
                        leadingIcon = {
                            Icon(
                                imageVector =
                                    if (isFavorite) {
                                        Icons.Filled.Star
                                    } else {
                                        Icons.Outlined.StarBorder
                                    },
                                contentDescription = null,
                            )
                        },
                    )
                }
            }
        }

        Column {
            Text("Subscribe", fontWeight = FontWeight.Bold)
            boards.forEach { board ->
                val isSubscribed = board.id.value in subscribedBoardIds
                ListItem(
                    modifier = Modifier.clickable { onSubscriptionChange(board.id.value, !isSubscribed) },
                    headlineContent = { Text("/${board.id.value}/ - ${board.title}") },
                    supportingContent = {
                        if (board.description.isNotBlank()) Text(board.description)
                    },
                    trailingContent = {
                        Switch(
                            checked = isSubscribed,
                            onCheckedChange = { onSubscriptionChange(board.id.value, it) },
                        )
                    },
                )
                HorizontalDivider()
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BoardChipSection(
    title: String,
    emptyText: String,
    boards: List<Board>,
    onBoardClick: (Board) -> Unit,
    onRemove: (Board) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, fontWeight = FontWeight.Bold)
        if (boards.isEmpty()) {
            Text(emptyText, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                boards.forEach { board ->
                    AssistChip(
                        onClick = { onBoardClick(board) },
                        label = { Text("/${board.id.value}/") },
                        leadingIcon = { Icon(Icons.Filled.Done, contentDescription = null) },
                        trailingIcon = {
                            IconButton(onClick = { onRemove(board) }) {
                                Icon(Icons.Filled.Close, contentDescription = "Remove")
                            }
                        },
                    )
                }
            }
        }
    }
}
