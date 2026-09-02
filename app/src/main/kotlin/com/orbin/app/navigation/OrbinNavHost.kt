package com.orbin.app.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.orbin.app.LocalOrbinSnackbarHostState
import com.orbin.core.model.ThreadPresentation
import com.orbin.feature.board.NextBoardScreen
import com.orbin.feature.downloads.DownloadsScreen
import com.orbin.feature.gallery.GalleryBrowserScreen
import com.orbin.feature.gallery.GalleryScreen
import com.orbin.feature.gallery.NextAllMediaScreen
import com.orbin.feature.history.HistoryScreen
import com.orbin.feature.home.BoardGalleryScreen
import com.orbin.feature.home.HomeScreen
import com.orbin.feature.home.NextFeedScreen
import com.orbin.feature.onboarding.OnboardingScreen
import com.orbin.feature.search.SearchScreen
import com.orbin.feature.settings.NextSettingsScreen
import com.orbin.feature.settings.SubscriptionsScreen
import com.orbin.feature.thread.NextThreadScreen

private const val TRANSITION_MS = 300

internal const val THREAD_MEDIA_SCROLL_INDEX_KEY = "threadMediaScrollIndex"
internal const val NO_THREAD_MEDIA_SCROLL_INDEX = -1

@Composable
fun OrbinNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
    startDestination: Route = Route.NextFeed,
    chromeHidesOnScroll: Boolean = false,
    twoPaneBoardDetail: Boolean = false,
    subscribedFeedScrollToTopRequest: Int = 0,
    subscribedFeedRefreshRequest: Int = 0,
    threadPresentation: ThreadPresentation = ThreadPresentation.PAGE,
    onChromeVisibleChange: (Boolean) -> Unit = {},
    onOpenCommands: () -> Unit = {},
    feedFilter: String = "",
    onClearFeedFilter: () -> Unit = {},
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
                onOpenSettings = { navController.navigate(Route.Settings()) },
            )
        }

        composable<Route.NextFeed> {
            NextFeedScreen(
                onOpenThread = openThread,
                onOpenCommands = onOpenCommands,
                onOpenSettings = { navController.navigate(Route.Settings()) },
                hideRailOnScroll = chromeHidesOnScroll,
                onChromeVisibleChange = onChromeVisibleChange,
                scrollToTopRequest = subscribedFeedScrollToTopRequest,
                refreshRequest = subscribedFeedRefreshRequest,
                filter = feedFilter,
                onClearFilter = onClearFeedFilter,
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

        composable<Route.GalleryBrowser> { backStackEntry ->
            val mediaScrollIndex by
                backStackEntry.savedStateHandle
                    .getStateFlow(THREAD_MEDIA_SCROLL_INDEX_KEY, NO_THREAD_MEDIA_SCROLL_INDEX)
                    .collectAsStateWithLifecycle()

            GalleryBrowserScreen(
                onOpenMedia = { provider, board, thread, index ->
                    navController.navigate(Route.Gallery(provider, board, thread, index))
                },
                onOpenThread = openThread,
                onOpenAllMedia = { navController.navigate(Route.AllMedia) },
                mediaScrollIndex = mediaScrollIndex.takeIf { it != NO_THREAD_MEDIA_SCROLL_INDEX },
                onMediaScrollConsumed = {
                    backStackEntry.savedStateHandle[THREAD_MEDIA_SCROLL_INDEX_KEY] =
                        NO_THREAD_MEDIA_SCROLL_INDEX
                },
            )
        }

        composable<Route.AllMedia> {
            NextAllMediaScreen(
                hideRailOnScroll = chromeHidesOnScroll,
                onChromeVisibleChange = onChromeVisibleChange,
                onOpenMedia = { provider, board, thread, attachmentId ->
                    navController.navigate(
                        Route.Gallery(provider, board, thread, startIndex = 0, attachmentId = attachmentId),
                    )
                },
                onOpenCommands = onOpenCommands,
            )
        }

        composable<Route.Board> { backStackEntry ->
            val mediaScrollIndex by
                backStackEntry.savedStateHandle
                    .getStateFlow(THREAD_MEDIA_SCROLL_INDEX_KEY, NO_THREAD_MEDIA_SCROLL_INDEX)
                    .collectAsStateWithLifecycle()
            var paneThread by
                rememberSaveable(stateSaver = threadRouteSaver) { mutableStateOf<Route.Thread?>(null) }

            LaunchedEffect(twoPaneBoardDetail) {
                if (!twoPaneBoardDetail) {
                    paneThread?.let { thread ->
                        paneThread = null
                        navController.navigate(thread)
                    }
                }
            }

            if (twoPaneBoardDetail) {
                BoardDetailTwoPane(
                    selectedThread = paneThread,
                    onThreadSelected = { paneThread = it },
                    onOpenGallery = { provider, board, thread, index ->
                        navController.navigate(Route.Gallery(provider, board, thread, index))
                    },
                    mediaScrollIndex = mediaScrollIndex.takeIf { it != NO_THREAD_MEDIA_SCROLL_INDEX },
                    onMediaScrollConsumed = {
                        backStackEntry.savedStateHandle[THREAD_MEDIA_SCROLL_INDEX_KEY] =
                            NO_THREAD_MEDIA_SCROLL_INDEX
                    },
                    onOpenCommands = onOpenCommands,
                    onBack = navController::navigateUp,
                )
            } else {
                NextBoardScreen(
                    onOpenThread = openThread,
                    onOpenCommands = onOpenCommands,
                    hideRailOnScroll = chromeHidesOnScroll,
                    onChromeVisibleChange = onChromeVisibleChange,
                )
            }
        }

        composable<Route.Thread> { backStackEntry ->
            val route = backStackEntry.toRoute<Route.Thread>()
            val mediaScrollIndex by
                backStackEntry.savedStateHandle
                    .getStateFlow(THREAD_MEDIA_SCROLL_INDEX_KEY, NO_THREAD_MEDIA_SCROLL_INDEX)
                    .collectAsStateWithLifecycle()
            NextThreadScreen(
                onOpenMedia = { index ->
                    navController.navigate(Route.Gallery(route.provider, route.board, route.thread, index))
                },
                mediaScrollIndex = mediaScrollIndex.takeIf { it != NO_THREAD_MEDIA_SCROLL_INDEX },
                onMediaScrollConsumed = {
                    backStackEntry.savedStateHandle[THREAD_MEDIA_SCROLL_INDEX_KEY] =
                        NO_THREAD_MEDIA_SCROLL_INDEX
                },
                onOpenCommands = onOpenCommands,
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

        composable<Route.Downloads> { DownloadsScreen(onBack = navController::navigateUp) }

        composable<Route.Settings> { backStackEntry ->
            NextSettingsScreen(
                onOpenCommands = onOpenCommands,
                onRunSetup = { navController.navigate(Route.Onboarding) },
                snackbarHostState = LocalOrbinSnackbarHostState.current,
                focusId = backStackEntry.toRoute<Route.Settings>().focus,
            )
        }

        composable<Route.Subscriptions> { SubscriptionsScreen(onBack = navController::navigateUp) }

        composable<Route.Onboarding> {
            OnboardingScreen(
                onFinish = {
                    navController.navigate(Route.NextFeed) {
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

private fun NavDestination.slidesOver(threadPresentation: ThreadPresentation): Boolean =
    hasRoute(Route.Settings::class) ||
        (threadPresentation == ThreadPresentation.OVERLAY && hasRoute(Route.Thread::class))

internal val threadRouteSaver =
    listSaver<Route.Thread?, Any>(
        save = { thread ->
            thread?.let { listOf(it.provider, it.board, it.thread, it.title) } ?: emptyList()
        },
        restore = { fields ->
            Route.Thread(
                provider = fields[0] as String,
                board = fields[1] as String,
                thread = fields[2] as Long,
                title = fields[3] as String,
            )
        },
    )
