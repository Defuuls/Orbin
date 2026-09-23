package com.orbin.feature.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.orbin.core.model.AppSettings
import com.orbin.core.model.AppThemeMode
import com.orbin.core.model.Board
import com.orbin.provider.api.ImageBoardProvider
import com.orbin.uinext.GroupedDivider
import com.orbin.uinext.InlineAction
import com.orbin.uinext.NextEmpty
import com.orbin.uinext.NextError
import com.orbin.uinext.NextIconAction
import com.orbin.uinext.NextLinearProgress
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

private enum class SetupStep(
    val title: String,
    val label: String,
) {
    START("Setup", "Start"),
    BOARDS("Boards", "Subscribe"),
    APPEARANCE("Look", "Display"),
    MEDIA("Media", "Playback"),
    PRIVACY("Privacy", "Privacy"),
    DONE("Ready", "Finish"),
}

/**
 * Reusable setup wizard. It runs on first launch and can also be opened from Settings to revisit
 * board subscriptions, favorites, media preferences, and privacy controls.
 */
@Composable
fun OnboardingScreen(
    onFinish: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val boards by viewModel.boards.collectAsStateWithLifecycle()
    val subscribed by viewModel.subscribedBoardIds.collectAsStateWithLifecycle()
    val favorites by viewModel.favoriteBoardIds.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val selectedProvider by viewModel.selectedProvider.collectAsStateWithLifecycle()

    val steps = SetupStep.entries
    var index by rememberSaveable { mutableIntStateOf(0) }
    val step = steps[index]
    val isLast = index == steps.lastIndex

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
                text = step.title,
                subtitle = "${index + 1}/${steps.size} · ${step.label}",
            )
            NextLinearProgress(
                progress = (index + 1).toFloat() / steps.size,
                modifier = Modifier.padding(horizontal = NextSpace.gutter),
            )
            StepTabs(
                steps = steps,
                selectedIndex = index,
                onSelect = { index = it },
            )
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                when (step) {
                    SetupStep.START ->
                        StartStep(
                            settings,
                            selectedProvider,
                            viewModel.providers,
                            viewModel::setSelectedProvider,
                        )
                    SetupStep.BOARDS ->
                        BoardsStep(
                            state = boards,
                            subscribedBoardIds = subscribed,
                            favoriteBoardIds = favorites,
                            onSubscriptionChange = viewModel::setSubscribed,
                            onFavoriteChange = viewModel::setFavorite,
                            onRetry = viewModel::loadBoards,
                        )
                    SetupStep.APPEARANCE ->
                        AppearanceStep(
                            settings,
                            viewModel::setThemeMode,
                            viewModel::setDynamicColor,
                            viewModel::setAmoled,
                        )
                    SetupStep.MEDIA ->
                        MediaStep(
                            settings,
                            viewModel::setMute,
                        )
                    SetupStep.PRIVACY ->
                        PrivacyStep(
                            settings,
                            viewModel::setBiometricLock,
                        )
                    SetupStep.DONE -> DoneStep(subscribed.size, favorites.size)
                }
            }
            SetupBottomBar(
                showBack = index > 0,
                isLast = isLast,
                onBack = { index -= 1 },
                onNext = {
                    if (isLast) {
                        viewModel.complete()
                        onFinish()
                    } else {
                        index += 1
                    }
                },
            )
        }
    }
}

@Composable
private fun StepTabs(
    steps: List<SetupStep>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = NextSpace.gutter - 4.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        steps.forEachIndexed { index, step ->
            InlineAction(
                label = step.label,
                selected = index == selectedIndex,
                onClick = { onSelect(index) },
            )
        }
    }
}

@Composable
private fun SetupBottomBar(
    showBack: Boolean,
    isLast: Boolean,
    onBack: () -> Unit,
    onNext: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))
                .padding(horizontal = NextSpace.gutter - 4.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showBack) {
            InlineAction(label = "Back", onClick = onBack)
        }
        Box(modifier = Modifier.weight(1f))
        InlineAction(
            label = if (isLast) "Finish setup" else "Continue",
            accent = true,
            onClick = onNext,
        )
    }
}

