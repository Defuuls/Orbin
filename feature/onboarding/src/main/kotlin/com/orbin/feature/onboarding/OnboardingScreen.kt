package com.orbin.feature.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.orbin.core.model.AppSettings
import com.orbin.core.model.AppThemeMode
import com.orbin.core.model.Board
import com.orbin.core.ui.state.EmptyView
import com.orbin.core.ui.state.ErrorView
import com.orbin.core.ui.state.LoadingView

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
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(
    onFinish: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val boards by viewModel.boards.collectAsStateWithLifecycle()
    val subscribed by viewModel.subscribedBoardIds.collectAsStateWithLifecycle()
    val favorites by viewModel.favoriteBoardIds.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    val steps = SetupStep.entries
    var index by rememberSaveable { mutableIntStateOf(0) }
    val step = steps[index]
    val isLast = index == steps.lastIndex

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Column {
                            Text(step.title)
                            Text(
                                text = "${index + 1}/${steps.size} ${step.label}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                )
                LinearProgressIndicator(
                    progress = { (index + 1).toFloat() / steps.size },
                    modifier = Modifier.fillMaxWidth(),
                )
                StepTabs(
                    steps = steps,
                    selectedIndex = index,
                    onSelect = { index = it },
                )
            }
        },
        bottomBar = {
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
        },
    ) { padding ->
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerLowest,
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            when (step) {
                SetupStep.START -> StartStep(settings)
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
                    AppearanceStep(settings, viewModel::setThemeMode, viewModel::setDynamicColor, viewModel::setAmoled)
                SetupStep.MEDIA ->
                    MediaStep(
                        settings,
                        viewModel::setAutoplay,
                        viewModel::setMute,
                        viewModel::setPreload,
                    )
                SetupStep.PRIVACY ->
                    PrivacyStep(
                        settings,
                        viewModel::setDoh,
                        viewModel::setBiometricLock,
                        viewModel::setSaveRecentSearches,
                    )
                SetupStep.DONE -> DoneStep(subscribed.size, favorites.size)
            }
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
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        steps.forEachIndexed { index, step ->
            AssistChip(
                onClick = { onSelect(index) },
                label = { Text(step.label, maxLines = 1) },
                leadingIcon =
                    if (index < selectedIndex) {
                        {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                            )
                        }
                    } else {
                        null
                    },
                modifier = Modifier.weight(1f),
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
    Surface(tonalElevation = 3.dp) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (showBack) {
                OutlinedButton(onClick = onBack, modifier = Modifier.weight(1f)) { Text("Back") }
            }
            Button(onClick = onNext, modifier = Modifier.weight(1f)) {
                Text(if (isLast) "Finish setup" else "Continue")
            }
        }
    }
}

