package com.orbin.uinext

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.orbin.uinext.tokens.NextRadius
import com.orbin.uinext.tokens.NextSpace
import com.orbin.uinext.tokens.NextType

/** A board the first-run picker offers. */
data class OnboardingBoard(
    val id: String,
    val title: String,
    val description: String,
    val isNsfw: Boolean,
)

/** The first-run picker's board list as it loads. */
sealed interface OnboardingBoards {
    data object Loading : OnboardingBoards

    data class Failed(
        val message: String,
    ) : OnboardingBoards

    data class Ready(
        val boards: List<OnboardingBoard>,
    ) : OnboardingBoards
}

/**
 * First run, in one step: pick the boards to follow. The same screen on Android and iOS.
 *
 * Everything else a wizard used to ask — theme, playback, privacy — either has a sensible default
 * or lives in Settings, so a new reader is one screen away from their feed.
 *
 * [sites] are the sites' names; the switcher shows only when there is more than one.
 */
@Composable
fun OnboardingScreen(
    boards: OnboardingBoards,
    following: Set<String>,
    sites: List<String>,
    selectedSite: Int,
    onSelectSite: (Int) -> Unit,
    onFollow: (board: String, follow: Boolean) -> Unit,
    onRetry: () -> Unit,
    onStart: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .windowInsetsPadding(
                    WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
                ),
    ) {
        ScreenTitle(
            text = "Welcome to Orbin",
            subtitle = "Follow a few boards to fill your feed.",
        )
        if (sites.size > 1) {
            SiteSelector(sites, selectedSite, onSelectSite)
        }
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            BoardsList(boards, following, onFollow, onRetry)
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
                text = if (following.isEmpty()) "You can follow boards later" else "${following.size} following",
                style = NextType.footnote,
                color = next.muted,
                modifier = Modifier.weight(1f).padding(start = 4.dp),
            )
            InlineAction(label = "Start browsing", accent = true, onClick = onStart)
        }
    }
}

@Composable
private fun SiteSelector(
    sites: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
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
        sites.forEachIndexed { index, name ->
            InlineAction(
                label = name,
                selected = index == selected,
                onClick = { onSelect(index) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun BoardsList(
    state: OnboardingBoards,
    following: Set<String>,
    onFollow: (board: String, follow: Boolean) -> Unit,
    onRetry: () -> Unit,
) {
    when (state) {
        OnboardingBoards.Loading -> NextLoading()
        is OnboardingBoards.Failed -> NextError(state.message, onRetry = onRetry)
        is OnboardingBoards.Ready ->
            if (state.boards.isEmpty()) {
                NextEmpty("No boards available")
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 6.dp),
                ) {
                    items(state.boards, key = { it.id }) { board ->
                        BoardRow(board, isFollowed = board.id in following, onFollow = onFollow)
                        GroupedDivider()
                    }
                }
            }
    }
}

@Composable
private fun BoardRow(
    board: OnboardingBoard,
    isFollowed: Boolean,
    onFollow: (board: String, follow: Boolean) -> Unit,
) {
    val description =
        board.description.ifBlank {
            if (board.isNsfw) "Adult board" else "Imageboard catalog"
        }
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .nextClickable(onClick = { onFollow(board.id, !isFollowed) })
                .padding(horizontal = NextSpace.gutter, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        BoardMonogram(board.id)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "/${board.id}/",
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
                text = description,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = next.muted,
                style = NextType.footnote,
            )
        }
        NextToggle(checked = isFollowed, onCheckedChange = { onFollow(board.id, it) })
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
