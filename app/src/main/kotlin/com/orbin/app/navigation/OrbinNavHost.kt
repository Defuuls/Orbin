package com.orbin.app.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.orbin.core.model.ThreadPresentation
import com.orbin.feature.board.BoardScreen
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

private const val TRANSITION_MS = 300
private const val THREAD_MEDIA_SCROLL_INDEX_KEY = "threadMediaScrollIndex"
private const val NO_THREAD_MEDIA_SCROLL_INDEX = -1

/**
 * The single navigation graph for the app. Predictive back is enabled at the manifest level so the
 * system back gesture animates these destinations.
 *
 * Two transition styles are in play. Most destinations *push*: the outgoing screen slides away with
 * the incoming one, the usual Android forward navigation. Settings — and threads, when the user
 * picks that — instead slide in *over* the screen behind, which stays where it is and is revealed
 * again on the way back. NavHost gives the entering destination a higher z-index on push and the
 * departing one a higher z-index on pop, so the overlay is drawn on top in both directions without
 * any extra layering.
 */
@Composable
fun OrbinNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
    startDestination: Route = Route.SubscribedFeed,
    subscribedFeedChromeHidesOnScroll: Boolean = false,
    subscribedFeedShowBoardHeaders: Boolean = true,
    hideSubscribedFeedTopBar: Boolean = false,
    tabletSubscribedFeedLayout: Boolean = false,
    subscribedFeedScrollToTopRequest: Int = 0,
    subscribedFeedRefreshRequest: Int = 0,
    threadPresentation: ThreadPresentation = ThreadPresentation.PAGE,
    onFeedChromeVisibleChange: (Boolean) -> Unit = {},
) {
    val openThread: (String, String, Long, String) -> Unit = { provider, board, thread, title ->
        navController.navigate(Route.Thread(provider, board, thread, title))
    }

    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier,
        enterTransition = {
            if (targetState.destination.slidesOver(threadPresentation)) {
                slideInHorizontally(tween(TRANSITION_MS)) { width -> width }
            } else {
                slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(TRANSITION_MS))
            }
        },
        exitTransition = {
            // Nothing: an overlay slides on top of this screen, so it must stay put underneath.
            if (targetState.destination.slidesOver(threadPresentation)) {
                ExitTransition.None
            } else {
                slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(TRANSITION_MS))
            }
        },
        popEnterTransition = {
            if (initialState.destination.slidesOver(threadPresentation)) {
                EnterTransition.None
            } else {
                slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.End, tween(TRANSITION_MS))
            }
        },
        popExitTransition = {
            if (initialState.destination.slidesOver(threadPresentation)) {
                slideOutHorizontally(tween(TRANSITION_MS)) { width -> width }
            } else {
                slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.End, tween(TRANSITION_MS))
            }
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

        composable<Route.SubscribedFeed> {
            SubscribedFeedScreen(
                onOpenThread = openThread,
                onOpenBoards = { navController.navigate(Route.BoardGallery) },
                onOpenSettings = { navController.navigate(Route.Settings) },
                chromeHidesOnScroll = subscribedFeedChromeHidesOnScroll,
                showTopBar = !hideSubscribedFeedTopBar,
                showBoardHeaders = subscribedFeedShowBoardHeaders,
                tabletFeedLayout = tabletSubscribedFeedLayout,
                scrollToTopRequest = subscribedFeedScrollToTopRequest,
                refreshRequest = subscribedFeedRefreshRequest,
                onChromeVisibleChange = onFeedChromeVisibleChange,
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

        composable<Route.History> { HistoryScreen(onOpenThread = openThread) }

        composable<Route.GalleryBrowser> {
            GalleryBrowserScreen(
                onOpenMedia = { provider, board, thread, index ->
                    navController.navigate(Route.Gallery(provider, board, thread, index))
                },
                onOpenThread = openThread,
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

/**
 * Whether this destination lays itself over the screen behind rather than pushing it aside.
 *
 * Settings always does. Threads do only when the user has asked for it — the default stays the
 * ordinary push, which is what Android users expect of a forward navigation.
 */
private fun NavDestination.slidesOver(threadPresentation: ThreadPresentation): Boolean =
    hasRoute(Route.Settings::class) ||
        (threadPresentation == ThreadPresentation.OVERLAY && hasRoute(Route.Thread::class))
