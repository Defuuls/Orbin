package com.orbin.minimal

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.orbin.feature.gallery.GalleryScreen
import com.orbin.feature.thread.NextThreadScreen
import com.orbin.uinext.MessageScreen
import kotlinx.serialization.Serializable

@Composable
fun MinimalNavHost(boardsViewModel: MinimalBoardsViewModel = hiltViewModel()) {
    val navController = rememberNavController()
    val hasSubscriptions by boardsViewModel.hasSubscriptions.collectAsStateWithLifecycle()

    NavHost(navController = navController, startDestination = MinimalRoute.Bootstrap) {
        composable<MinimalRoute.Bootstrap> {
            LaunchedEffect(hasSubscriptions) {
                val resolved = hasSubscriptions ?: return@LaunchedEffect
                navController.navigate(if (resolved) MinimalRoute.Feed else MinimalRoute.Boards) {
                    popUpTo<MinimalRoute.Bootstrap> { inclusive = true }
                }
            }
            MessageScreen(
                title = stringResource(R.string.minimal_app_name),
                subtitle =
                    if (hasSubscriptions == false) {
                        stringResource(R.string.minimal_choose_boards)
                    } else {
                        stringResource(R.string.minimal_preparing)
                    },
            )
        }

        composable<MinimalRoute.Feed> {
            MinimalFeedScreen(
                onOpenThread = { provider, board, thread, title ->
                    navController.navigate(MinimalRoute.Thread(provider, board, thread, title))
                },
                onOpenBoards = { navController.navigate(MinimalRoute.Boards) },
            )
        }

        composable<MinimalRoute.Boards> {
            MinimalBoardsScreen(
                onBack = {
                    if (!navController.navigateUp()) {
                        navController.navigate(MinimalRoute.Feed) {
                            launchSingleTop = true
                        }
                    }
                },
                viewModel = boardsViewModel,
            )
        }

        composable<MinimalRoute.Thread> { backStackEntry ->
            val route = backStackEntry.toRoute<MinimalRoute.Thread>()
            NextThreadScreen(
                onOpenMedia = { index ->
                    navController.navigate(
                        MinimalRoute.Media(route.provider, route.board, route.thread, index),
                    )
                },
            )
        }

        composable<MinimalRoute.Media> {
            GalleryScreen(onClose = navController::navigateUp)
        }
    }
}

sealed interface MinimalRoute {
    @Serializable
    data object Bootstrap : MinimalRoute

    @Serializable
    data object Feed : MinimalRoute

    @Serializable
    data object Boards : MinimalRoute

    @Serializable
    data class Thread(
        val provider: String,
        val board: String,
        val thread: Long,
        val title: String,
    ) : MinimalRoute

    @Serializable
    data class Media(
        val provider: String,
        val board: String,
        val thread: Long,
        val startIndex: Int,
    ) : MinimalRoute
}
