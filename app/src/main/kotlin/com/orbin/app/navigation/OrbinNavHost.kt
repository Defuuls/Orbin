package com.orbin.app.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.orbin.feature.board.BoardScreen
import com.orbin.feature.bookmarks.BookmarksScreen
import com.orbin.feature.downloads.DownloadsScreen
import com.orbin.feature.gallery.GalleryBrowserScreen
import com.orbin.feature.gallery.GalleryScreen
import com.orbin.feature.history.HistoryScreen
import com.orbin.feature.home.BoardGalleryScreen
import com.orbin.feature.home.HomeScreen
import com.orbin.feature.home.SubscribedFeedScreen
import com.orbin.feature.onboarding.OnboardingScreen
import com.orbin.feature.search.SearchScreen
import com.orbin.feature.settings.SettingsScreen
import com.orbin.feature.settings.SubscriptionsScreen
import com.orbin.feature.thread.ThreadScreen
import com.orbin.app.browser.VanadiumBrowserScreen

private const val TRANSITION_MS = 300
private const val THREAD_MEDIA_SCROLL_INDEX_KEY = "threadMediaScrollIndex"
private const val NO_THREAD_MEDIA_SCROLL_INDEX = -1

/**
 * The single navigation graph for the app. Slide + fade transitions give a smooth, native feel;
 * predictive back is enabled at the manifest level so the system back gesture animates these
 * destinations.
 */
@Composable
fun OrbinNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
    startDestination: Route = Route.SubscribedFeed,
) {
    val openThread: (String, String, Long, String) -> Unit = { provider, board, thread, title ->
        navController.navigate(Route.Thread(provider, board, thread, title))
    }

    NavHost(
        navController = navController,
        startDestination = startDestination,
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
                onOpenBoardGallery = { navController.navigate(Route.BoardGallery) },
                onOpenVanadiumBrowser = { navController.navigate(Route.VanadiumBrowser) },
            )
        }

        composable<Route.SubscribedFeed> {
            SubscribedFeedScreen(
                onOpenThread = openThread,
                onOpenBoards = { navController.navigate(Route.BoardGallery) },
                onOpenSettings = { navController.navigate(Route.Settings) },
                onOpenVanadiumBrowser = { navController.navigate(Route.VanadiumBrowser) },
            )
        }

        composable<Route.BoardGallery> {
            BoardGalleryScreen(
                onBack = navController::navigateUp,
                onOpenBoard = { provider, board, title ->
                    navController.navigate(Route.Board(provider, board, title))
                },
            )
        }

        composable<Route.Search> { SearchScreen(onOpenThread = openThread) }

        composable<Route.Bookmarks> { BookmarksScreen(onOpenThread = openThread) }

        composable<Route.History> { HistoryScreen(onOpenThread = openThread) }

        composable<Route.GalleryBrowser> {
            GalleryBrowserScreen(
                onOpenMedia = { provider, board, thread, index ->
                    navController.navigate(Route.Gallery(provider, board, thread, index))
                },
            )
        }

        composable<Route.Board> {
            BoardScreen(onOpenThread = openThread, onBack = navController::navigateUp)
        }

        composable<Route.Thread> { backStackEntry ->
            val route = backStackEntry.toRoute<Route.Thread>()
            val mediaScrollIndex by
                backStackEntry.savedStateHandle
                    .getStateFlow(THREAD_MEDIA_SCROLL_INDEX_KEY, NO_THREAD_MEDIA_SCROLL_INDEX)
                    .collectAsStateWithLifecycle()

            ThreadScreen(
                onBack = navController::navigateUp,
                onOpenMedia = { index ->
                    navController.navigate(Route.Gallery(route.provider, route.board, route.thread, index))
                },
                mediaScrollIndex = mediaScrollIndex.takeIf { it != NO_THREAD_MEDIA_SCROLL_INDEX },
                onMediaScrollConsumed = {
                    backStackEntry.savedStateHandle[THREAD_MEDIA_SCROLL_INDEX_KEY] =
                        NO_THREAD_MEDIA_SCROLL_INDEX
                },
            )
        }

        composable<Route.Gallery> {
            GalleryScreen(
                onClose = navController::navigateUp,
                onMediaPageChanged = { page ->
                    navController.previousBackStackEntry
                        ?.savedStateHandle
                        ?.set(THREAD_MEDIA_SCROLL_INDEX_KEY, page)
                },
            )
        }

        composable<Route.Downloads> {
            DownloadsScreen(onBack = navController::navigateUp)
        }

        composable<Route.Settings> {
            SettingsScreen(
                onBack = navController::navigateUp,
                onOpenDownloads = { navController.navigate(Route.Downloads) },
                onOpenSubscriptions = { navController.navigate(Route.Subscriptions) },
                onOpenSetup = { navController.navigate(Route.Onboarding) },
            )
        }

        composable<Route.VanadiumBrowser> {
            VanadiumBrowserScreen(onClose = navController::navigateUp)
        }

        composable<Route.Subscriptions> {
            SubscriptionsScreen(onBack = navController::navigateUp)
        }

        composable<Route.Onboarding> {
            OnboardingScreen(
                onFinish = {
                    navController.navigate(Route.SubscribedFeed) {
                        // Clear onboarding from the back stack so Back from Feed exits the app.
                        popUpTo(navController.graph.id) {
                            inclusive = true
                            saveState = false
                        }
                    }
                },
            )
        }
    }
}
