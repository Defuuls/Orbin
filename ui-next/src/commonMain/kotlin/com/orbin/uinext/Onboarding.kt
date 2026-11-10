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
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.orbin.uinext.resources.Res
import com.orbin.uinext.resources.next_onboarding_adult_board
import com.orbin.uinext.resources.next_onboarding_catalog
import com.orbin.uinext.resources.next_onboarding_following_count
import com.orbin.uinext.resources.next_onboarding_skip_hint
import com.orbin.uinext.resources.next_onboarding_start
import com.orbin.uinext.resources.next_onboarding_subtitle
import com.orbin.uinext.resources.next_onboarding_title
import com.orbin.uinext.tokens.NextSpace
import com.orbin.uinext.tokens.NextType
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

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
            text = stringResource(Res.string.next_onboarding_title),
            subtitle = stringResource(Res.string.next_onboarding_subtitle),
        )
        if (sites.size > 1) {
            SiteSelector(sites, selectedSite, onSelectSite)
        }
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            BoardsList(boards, following, sites.getOrNull(selectedSite).orEmpty(), onFollow, onRetry)
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
                text =
                    if (following.isEmpty()) {
                        stringResource(Res.string.next_onboarding_skip_hint)
                    } else {
                        pluralStringResource(
                            Res.plurals.next_onboarding_following_count,
                            following.size,
                            following.size,
                        )
                    },
                style = NextType.footnote,
                color = next.muted,
                modifier = Modifier.weight(1f).padding(start = 4.dp),
            )
            InlineAction(label = stringResource(Res.string.next_onboarding_start), accent = true, onClick = onStart)
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
    siteId: String,
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
                        BoardRow(board, siteId, isFollowed = board.id in following, onFollow = onFollow)
                        GroupedDivider()
                    }
                }
            }
    }
}

@Composable
private fun BoardRow(
    board: OnboardingBoard,
    siteId: String,
    isFollowed: Boolean,
    onFollow: (board: String, follow: Boolean) -> Unit,
) {
    val description =
        board.description.ifBlank {
            if (board.isNsfw) {
                stringResource(Res.string.next_onboarding_adult_board)
            } else {
                stringResource(Res.string.next_onboarding_catalog)
            }
        }
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .toggleable(value = isFollowed, role = Role.Switch, onValueChange = { onFollow(board.id, it) })
                .padding(horizontal = NextSpace.gutter, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        BoardIcon(siteId = siteId, boardId = board.id, title = board.title, modifier = Modifier.size(42.dp))
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
        Box(Modifier.clearAndSetSemantics {}) {
            NextToggle(checked = isFollowed, onCheckedChange = null)
        }
    }
}
