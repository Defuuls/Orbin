package com.orbin.feature.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.orbin.core.model.AppSettings
import com.orbin.core.model.AppThemeMode
import com.orbin.core.model.Board
import com.orbin.core.ui.state.EmptyView
import com.orbin.core.ui.state.ErrorView
import com.orbin.core.ui.state.LoadingView

private enum class OnboardingStep(
    val title: String,
) {
    WELCOME("Welcome"),
    BOARDS("Pick boards"),
    APPEARANCE("Appearance"),
    MEDIA("Media"),
    PRIVACY("Privacy & network"),
    DONE("All set"),
}

/**
 * First-run setup wizard. Walks the user through subscribing to boards and the appearance / media /
 * privacy preferences, then marks onboarding complete so it never auto-shows again. [onFinish] is
 * invoked after the flag is persisted so the host can navigate into the app.
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

    val steps = OnboardingStep.entries
    var index by rememberSaveable { mutableIntStateOf(0) }
    val step = steps[index]
    val isLast = index == steps.lastIndex

    Scaffold(
        topBar = {
            Column {
                TopAppBar(title = { Text(step.title) })
                LinearProgressIndicator(
                    progress = { (index + 1).toFloat() / steps.size },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        bottomBar = {
            OnboardingBottomBar(
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
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (step) {
                OnboardingStep.WELCOME -> WelcomeStep()
                OnboardingStep.BOARDS ->
                    BoardsStep(
                        state = boards,
                        subscribedBoardIds = subscribed,
                        favoriteBoardIds = favorites,
                        onSubscriptionChange = viewModel::setSubscribed,
                        onFavoriteChange = viewModel::setFavorite,
                        onRetry = viewModel::loadBoards,
                    )
                OnboardingStep.APPEARANCE ->
                    AppearanceStep(settings, viewModel::setThemeMode, viewModel::setDynamicColor, viewModel::setAmoled)
                OnboardingStep.MEDIA ->
                    MediaStep(settings, viewModel::setAutoplay, viewModel::setMute, viewModel::setPreload)
                OnboardingStep.PRIVACY ->
                    PrivacyStep(settings, viewModel::setHttpsOnly, viewModel::setDoh, viewModel::setBiometricLock)
                OnboardingStep.DONE -> DoneStep()
            }
        }
    }
}

@Composable
private fun OnboardingBottomBar(
    showBack: Boolean,
    isLast: Boolean,
    onBack: () -> Unit,
    onNext: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (showBack) {
            OutlinedButton(onClick = onBack, modifier = Modifier.weight(1f)) { Text("Back") }
        }
        Button(onClick = onNext, modifier = Modifier.weight(1f)) {
            Text(if (isLast) "Finish" else "Next")
        }
    }
}

@Composable
private fun WelcomeStep() {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Welcome to Orbin", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        Text(
            text =
                "Let's get you set up — pick a few boards to follow and tune your preferences. " +
                    "You can change all of this later in Settings.",
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    item {
                        Text(
                            text = "Subscribe for thread updates; tap the star to favourite.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
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
                        HorizontalDivider()
                    }
                }
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
    ListItem(
        headlineContent = { Text("/${board.id.value}/ - ${board.title}") },
        supportingContent = { if (board.description.isNotBlank()) Text(board.description) },
        trailingContent = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                IconButton(onClick = { onFavoriteChange(board.id.value, !isFavorite) }) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Filled.Star else Icons.Outlined.StarBorder,
                        contentDescription = if (isFavorite) "Remove favourite" else "Favourite board",
                    )
                }
                Switch(
                    checked = isSubscribed,
                    onCheckedChange = { onSubscriptionChange(board.id.value, it) },
                )
            }
        },
    )
}

@Composable
private fun AppearanceStep(
    settings: AppSettings,
    onThemeMode: (AppThemeMode) -> Unit,
    onDynamicColor: (Boolean) -> Unit,
    onAmoled: (Boolean) -> Unit,
) {
    StepColumn {
        Text("Theme", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AppThemeMode.entries.forEach { mode ->
                FilterChip(
                    selected = settings.themeMode == mode,
                    onClick = { onThemeMode(mode) },
                    label = { Text(mode.name.lowercase().replaceFirstChar { it.uppercase() }) },
                )
            }
        }
        SwitchRow("Dynamic color", settings.dynamicColor, onDynamicColor)
        SwitchRow("AMOLED black", settings.amoled, onAmoled)
    }
}

@Composable
private fun MediaStep(
    settings: AppSettings,
    onAutoplay: (Boolean) -> Unit,
    onMute: (Boolean) -> Unit,
    onPreload: (Boolean) -> Unit,
) {
    StepColumn {
        SwitchRow("Autoplay videos", settings.autoplayVideos, onAutoplay)
        SwitchRow("Mute by default", settings.muteByDefault, onMute)
        SwitchRow("Preload images", settings.preloadImages, onPreload)
    }
}

@Composable
private fun PrivacyStep(
    settings: AppSettings,
    onHttpsOnly: (Boolean) -> Unit,
    onDoh: (Boolean) -> Unit,
    onBiometricLock: (Boolean) -> Unit,
) {
    StepColumn {
        SwitchRow("HTTPS only", settings.httpsOnly, onHttpsOnly)
        SwitchRow("DNS over HTTPS", settings.dohEnabled, onDoh)
        SwitchRow("Lock with biometrics", settings.biometricLockEnabled, onBiometricLock)
    }
}

@Composable
private fun DoneStep() {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("You're all set", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        Text(
            text = "Tap Finish to start browsing. Tweak anything anytime from Settings.",
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun StepColumn(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        content()
    }
}

@Composable
private fun SwitchRow(
    label: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    ListItem(
        headlineContent = { Text(label) },
        trailingContent = { Switch(checked = checked, onCheckedChange = onChange) },
    )
}
