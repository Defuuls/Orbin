package com.orbin.app.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.orbin.feature.board.BoardScreen
import com.orbin.feature.bookmarks.BookmarksScreen
import com.orbin.feature.downloads.DownloadsScreen
import com.orbin.feature.gallery.GalleryScreen
import com.orbin.feature.history.HistoryScreen
import com.orbin.feature.home.HomeScreen
import com.orbin.feature.search.SearchScreen
import com.orbin.feature.settings.SettingsScreen
import com.orbin.feature.thread.ThreadScreen

private const val TRANSITION_MS = 300

/**
 * The single navigation graph for the app. Slide + fade transitions give a smooth, native feel;
 * predictive back is enabled at the manifest level so the system back gesture animates these
 * destinations.
 */
@Composable
fun OrbinNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
) {
    val openThread: (String, String, Long, String) -> Unit = { provider, board, thread, title ->
        navController.navigate(Route.Thread(provider, board, thread, title))
    }

    NavHost(
        navController = navController,
        startDestination = Route.Home,
        modifier = modifier,
        enterTransition = {
            slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(TRANSITION_MS))
        },
        exitTransition = {
            slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(TRANSITION_MS))
        },
        popEnterTransition = {
            slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.End, tween(TRANSITION_MS))
        },
        popExitTransition = {
            slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.End, tween(TRANSITION_MS))
        },
    ) {
        composable<Route.Home> {
            HomeScreen(
                onOpenBoard = { provider, board, title ->
                    navController.navigate(Route.Board(provider, board, title))
                },
                onOpenSettings = { navController.navigate(Route.Settings) },
            )
        }

        composable<Route.Search> { SearchScreen(onOpenThread = openThread) }

        composable<Route.Bookmarks> { BookmarksScreen(onOpenThread = openThread) }

        composable<Route.History> { HistoryScreen(onOpenThread = openThread) }

        composable<Route.Board> {
            BoardScreen(onOpenThread = openThread, onBack = navController::navigateUp)
        }

        composable<Route.Thread> { backStackEntry ->
            val route = backStackEntry.toRoute<Route.Thread>()
            ThreadScreen(
                onBack = navController::navigateUp,
                onOpenMedia = { index ->
                    navController.navigate(Route.Gallery(route.provider, route.board, route.thread, index))
                },
            )
        }

        composable<Route.Gallery> {
            GalleryScreen(onClose = navController::navigateUp)
        }

        composable<Route.Downloads> {
            DownloadsScreen(onBack = navController::navigateUp)
        }

        composable<Route.Settings> {
            SettingsScreen(
                onBack = navController::navigateUp,
                onOpenDownloads = { navController.navigate(Route.Downloads) },
            )
        }
    }
}
