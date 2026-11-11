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
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.orbin.app.LocalOrbinSnackbarHostState
import com.orbin.app.R
import com.orbin.core.model.ThreadPresentation
import com.orbin.feature.board.NextBoardScreen
import com.orbin.feature.downloads.DownloadsScreen
import com.orbin.feature.gallery.GalleryScreen
import com.orbin.feature.gallery.NextAllMediaScreen
import com.orbin.feature.home.BoardGalleryScreen
import com.orbin.feature.home.NextFeedWithSiteSwitcherScreen
import com.orbin.feature.onboarding.OnboardingScreen
import com.orbin.feature.search.NextSearchScreen
import com.orbin.feature.settings.NextSettingsScreen
import com.orbin.feature.thread.NextThreadScreen
import com.orbin.uinext.NextDestination
import com.orbin.uinext.NextScaffold

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
    onOnboardingCompleted: () -> Unit = {},
) {
    val openThread: (String, String, Long, String) -> Unit = { provider, board, thread, title ->
        navController.navigate(Route.Thread(provider, board, thread, title))
    }

    val uriHandler = LocalUriHandler.current
    val onDestination: (NextDestination) -> Unit = { destination ->
        navController.openDestination(destination)
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
                onOpenThreadVideos = { provider, board, thread, _ ->
                    navController.navigate(Route.ThreadVideos(provider, board, thread, videosOnly = true))
                },
                onOpenBoard = { provider, board, title ->
                    navController.navigate(Route.Board(provider, board, title))
                },
                onOpenSettings = { navController.navigate(Route.Settings) },
                hideRailOnScroll = chromeHidesOnScroll,
                onChromeVisibleChange = onChromeVisibleChange,
                onOpenBoards = { navController.navigateToTab(Route.BoardGallery) },
                onOpenDownloads = { navController.navigate(Route.Downloads) },
                onOpenSearch = { navController.navigateToTab(Route.Search) },
                onOpenSaved = { navController.navigateToTab(Route.Saved) },
                onOpenWatched = { navController.navigateToTab(Route.Watched) },
            )
        }

        composable<Route.BoardGallery> {
            BoardGalleryScreen(
                onOpenBoard = { provider, board, title ->
                    navController.navigate(Route.Board(provider, board, title))
                },
                onOpenFeed = { navController.navigateToTab(Route.NextFeed) },
                onOpenDownloads = { navController.navigate(Route.Downloads) },
                onOpenSaved = { navController.navigateToTab(Route.Saved) },
                onOpenWatched = { navController.navigateToTab(Route.Watched) },
                onOpenSettings = { navController.navigate(Route.Settings) },
                onOpenSearch = { navController.navigateToTab(Route.Search) },
                hideRailOnScroll = chromeHidesOnScroll,
                onChromeVisibleChange = onChromeVisibleChange,
            )
        }

        composable<Route.Search> {
            NextScaffold(
                where = stringResource(R.string.nav_search),
                destination = NextDestination.SEARCH,
                onDestination = onDestination,
            ) { padding ->
                Box(Modifier.fillMaxSize().padding(padding)) {
                    NextSearchScreen(
                        onOpenThread = openThread,
                        onBack = { navController.navigateToTab(Route.NextFeed) },
                    )
                }
            }
        }

        composable<Route.BoardMedia> { backStackEntry ->
            NextAllMediaScreen(
                title = "/${backStackEntry.toRoute<Route.BoardMedia>().board}/",
                onOpenMedia = { provider, board, thread, attachmentId ->
                    navController.navigate(
                        Route.Gallery(
                            provider = provider,
                            board = board,
                            thread = thread,
                            startIndex = 0,
                            attachmentId = attachmentId,
                        ),
                    )
                },
                onOpenSaved = { navController.navigateToTab(Route.Saved) },
            )
        }

        composable<Route.Board> { backStackEntry ->
            val boardRoute = backStackEntry.toRoute<Route.Board>()
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
                    onOpenBoardMedia = { board ->
                        navController.navigate(Route.BoardMedia(board = board, provider = boardRoute.provider))
                    },
                )
            } else {
                NextBoardScreen(
                    onOpenThread = openThread,
                    onOpenMedia = { board ->
                        navController.navigate(Route.BoardMedia(board = board, provider = boardRoute.provider))
                    },
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
                onBack = { navController.navigateUp() },
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

        composable<Route.ThreadVideos> {
            GalleryScreen(onClose = navController::navigateUp)
        }

        composable<Route.Saved> {
            ThreadCollectionsDestination(
                kind = CollectionKind.SAVED,
                title = stringResource(R.string.nav_saved),
                onOpenThread = openThread,
                onSettings = { navController.navigate(Route.Settings) },
                onDestination = onDestination,
            )
        }
        composable<Route.History> {
            ThreadCollectionsDestination(
                kind = CollectionKind.HISTORY,
                title = stringResource(R.string.nav_history),
                onOpenThread = openThread,
                onBack = { navController.navigateUp() },
            )
        }
        composable<Route.Watched> {
            ThreadCollectionsDestination(
                kind = CollectionKind.WATCHED,
                title = stringResource(R.string.nav_watched),
                onOpenThread = openThread,
                onSettings = { navController.navigate(Route.Settings) },
                onDestination = onDestination,
            )
        }
        composable<Route.Downloads> {
            DownloadsScreen(onBack = { navController.navigateUp() })
        }

        composable<Route.Settings> {
            NextSettingsScreen(
                snackbarHostState = LocalOrbinSnackbarHostState.current,
                onBack = { navController.navigateUp() },
                onNavigate = { destination ->
                    when (destination) {
                        "boards", "favorites" -> navController.navigateToTab(Route.BoardGallery)
                        "history" -> navController.navigate(Route.History)
                        "watched" -> navController.navigateToTab(Route.Watched)
                        "downloads" -> navController.navigate(Route.Downloads)
                        "help" -> uriHandler.openUri("https://github.com/Defuuls/Orbin/wiki")
                    }
                },
            )
        }

        composable<Route.Onboarding> {
            OnboardingScreen(
                onFinish = {
                    onOnboardingCompleted()
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
 * Switches between Home, Boards, Watched, Search and Saved without piling up previous tab destinations.
 * Home remains the root, so Android Back from another tab returns there.
 */
internal fun NavHostController.navigateToTab(route: Route) {
    // Save only the owning tab. Restoring a covered Settings or detail page would open it when
    // the reader explicitly selected the tab underneath it.
    while (currentDestination?.isPrimaryTab() == false) {
        if (!popBackStack()) break
    }
    navigate(route) {
        popUpTo(Route.NextFeed) {
            inclusive = false
            saveState = true
        }
        launchSingleTop = true
        restoreState = true
    }
}

/** Resolves the five tabs without confusing saved threads with downloaded files. */
internal fun NavHostController.openDestination(destination: NextDestination) {
    when (destination) {
        NextDestination.FEED -> navigateToTab(Route.NextFeed)
        NextDestination.SEARCH -> navigateToTab(Route.Search)
        NextDestination.BOARDS -> navigateToTab(Route.BoardGallery)
        NextDestination.SAVED -> navigateToTab(Route.Saved)
        NextDestination.WATCHED -> navigateToTab(Route.Watched)
        NextDestination.DOWNLOADS -> navigate(Route.Downloads)
        NextDestination.SETTINGS -> navigate(Route.Settings)
    }
}
