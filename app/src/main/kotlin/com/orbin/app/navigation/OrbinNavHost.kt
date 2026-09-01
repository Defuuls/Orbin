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

// Internal rather than private: every screen that can open the full-screen gallery reads this same
// key from its own back stack entry, since Route.Gallery always writes the page it's on back to
// whichever entry precedes it — the two-pane catalog (when the thread is a pane, not a
// destination) and the standalone gallery browser both read it themselves.
internal const val THREAD_MEDIA_SCROLL_INDEX_KEY = "threadMediaScrollIndex"
internal const val NO_THREAD_MEDIA_SCROLL_INDEX = -1

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
    startDestination: Route = Route.NextFeed,
    /** Hide the rail as the reader scrolls down, on every screen that has one. */
    chromeHidesOnScroll: Boolean = false,
    /** Show the catalog and the selected thread side by side instead of one replacing the other. */
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
            // Hoisted above the two-pane/one-pane branch so it survives the switch. A 10" tablet is
            // around 1280dp in landscape and 800dp in portrait, so an ordinary rotation crosses the
            // threshold — without this the open thread would just vanish when the panes collapse.
            var paneThread by
                rememberSaveable(stateSaver = threadRouteSaver) { mutableStateOf<Route.Thread?>(null) }

            LaunchedEffect(twoPaneBoardDetail) {
                if (!twoPaneBoardDetail) {
                    // Collapsed: promote whatever the detail pane held to a destination of its own,
                    // so the reader keeps the thread and Back returns them to the catalog.
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
            NextThreadScreen(
                onOpenMedia = { index ->
                    navController.navigate(Route.Gallery(route.provider, route.board, route.thread, index))
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

        composable<Route.Downloads> {
            DownloadsScreen(onBack = navController::navigateUp)
        }

        composable<Route.Settings> { backStackEntry ->
            NextSettingsScreen(
                onOpenCommands = onOpenCommands,
                onRunSetup = { navController.navigate(Route.Onboarding) },
                snackbarHostState = LocalOrbinSnackbarHostState.current,
                focusId = backStackEntry.toRoute<Route.Settings>().focus,
            )
        }

        composable<Route.Subscriptions> {
            SubscriptionsScreen(onBack = navController::navigateUp)
        }

        composable<Route.Onboarding> {
            OnboardingScreen(
                onFinish = {
                    navController.navigate(Route.NextFeed) {
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

/**
 * Saves the thread open in the detail pane across configuration changes.
 *
 * [Route.Thread] is `@Serializable` for navigation, not `Parcelable`, so it cannot go into a
 * Bundle as-is; its four fields all can.
 *
 * "No thread selected" is encoded as the empty list, which `listSaver` turns into a *null saved
 * value* — nothing is written, and `rememberSaveable` re-runs its initialiser on the way back,
 * which yields null. So [restore] is only ever handed a populated list; it does not need, and must
 * not pretend to have, an empty case.
 */
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

/** Which destination a settings section's editor lives at. */