@Composable
private fun StartStep(
    settings: AppSettings,
    selectedProvider: ImageBoardProvider,
    providers: ImmutableList<ImageBoardProvider>,
    onProviderSelected: (ImageBoardProvider) -> Unit,
) {
    SetupPage {
        Text(
            "Orbin setup",
            style = NextType.title1,
            fontWeight = FontWeight.Bold,
            color = next.ink,
        )
        Text(
            text = "Choose boards to follow, tune playback, and lock down the defaults before browsing.",
            color = next.muted,
        )

        ProviderSelector(providers, selectedProvider, onProviderSelected)
        SignalPanel(settings)
    }
}

@Composable
private fun ProviderSelector(
    providers: ImmutableList<ImageBoardProvider>,
    selectedProvider: ImageBoardProvider,
    onProviderSelected: (ImageBoardProvider) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Select a site", style = NextType.subheadline)
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(next.raised, RoundedCornerShape(12.dp))
                    .padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            providers.forEach { provider ->
                InlineAction(
                    label = provider.metadata.displayName,
                    selected =
                        provider.metadata.id == selectedProvider.metadata.id,
                    onClick = { onProviderSelected(provider) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun SignalPanel(settings: AppSettings) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(next.raised, RoundedCornerShape(12.dp))
                .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SetupSignal(Icons.Outlined.Palette, "Display", settings.themeMode.name.lowercase())
        SetupSignal(
            Icons.Outlined.PlayCircle,
            "Media",
            if (settings.muteByDefault) "muted by default" else "unmuted",
        )
        SetupSignal(Icons.Outlined.Security, "Network", "https only")
        SetupSignal(Icons.Outlined.Lock, "App lock", if (settings.biometricLockEnabled) "biometric" else "off")
    }
}

@Composable
private fun SetupSignal(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    value: String,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(icon, contentDescription = null, tint = next.accent, modifier = Modifier.size(18.dp))
        Text(title, modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
        Text(value, color = next.muted, style = NextType.footnote)
    }
}

@Composable
private fun BoardsStep(
    state: OnboardingBoardsState,
    subscribedBoardIds: Set<String>,
    favoriteBoardIds: Set<String>,
    onSubscriptionChange: (board: String, subscribed: Boolean) -> Unit,
    onFavoriteChange: (board: String, favorite: Boolean) -> Unit,
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
                    item {
                        BoardHeader(
                            subscribedCount = subscribedBoardIds.size,
                            favoriteCount = favoriteBoardIds.size,
                        )
                    }
                    items(state.boards, key = { it.id.value }) { board ->
                        BoardRow(
                            board = board,
                            isSubscribed = board.id.value in subscribedBoardIds,
                            isFavorite = board.id.value in favoriteBoardIds,
                            onSubscriptionChange = onSubscriptionChange,
                            onFavoriteChange = onFavoriteChange,
                        )
                        GroupedDivider()
                    }
                }
            }
    }
}

@Composable
private fun BoardHeader(
    subscribedCount: Int,
    favoriteCount: Int,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Subscribe to boards", style = NextType.title3, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MetaChip("$subscribedCount subscribed")
            MetaChip("$favoriteCount favorites")
        }
    }
}

@Composable
private fun BoardRow(
    board: Board,
    isSubscribed: Boolean,
    isFavorite: Boolean,
    onSubscriptionChange: (board: String, subscribed: Boolean) -> Unit,
    onFavoriteChange: (board: String, favorite: Boolean) -> Unit,
) {
    val boardDescription =
        board.description.ifBlank {
            if (board.isNsfw) "Adult board" else "Imageboard catalog"
        }
    val favoriteTint =
        if (isFavorite) {
            next.accent
        } else {
            next.muted
        }

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .nextClickable(onClick = { onSubscriptionChange(board.id.value, !isSubscribed) })
                .padding(horizontal = 14.dp, vertical = 9.dp),
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
        NextIconAction(
            imageVector = if (isFavorite) Icons.Outlined.Star else Icons.Outlined.StarBorder,
            contentDescription = if (isFavorite) "Remove favorite" else "Favorite board",
            tint = favoriteTint,
            onClick = { onFavoriteChange(board.id.value, !isFavorite) },
        )
        NextToggle(
            checked = isSubscribed,
            onCheckedChange = { onSubscriptionChange(board.id.value, it) },
        )
    }
}