@Composable
private fun StartStep(settings: AppSettings) {
    SetupPage {
        Text("Orbin setup", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(
            text = "Choose boards to follow, tune playback, and lock down the defaults before browsing.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SignalPanel(settings)
    }
}

@Composable
private fun SignalPanel(settings: AppSettings) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainerLow, RoundedCornerShape(6.dp))
                .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SetupSignal(Icons.Filled.Palette, "Display", settings.themeMode.name.lowercase())
        SetupSignal(Icons.Filled.PlayCircle, "Media", if (settings.autoplayVideos) "autoplay on" else "manual playback")
        SetupSignal(Icons.Filled.Security, "Network", "https only")
        SetupSignal(Icons.Filled.Lock, "App lock", if (settings.biometricLockEnabled) "biometric" else "off")
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
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
        Text(title, modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
        Text(value, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
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
        OnboardingBoardsState.Loading -> LoadingView()
        is OnboardingBoardsState.Error -> ErrorView(state.message, onRetry = onRetry)
        is OnboardingBoardsState.Success ->
            if (state.boards.isEmpty()) {
                EmptyView("No boards available")
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
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
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
        Text("Subscribe to boards", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
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
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        }

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable { onSubscriptionChange(board.id.value, !isSubscribed) }
                .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        BoardMonogram(board.id.value)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "/${board.id.value}/",
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = board.title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Text(
                text = boardDescription,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        IconButton(onClick = { onFavoriteChange(board.id.value, !isFavorite) }) {
            Icon(
                imageVector = if (isFavorite) Icons.Filled.Star else Icons.Outlined.StarBorder,
                contentDescription = if (isFavorite) "Remove favorite" else "Favorite board",
                tint = favoriteTint,
            )
        }
        Switch(
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
                .background(color, RoundedCornerShape(4.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = id.take(1).uppercase(),
            color = Color.White,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleMedium,
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
        PreferenceHeader(Icons.Filled.Palette, "Display preferences", "Theme and contrast")
        SurfacePanel {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AppThemeMode.entries.forEach { mode ->
                    FilterChip(
                        selected = settings.themeMode == mode,
                        onClick = { onThemeMode(mode) },
                        label = { Text(mode.name.lowercase().replaceFirstChar { it.uppercase() }) },
                    )
                }
            }
            PreferenceSwitch("Dynamic color", "Follow Pixel system palette", settings.dynamicColor, onDynamicColor)
            PreferenceSwitch("AMOLED black", "Use true black surfaces in dark mode", settings.amoled, onAmoled)
        }
    }
}

@Composable
private fun MediaStep(
    settings: AppSettings,
    onAutoplay: (Boolean) -> Unit,
    onMute: (Boolean) -> Unit,
    onPreload: (Boolean) -> Unit,
) {
    SetupPage {
        PreferenceHeader(Icons.Filled.PlayCircle, "Media behavior", "Images, videos, and thread browsing")
        SurfacePanel {
            PreferenceSwitch(
                "Autoplay videos",
                "Start video playback as media comes into view",
                settings.autoplayVideos,
                onAutoplay,
            )
            PreferenceSwitch("Mute by default", "Keep videos quiet until you opt in", settings.muteByDefault, onMute)
            PreferenceSwitch("Preload images", "Load nearby media ahead of time", settings.preloadImages, onPreload)
        }
    }
}

@Composable
private fun PrivacyStep(
    settings: AppSettings,
    onDoh: (Boolean) -> Unit,
    onBiometricLock: (Boolean) -> Unit,
    onSaveRecentSearches: (Boolean) -> Unit,
) {
    SetupPage {
        PreferenceHeader(Icons.Filled.Security, "Privacy & network", "Transport security and local access")
        SurfacePanel {
            PreferenceSwitch(
                "HTTPS only",
                "Always enforced for board traffic and downloads",
                true,
                {},
            )
            PreferenceSwitch(
                "DNS over HTTPS",
                "Resolve through the app's secure DNS setting",
                settings.dohEnabled,
                onDoh,
            )
            PreferenceSwitch(
                "Lock with biometrics",
                "Require fingerprint or device credential on launch",
                settings.biometricLockEnabled,
                onBiometricLock,
            )
            PreferenceSwitch(
                "Save recent searches",
                "Keep search suggestions on this device",
                settings.saveRecentSearches,
                onSaveRecentSearches,
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
        Text("Ready to browse", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(
            text = "Your setup is saved. You can run this again from Settings whenever you want.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SurfacePanel {
            SetupSignal(Icons.Filled.Notifications, "Subscribed boards", subscribedCount.toString())
            SetupSignal(Icons.Filled.Star, "Favorite boards", favoriteCount.toString())
            SetupSignal(Icons.Filled.Check, "Setup", "complete")
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
                .background(MaterialTheme.colorScheme.surfaceContainerLow, RoundedCornerShape(6.dp))
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
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Column {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun MetaChip(text: String) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        shape = RoundedCornerShape(4.dp),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

private const val HUE_DEGREES = 360

private fun boardColor(id: String): Color {
    val hue = (((id.hashCode() % HUE_DEGREES) + HUE_DEGREES) % HUE_DEGREES).toFloat()
    return Color.hsv(hue, saturation = 0.42f, value = 0.48f)
}
