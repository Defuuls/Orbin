package com.orbin.feature.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.orbin.core.model.Board
import com.orbin.provider.api.ImageBoardProvider
import com.orbin.uinext.GroupedDivider
import com.orbin.uinext.InlineAction
import com.orbin.uinext.NextEmpty
import com.orbin.uinext.NextError
import com.orbin.uinext.NextLoading
import com.orbin.uinext.NextTheme
import com.orbin.uinext.NextToggle
import com.orbin.uinext.ScreenTitle
import com.orbin.uinext.next
import com.orbin.uinext.nextClickable
import com.orbin.uinext.tokens.NextRadius
import com.orbin.uinext.tokens.NextSpace
import com.orbin.uinext.tokens.NextType
import kotlinx.collections.immutable.ImmutableList

/**
 * First run, in one step: pick the boards to follow.
 *
 * Everything else a wizard used to ask — theme, playback, privacy — either has a sensible default
 * or lives in Settings, so a new reader is one screen away from their feed.
 */
@Composable
fun OnboardingScreen(
    onFinish: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val boards by viewModel.boards.collectAsStateWithLifecycle()
    val subscribed by viewModel.subscribedBoardIds.collectAsStateWithLifecycle()
    val selectedProvider by viewModel.selectedProvider.collectAsStateWithLifecycle()

    NextTheme {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(
                        WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
                    ),
        ) {
            ScreenTitle(
                text = "Welcome to Orbin",
                subtitle = "Follow a few boards to fill your feed.",
            )
            if (viewModel.providers.size > 1) {
                ProviderSelector(viewModel.providers, selectedProvider, viewModel::setSelectedProvider)
            }
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                BoardsList(
                    state = boards,
                    subscribedBoardIds = subscribed,
                    onSubscriptionChange = viewModel::setSubscribed,
                    onRetry = viewModel::loadBoards,
                )
            }
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))
                        .padding(horizontal = NextSpace.gutter - 4.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (subscribed.isEmpty()) "You can follow boards later" else "${subscribed.size} following",
                    style = NextType.footnote,
                    color = next.muted,
                    modifier = Modifier.weight(1f).padding(start = 4.dp),
                )
                InlineAction(
                    label = "Start browsing",
                    accent = true,
                    onClick = {
                        viewModel.complete()
                        onFinish()
                    },
                )
            }
        }
    }
}

@Composable
private fun ProviderSelector(
    providers: ImmutableList<ImageBoardProvider>,
    selectedProvider: ImageBoardProvider,
    onProviderSelected: (ImageBoardProvider) -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = NextSpace.gutter, vertical = 4.dp)
                .background(next.raised, RoundedCornerShape(12.dp))
                .padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        providers.forEach { provider ->
            InlineAction(
                label = provider.metadata.displayName,
                selected = provider.metadata.id == selectedProvider.metadata.id,
                onClick = { onProviderSelected(provider) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun BoardsList(
    state: OnboardingBoardsState,
    subscribedBoardIds: Set<String>,
    onSubscriptionChange: (board: String, subscribed: Boolean) -> Unit,
    onRetry: () -> Unit,
) {
    when (state) {
        OnboardingBoardsState.Loading -> NextLoading()
        is OnboardingBoardsState.Error -> NextError(state.message, onRetry = onRetry)
        is OnboardingBoardsState.Success ->
            if (state.boards.isEmpty()) {
                NextEmpty("No boards available")
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 6.dp),
                ) {
                    items(state.boards, key = { it.id.value }) { board ->
                        BoardRow(
                            board = board,
                            isSubscribed = board.id.value in subscribedBoardIds,
                            onSubscriptionChange = onSubscriptionChange,
                        )
                        GroupedDivider()
                    }
                }
            }
    }
}

@Composable
private fun BoardRow(
    board: Board,
    isSubscribed: Boolean,
    onSubscriptionChange: (board: String, subscribed: Boolean) -> Unit,
) {
    val boardDescription =
        board.description.ifBlank {
            if (board.isNsfw) "Adult board" else "Imageboard catalog"
        }
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .nextClickable(onClick = { onSubscriptionChange(board.id.value, !isSubscribed) })
                .padding(horizontal = NextSpace.gutter, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        BoardMonogram(board.id.value)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "/${board.id.value}/",
                    color = next.accent,
                    fontWeight = FontWeight.Bold,
                    style = NextType.headline,
                )
                Text(
                    text = board.title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = NextType.body,
                )
            }
            Text(
                text = boardDescription,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = next.muted,
                style = NextType.footnote,
            )
        }
        NextToggle(
            checked = isSubscribed,
            onCheckedChange = { onSubscriptionChange(board.id.value, it) },
        )
    }
}

@Composable
private fun BoardMonogram(id: String) {
    Box(
        modifier =
            Modifier
                .size(42.dp)
                .background(boardColor(id), RoundedCornerShape(NextRadius.tight)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = id.take(1).uppercase(),
            color = Color.White,
            fontWeight = FontWeight.Bold,
            style = NextType.title3,
        )
    }
}

private const val HUE_DEGREES = 360

private fun boardColor(id: String): Color {
    val hue = (((id.hashCode() % HUE_DEGREES) + HUE_DEGREES) % HUE_DEGREES).toFloat()
    return Color.hsv(hue, saturation = 0.42f, value = 0.48f)
}
