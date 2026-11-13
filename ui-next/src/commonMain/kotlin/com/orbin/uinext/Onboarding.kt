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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.orbin.uinext.resources.Res
import com.orbin.uinext.resources.next_onboarding_adult_board
import com.orbin.uinext.resources.next_onboarding_catalog
import com.orbin.uinext.resources.next_onboarding_empty
import com.orbin.uinext.resources.next_onboarding_following_count
import com.orbin.uinext.resources.next_onboarding_required_hint
import com.orbin.uinext.resources.next_onboarding_saving
import com.orbin.uinext.resources.next_onboarding_start
import com.orbin.uinext.resources.next_onboarding_subtitle
import com.orbin.uinext.resources.next_onboarding_title
import com.orbin.uinext.tokens.NextSpace
import com.orbin.uinext.tokens.OrbinRadius
import com.orbin.uinext.tokens.OrbinType
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
    totalFollowing: Int = following.size,
    isSaving: Boolean = false,
    errorMessage: String? = null,
    canStart: Boolean = totalFollowing > 0 && !isSaving,
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(reader.background)
                .windowInsetsPadding(
                    WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
                ),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = NextSpace.gutter, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OrbinWordmark(logoSize = 32.dp)
            Text(
                text = stringResource(Res.string.next_onboarding_title),
                style = OrbinType.stateTitle.withReaderFont().copy(fontWeight = FontWeight.SemiBold),
                color = reader.ink,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = stringResource(Res.string.next_onboarding_subtitle),
                style = OrbinType.stateBody.withReaderFont(),
                color = reader.inkMuted,
            )
        }
        if (sites.size > 1) {
            SiteSelector(sites, selectedSite, enabled = !isSaving, onSelectSite)
        }
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            BoardsList(boards, following, sites.getOrNull(selectedSite).orEmpty(), !isSaving, onFollow, onRetry)
        }
        if (errorMessage != null) {
            Text(
                text = errorMessage,
                style = OrbinType.meta.withReaderFont(),
                color = reader.error,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = NextSpace.gutter, vertical = 4.dp)
                        .semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(reader.background)
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))
                    .padding(horizontal = NextSpace.gutter, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text =
                    when {
                        isSaving -> stringResource(Res.string.next_onboarding_saving)
                        totalFollowing == 0 -> stringResource(Res.string.next_onboarding_required_hint)
                        else ->
                            pluralStringResource(
                                Res.plurals.next_onboarding_following_count,
                                totalFollowing,
                                totalFollowing,
                            )
                    },
                style = OrbinType.meta.withReaderFont(),
                color = reader.inkMuted,
                modifier = Modifier.weight(1f),
            )
            OrbinFilledButton(
                label = stringResource(Res.string.next_onboarding_start),
                enabled = canStart && totalFollowing > 0 && !isSaving,
                onClick = { if (canStart && totalFollowing > 0 && !isSaving) onStart() },
            )
        }
    }
}

@Composable
private fun SiteSelector(
    sites: List<String>,
    selected: Int,
    enabled: Boolean,
    onSelect: (Int) -> Unit,
) {
    OrbinCard(
        modifier = Modifier.fillMaxWidth().padding(horizontal = NextSpace.gutter, vertical = 4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(6.dp).selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            sites.forEachIndexed { index, name ->
                val isSelected = index == selected
                val shape = RoundedCornerShape(OrbinRadius.pill)
                Box(
                    modifier =
                        Modifier
                            .weight(1f)
                            .heightIn(min = MIN_TOUCH_TARGET)
                            .clip(shape)
                            .background(if (isSelected) reader.accent else Color.Transparent)
                            .semantics { this.selected = isSelected }
                            .nextClickable(
                                enabled = enabled,
                                role = Role.RadioButton,
                                onClick = { onSelect(index) },
                            ).padding(horizontal = 8.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = name,
                        style =
                            OrbinType.label.withReaderFont().copy(
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                            ),
                        color =
                            when {
                                !enabled -> reader.inkMuted
                                isSelected -> reader.onAccent
                                else -> reader.ink
                            },
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun BoardsList(
    state: OnboardingBoards,
    following: Set<String>,
    siteId: String,
    enabled: Boolean,
    onFollow: (board: String, follow: Boolean) -> Unit,
    onRetry: () -> Unit,
) {
    when (state) {
        OnboardingBoards.Loading -> NextLoading()
        is OnboardingBoards.Failed -> NextError(state.message, onRetry = onRetry)
        is OnboardingBoards.Ready ->
            if (state.boards.isEmpty()) {
                NextError(stringResource(Res.string.next_onboarding_empty), onRetry = onRetry)
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = NextSpace.gutter, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(state.boards, key = { it.id }) { board ->
                        BoardRow(
                            board,
                            siteId,
                            isFollowed = board.id in following,
                            enabled = enabled,
                            onFollow = onFollow,
                        )
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
    enabled: Boolean,
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
    OrbinCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = MIN_TOUCH_TARGET)
                    .toggleable(
                        value = isFollowed,
                        enabled = enabled,
                        role = Role.Switch,
                        onValueChange = { onFollow(board.id, it) },
                    ).padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            BoardIcon(siteId = siteId, boardId = board.id, title = board.title, modifier = Modifier.size(48.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "/${board.id}/",
                        color = reader.accent,
                        fontWeight = FontWeight.Bold,
                        style = OrbinType.rowTitle.withReaderFont(),
                    )
                    Text(
                        text = board.title,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        style = OrbinType.readerBody.withReaderFont(),
                        color = reader.ink,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                }
                Text(
                    text = description,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = reader.inkMuted,
                    style = OrbinType.meta.withReaderFont(),
                )
            }
            Box(Modifier.clearAndSetSemantics {}) {
                NextToggle(checked = isFollowed, onCheckedChange = null)
            }
        }
    }
}
