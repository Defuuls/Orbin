package com.orbin.feature.onboarding

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.orbin.uinext.NextTheme
import com.orbin.uinext.OnboardingBoard
import com.orbin.uinext.OnboardingBoards
import com.orbin.uinext.OnboardingScreen as SharedOnboardingScreen

/** First run: the shared board picker over [OnboardingViewModel]. */
@Composable
fun OnboardingScreen(
    onFinish: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val boards by viewModel.boards.collectAsStateWithLifecycle()
    val subscribed by viewModel.subscribedBoardIds.collectAsStateWithLifecycle()
    val selectedProvider by viewModel.selectedProvider.collectAsStateWithLifecycle()
    val providers = viewModel.providers
    val siteNames = remember(providers) { providers.map { it.metadata.displayName } }

    NextTheme {
        SharedOnboardingScreen(
            boards = remember(boards) { boards.toShared() },
            following = subscribed,
            sites = siteNames,
            selectedSite = providers.indexOfFirst { it.metadata.id == selectedProvider.metadata.id },
            onSelectSite = { index -> viewModel.setSelectedProvider(providers[index]) },
            onFollow = viewModel::setSubscribed,
            onRetry = viewModel::loadBoards,
            onStart = {
                viewModel.complete()
                onFinish()
            },
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
