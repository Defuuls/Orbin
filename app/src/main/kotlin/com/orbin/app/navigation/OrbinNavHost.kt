package com.orbin.app.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.orbin.app.LocalOrbinSnackbarHostState
import com.orbin.core.model.ThreadPresentation
import com.orbin.feature.board.NextBoardScreen
import com.orbin.feature.downloads.DownloadsScreen
import com.orbin.feature.gallery.GalleryScreen
import com.orbin.feature.gallery.NextAllMediaScreen
import com.orbin.feature.home.BoardGalleryScreen
import com.orbin.feature.home.NextFeedWithSiteSwitcherScreen
import com.orbin.feature.onboarding.OnboardingScreen
import com.orbin.feature.search.SearchScreen
import com.orbin.feature.settings.NextSettingsScreen
import com.orbin.feature.thread.NextThreadScreen
import com.orbin.uinext.NextChromeHost

internal const val THREAD_MEDIA_SCROLL_INDEX_KEY = "threadMediaScrollIndex"
internal const val NO_THREAD_MEDIA_SCROLL_INDEX = -1

@Composable
fun OrbinNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
    startDestination: Route = Route.NextFeed,
    chromeHidesOnScroll: Boolean = true,
    twoPaneBoardDetail: Boolean = false,
    threadPresentation: ThreadPresentation = ThreadPresentation.PAGE,
    onChromeVisibleChange: (Boolean) -> Unit = {},
) {
    val openThread: (String, String, Long, String) -> Unit = { provider, board, thread, title ->
        navController.navigate(Route.Thread(provider, board, thread, title))
    }

    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier,
        enterTransition = { nextEnter(threadPresentation) },
        exitTransition = { nextExit(threadPresentation) },
        popEnterTransition = { nextPopEnter(threadPresentation) },
        popExitTransition = { nextPopExit(threadPresentation) },
    ) {
        composable<Route.NextFeed> {
            NextFeedWithSiteSwitcherScreen(
                onOpenThread = openThread,
                onOpenSettings = { navController.navigate(Route.Settings) },
                hideRailOnScroll = chromeHidesOnScroll,
                onChromeVisibleChange = onChromeVisibleChange,
                onOpenBoards = { navController.navigateToTab(Route.BoardGallery) },
                onOpenMedia = { navController.navigateToTab(Route.AllMedia) },
            )
        }

        composable<Route.BoardGallery> {
            BoardGalleryScreen(
                onOpenBoard = { provider, board, title ->
                    navController.navigate(Route.Board(provider, board, title))
                },
                onOpenFeed = { navController.navigateToTab(Route.NextFeed) },
                onOpenMedia = { navController.navigateToTab(Route.AllMedia) },
                onOpenSettings = { navController.navigate(Route.Settings) },
                hideRailOnScroll = chromeHidesOnScroll,
                onChromeVisibleChange = onChromeVisibleChange,
            )
        }

        composable<Route.Search> {
            NextChromeHost(
                where = "Search",
            ) { padding ->
                Box(Modifier.fillMaxSize().padding(padding)) {
                    SearchScreen(onOpenThread = openThread)
                }
            }
        }

        composable<Route.AllMedia> {
            NextAllMediaScreen(
                hideRailOnScroll = chromeHidesOnScroll,
                onChromeVisibleChange = onChromeVisibleChange,
                onOpenMedia = { provider, board, thread, _ ->
                    openThread(provider, board, thread, "No.$thread")
                },
                onOpenFeed = { navController.navigateToTab(Route.NextFeed) },
                onOpenBoards = { navController.navigateToTab(Route.BoardGallery) },
                onOpenSettings = { navController.navigate(Route.Settings) },
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
                    onBack = navController::navigateUp,
                )
            } else {
                NextBoardScreen(
                    onOpenThread = openThread,
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
            NextChromeHost(
                where = "Downloads",
            ) { padding ->
                Box(Modifier.fillMaxSize().padding(padding)) {
                    DownloadsScreen(onBack = navController::navigateUp)
                }
            }
        }

        composable<Route.Settings> {
            NextSettingsScreen(
                snackbarHostState = LocalOrbinSnackbarHostState.current,
                onOpenSearch = { navController.navigate(Route.Search) },
                onOpenDownloads = { navController.navigate(Route.Downloads) },
            )
        }

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

/**
 * Switches to one of the three tabs. Each tab sits directly on top of the Feed rather than on top
 * of whatever was open, so Back from Media or Boards always lands on the Feed and switching tabs
 * never piles screens up.
 */
private fun NavHostController.navigateToTab(route: Route) {
    navigate(route) {
        popUpTo(Route.NextFeed) { inclusive = false }
        launchSingleTop = true
    }
}