@Composable
private fun BoardMonogram(id: String) {
    val color = boardColor(id)
    Box(
        modifier =
            Modifier
                .size(42.dp)
                .background(color, RoundedCornerShape(NextRadius.tight)),
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

@Composable
private fun AppearanceStep(
    settings: AppSettings,
    onThemeMode: (AppThemeMode) -> Unit,
    onDynamicColor: (Boolean) -> Unit,
    onAmoled: (Boolean) -> Unit,
) {
    SetupPage {
        PreferenceHeader(Icons.Outlined.Palette, "Display preferences", "Theme and contrast")
        SurfacePanel {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AppThemeMode.entries.forEach { mode ->
                    InlineAction(
                        label = mode.name.lowercase().replaceFirstChar { it.uppercase() },
                        selected = settings.themeMode == mode,
                        onClick = { onThemeMode(mode) },
                    )
                }
            }
            PreferenceSwitch("Dynamic color", "Follow system dynamic colors", settings.dynamicColor, onDynamicColor)
            PreferenceSwitch("AMOLED black", "Use true black surfaces in dark mode", settings.amoled, onAmoled)
        }
    }
}

@Composable
private fun MediaStep(
    settings: AppSettings,
    onMute: (Boolean) -> Unit,
) {
    SetupPage {
        PreferenceHeader(Icons.Outlined.PlayCircle, "Media behavior", "Audio and video in thread gallery")
        SurfacePanel {
            PreferenceSwitch("Mute by default", "Keep videos quiet until you opt in", settings.muteByDefault, onMute)
        }
    }
}

@Composable
private fun PrivacyStep(
    settings: AppSettings,
    onBiometricLock: (Boolean) -> Unit,
) {
    SetupPage {
        PreferenceHeader(Icons.Outlined.Security, "Privacy & network", "Transport security and local access")
        SurfacePanel {
            PreferenceSwitch(
                "Data encrypted at rest",
                "History, bookmarks, downloads, and settings are encrypted on this device with " +
                    "SQLCipher and an encrypted DataStore, both keyed by the Android Keystore",
                true,
                {},
            )
            PreferenceSwitch(
                "HTTPS only",
                "Always enforced for board traffic and downloads",
                true,
                {},
            )
            PreferenceSwitch(
                "DNS over HTTPS",
                "Always on — pick a resolver in Settings",
                true,
                {},
            )
            PreferenceSwitch(
                "Lock with biometrics",
                "Require fingerprint or device credential on launch",
                settings.biometricLockEnabled,
                onBiometricLock,
            )
        }
    }
}

@Composable
private fun DoneStep(
    subscribedCount: Int,
    favoriteCount: Int,
) {
    SetupPage {
        Text("Ready to browse", style = NextType.title2, fontWeight = FontWeight.Bold)
        Text(
            text = "Your setup is saved. You can run this again from Settings whenever you want.",
            color = next.muted,
        )
        SurfacePanel {
            SetupSignal(Icons.Outlined.Notifications, "Subscribed boards", subscribedCount.toString())
            SetupSignal(Icons.Outlined.Star, "Favorite boards", favoriteCount.toString())
            SetupSignal(Icons.Outlined.Check, "Setup", "complete")
        }
    }
}

@Composable
private fun SetupPage(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        content = content,
    )
}

@Composable
private fun SurfacePanel(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(next.raised, RoundedCornerShape(12.dp))
                .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        content = content,
    )
}

@Composable
private fun PreferenceHeader(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(icon, contentDescription = null, tint = next.accent)
        Column {
            Text(title, style = NextType.title3, fontWeight = FontWeight.Bold)
            Text(
                subtitle,
                style = NextType.footnote,
                color = next.muted,
            )
        }
    }
}

@Composable
private fun PreferenceSwitch(
    title: String,
    subtitle: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(
                subtitle,
                color = next.muted,
                style = NextType.footnote,
            )
        }
        NextToggle(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun MetaChip(text: String) {
    Text(
        text = text,
        style = NextType.caption1,
        color = next.muted,
        modifier =
            Modifier
                .background(next.elevated, RoundedCornerShape(4.dp))
                .padding(horizontal = 8.dp, vertical = 4.dp),
    )
}

private const val HUE_DEGREES = 360

private fun boardColor(id: String): Color {
    val hue = (((id.hashCode() % HUE_DEGREES) + HUE_DEGREES) % HUE_DEGREES).toFloat()
    return Color.hsv(hue, saturation = 0.42f, value = 0.48f)
}
