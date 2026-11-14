package com.orbin.feature.onboarding

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.orbin.core.model.AppThemeMode
import com.orbin.uinext.NextTheme
import com.orbin.uinext.OnboardingBoard
import com.orbin.uinext.OnboardingBoards
import com.orbin.uinext.OnboardingSetupConfig
import com.orbin.uinext.SetupThemeOption
import com.orbin.uinext.canonicalNextPalette
import com.orbin.uinext.OnboardingScreen as SharedOnboardingScreen

/** First run: the shared board picker over [OnboardingViewModel]. */
@Composable
fun OnboardingScreen(
    onFinish: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val boards by viewModel.boards.collectAsStateWithLifecycle()
    val subscribed by viewModel.subscribedBoardIds.collectAsStateWithLifecycle()
    val totalFollowing by viewModel.totalFollowing.collectAsStateWithLifecycle()
    val setupState by viewModel.setupState.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val selectedProvider by viewModel.selectedProvider.collectAsStateWithLifecycle()
    val providers = viewModel.providers
    val siteNames = remember(providers) { providers.map { it.metadata.displayName } }

    LaunchedEffect(setupState.completed) {
        if (setupState.completed) onFinish()
    }

    val dark =
        when (settings.themeMode) {
            AppThemeMode.SYSTEM -> isSystemInDarkTheme()
            AppThemeMode.LIGHT -> false
            AppThemeMode.DARK -> true
        }
    val palette = canonicalNextPalette(dark, settings.amoled)
    NextTheme(darkTheme = palette.dark, amoled = palette.amoled, palette = palette) {
        SharedOnboardingScreen(
            boards = remember(boards) { boards.toShared() },
            following = subscribed,
            sites = siteNames,
            selectedSite = providers.indexOfFirst { it.metadata.id == selectedProvider.metadata.id },
            onSelectSite = { index -> viewModel.setSelectedProvider(providers[index]) },
            onFollow = viewModel::setSubscribed,
            onRetry = viewModel::loadBoards,
            onStart = viewModel::complete,
            totalFollowing = totalFollowing,
            isSaving = setupState.isSaving,
            errorMessage = setupState.errorMessage,
            canStart = totalFollowing > 0 && !setupState.isSaving,
            setupWizard =
                OnboardingSetupConfig(
                    theme =
                        when {
                            settings.themeMode == AppThemeMode.SYSTEM -> SetupThemeOption.SYSTEM
                            settings.themeMode == AppThemeMode.LIGHT -> SetupThemeOption.LIGHT
                            settings.amoled -> SetupThemeOption.AMOLED
                            else -> SetupThemeOption.DARK
                        },
                    hideNsfwBoards = settings.hideNsfwBoards,
                    coverViolentMedia = settings.coverViolentMedia,
                    onThemeChange = { option ->
                        when (option) {
                            SetupThemeOption.SYSTEM -> viewModel.setTheme(AppThemeMode.SYSTEM, false)
                            SetupThemeOption.LIGHT -> viewModel.setTheme(AppThemeMode.LIGHT, false)
                            SetupThemeOption.DARK -> viewModel.setTheme(AppThemeMode.DARK, false)
                            SetupThemeOption.AMOLED -> viewModel.setTheme(AppThemeMode.DARK, true)
                        }
                    },
                    onHideNsfwChange = viewModel::setHideNsfwBoards,
                    onCoverViolentMediaChange = viewModel::setCoverViolentMedia,
                ),
        )
    }
}

private fun OnboardingBoardsState.toShared(): OnboardingBoards =
    when (this) {
        OnboardingBoardsState.Loading -> OnboardingBoards.Loading
        is OnboardingBoardsState.Error -> OnboardingBoards.Failed(message)
        is OnboardingBoardsState.Success ->
            OnboardingBoards.Ready(
                boards.map { OnboardingBoard(it.id.value, it.title, it.description, it.isNsfw) },
            )
    }
