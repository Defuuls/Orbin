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
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
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
import com.orbin.feature.home.FeedDownloadBadgeViewModel
import com.orbin.feature.home.NextFeedWithSiteSwitcherScreen
import com.orbin.feature.home.WatchedBadgeViewModel
import com.orbin.feature.onboarding.OnboardingScreen
import com.orbin.feature.search.NextSearchScreen
import com.orbin.feature.settings.NextSettingsScreen
import com.orbin.feature.thread.NextThreadScreen
import com.orbin.uinext.components.LibrarySection
import com.orbin.uinext.components.LocalOrbinNavigationAtSide
import com.orbin.uinext.components.NextDestination
import com.orbin.uinext.components.NextScaffold
import com.orbin.uinext.components.OrbinBottomBar
import com.orbin.uinext.components.ReaderAction
import com.orbin.uinext.components.ReaderIcons
import com.orbin.uinext.components.ReaderScaffold
import com.orbin.uinext.components.readerKeyboardShortcuts

internal const val THREAD_MEDIA_SCROLL_INDEX_KEY = "threadMediaScrollIndex"
internal const val NO_THREAD_MEDIA_SCROLL_INDEX = -1
internal const val INITIAL_THREAD_POST_ID_KEY = "initialThreadPostId"
internal const val INITIAL_THREAD_POST_OWNER_KEY = "initialThreadPostOwner"

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
    val library = LocalLibraryMemory.current
    val onDestination: (NextDestination) -> Unit = { destination ->
        navController.openDestination(destination, library)
    }
    val openPrimary: (NextDestination) -> Unit = { destination ->
        navController.openPrimaryDestination(destination, library)
    }
    val pickBoard: (String, String, String) -> Unit = { provider, board, title ->
        navController.selectCatalogBoard(Route.Board(provider, board, title))
    }
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier.readerKeyboardShortcuts(onDestination = openPrimary),
        enterTransition = { nextEnter(threadPresentation) },
        exitTransition = { nextExit(threadPresentation) },
        popEnterTransition = { nextPopEnter(threadPresentation) },
        popExitTransition = { nextPopExit(threadPresentation) },
    ) {
        // Home is a thread source like Watched or Saved: on a tablet the feed stays on the left
        // and the thread it opens takes the right, instead of the thread covering the feed.
        composable<Route.NextFeed> { backStackEntry ->
            SourceReaderRouteDestination(
                navController = navController,
                backStackEntry = backStackEntry,
                twoPane = twoPaneBoardDetail,
                title = stringResource(R.string.nav_home),
                destination = NextDestination.FEED,
                onDestination = onDestination,
            ) { sourceOpenThread ->
            NextFeedWithSiteSwitcherScreen(
                onOpenThread = sourceOpenThread,
                onOpenThreadVideos = { provider, board, thread, _ ->
                    navController.navigate(Route.ThreadVideos(provider, board, thread, videosOnly = true))
                },
                onOpenMedia = { provider, board, thread, attachment ->
                    navController.navigate(selectedMediaRoute(provider, board, thread, attachment))
                },
                onOpenBoard = { provider, board, title ->
                    navController.navigate(Route.Board(provider, board, title))
                },
                onOpenSettings = { openPrimary(NextDestination.SETTINGS) },
                onChromeVisibleChange = onChromeVisibleChange,
                onOpenBoards = { navController.navigateToTab(Route.BoardGallery) },
                onOpenDownloads = { openPrimary(NextDestination.DOWNLOADS) },
                onOpenSearch = { navController.navigateToTab(Route.Search) },
                onOpenSaved = { onDestination(NextDestination.SAVED) },
                onOpenWatched = { onDestination(NextDestination.WATCHED) },
                onOpenLibrary = { onDestination(NextDestination.LIBRARY) },
                watchedBadge = primaryWatchedBadge(),
            )
            }
        }

        composable<Route.BoardGallery> {
            BoardGalleryScreen(
                onOpenThread = openThread,
                downloadBadge = primaryDownloadBadge(),
                onOpenBoard = { provider, board, title ->
                    navController.navigate(Route.Board(provider, board, title))
                },
                onOpenFeed = { navController.navigateToTab(Route.NextFeed) },
                onOpenDownloads = { openPrimary(NextDestination.DOWNLOADS) },
                onOpenSaved = { onDestination(NextDestination.SAVED) },
                onOpenWatched = { onDestination(NextDestination.WATCHED) },
                onOpenLibrary = { onDestination(NextDestination.LIBRARY) },
                watchedBadge = primaryWatchedBadge(),
                onOpenSettings = { openPrimary(NextDestination.SETTINGS) },
                onOpenSearch = { navController.navigateToTab(Route.Search) },
                hideRailOnScroll = chromeHidesOnScroll,
                onChromeVisibleChange = onChromeVisibleChange,
            )
        }

        composable<Route.Search> { backStackEntry ->
            SourceReaderRouteDestination(
                navController = navController,
                backStackEntry = backStackEntry,
                twoPane = twoPaneBoardDetail,
                title = stringResource(R.string.nav_search),
                destination = NextDestination.SEARCH,
                onDestination = onDestination,
            ) { sourceOpenThread ->
                NextScaffold(
                    where = stringResource(R.string.nav_search),
                    destination = NextDestination.SEARCH,
                    onDestination = onDestination,
                    onBack = { navController.navigateUp() },
                ) { padding ->
                    Box(Modifier.fillMaxSize().padding(padding)) {
                        NextSearchScreen(
                            onOpenThread = sourceOpenThread,
                            onBack = null,
                            onOpenPost = { provider, board, thread, post, title ->
                                backStackEntry.savedStateHandle[INITIAL_THREAD_POST_OWNER_KEY] =
                                    "$provider/$board/$thread"
                                backStackEntry.savedStateHandle[INITIAL_THREAD_POST_ID_KEY] = post
                                sourceOpenThread(provider, board, thread, title)
                            },
                        )
                    }
                }
            }
        }

        composable<Route.BoardMedia> { backStackEntry ->
            ReaderScaffold(
                title = "/${backStackEntry.toRoute<Route.BoardMedia>().board}/",
                onBack = { navController.navigateUp() },
                actions =
                    listOf(
                        ReaderAction(ReaderIcons.Saved, stringResource(R.string.nav_saved)) {
                            navController.navigateToTab(Route.Saved)
                        },
                    ),
            ) {
                NextAllMediaScreen(
                    title = "/${backStackEntry.toRoute<Route.BoardMedia>().board}/",
                    onOpenMedia = { provider, board, thread, attachment ->
                        navController.navigate(selectedMediaRoute(provider, board, thread, attachment))
                    },
                    onOpenSaved = null,
                )
            }
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
                    onDestination = onDestination,
                    onOpenBoard = pickBoard,
                    onOpenBoardMedia = { board ->
                        navController.navigate(Route.BoardMedia(board = board, provider = boardRoute.provider))
                    },
                )
            } else {
                NextBoardScreen(
                    onOpenThread = openThread,
                    onDestination = onDestination,
                    onOpenBoard = pickBoard,
                    onBack = { navController.navigateUp() },
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
            val initialPostId by backStackEntry.savedStateHandle
                .getStateFlow(INITIAL_THREAD_POST_ID_KEY, -1L)
                .collectAsStateWithLifecycle()
            NextThreadScreen(
                onBack = { navController.navigateUp() },
                onOpenMedia = { index ->
                    navController.navigate(Route.Gallery(route.provider, route.board, route.thread, index))
                },
                onOpenMediaFiltered = { index, filter ->
                    navController.navigate(
                        Route.FilteredMedia(route.provider, route.board, route.thread, index, filter.name),
                    )
                },
                mediaScrollIndex = mediaScrollIndex.takeIf { it != NO_THREAD_MEDIA_SCROLL_INDEX },
                onMediaScrollConsumed = {
                    backStackEntry.savedStateHandle[THREAD_MEDIA_SCROLL_INDEX_KEY] =
                        NO_THREAD_MEDIA_SCROLL_INDEX
                },
                initialPostId = initialPostId.takeIf { it > 0 },
                onInitialPostConsumed = { backStackEntry.savedStateHandle[INITIAL_THREAD_POST_ID_KEY] = -1L },
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

        composable<Route.FilteredMedia> {
            GalleryScreen(
                onClose = navController::navigateUp,
                onMediaPageChanged = { page ->
                    navController.previousBackStackEntry?.savedStateHandle?.set(THREAD_MEDIA_SCROLL_INDEX_KEY, page)
                },
            )
        }

        composable<Route.SelectedMedia> {
            GalleryScreen(onClose = navController::navigateUp)
        }

        composable<Route.SeededMedia> {
            GalleryScreen(onClose = navController::navigateUp)
        }

        composable<Route.Saved> { backStackEntry ->
            SourceReaderRouteDestination(
                navController = navController,
                backStackEntry = backStackEntry,
                twoPane = twoPaneBoardDetail,
                title = stringResource(R.string.nav_saved),
                destination = NextDestination.SAVED,
                onDestination = onDestination,
            ) { sourceOpenThread ->
                ThreadCollectionsDestination(
                    kind = CollectionKind.SAVED,
                    title = stringResource(R.string.nav_saved),
                    onOpenThread = sourceOpenThread,
                    onSettings = { navController.navigate(Route.Settings) },
                    onDestination = onDestination,
                )
            }
        }
        composable<Route.History> { backStackEntry ->
            SourceReaderRouteDestination(
                navController = navController,
                backStackEntry = backStackEntry,
                twoPane = twoPaneBoardDetail,
                title = stringResource(R.string.nav_history),
                destination = null,
            ) { sourceOpenThread ->
                ThreadCollectionsDestination(
                    kind = CollectionKind.HISTORY,
                    title = stringResource(R.string.nav_history),
                    onOpenThread = sourceOpenThread,
                    onBack = { navController.navigateUp() },
                )
            }
        }
        composable<Route.Watched> { backStackEntry ->
            SourceReaderRouteDestination(
                navController = navController,
                backStackEntry = backStackEntry,
                twoPane = twoPaneBoardDetail,
                title = stringResource(R.string.nav_watched),
                destination = NextDestination.WATCHED,
                onDestination = onDestination,
            ) { sourceOpenThread ->
                ThreadCollectionsDestination(
                    kind = CollectionKind.WATCHED,
                    title = stringResource(R.string.nav_watched),
                    onOpenThread = sourceOpenThread,
                    onSettings = { navController.navigate(Route.Settings) },
                    onDestination = onDestination,
                )
            }
        }
        composable<Route.Downloads> {
            DownloadsScreen(
                onBack = null,
                bottomBar = null,
            )
        }

        composable<Route.Settings> {
            NextSettingsScreen(
                bottomBar =
                    if (LocalOrbinNavigationAtSide.current) {
                        null
                    } else {
                        { PrimaryBottomBar(NextDestination.SETTINGS, openPrimary) }
                    },
                snackbarHostState = LocalOrbinSnackbarHostState.current,
                onBack = { navController.navigateUp() },
                onNavigate = { link -> navController.openSettingsLink(link, library, uriHandler::openUri) },
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

/** The view model belongs to the current navigation entry; progress bytes never change this state. */
@Composable
private fun primaryDownloadBadge(): Int {
    val model: FeedDownloadBadgeViewModel = hiltViewModel()
    val count by model.activeCount.collectAsStateWithLifecycle()
    return count
}

@Composable
private fun PrimaryBottomBar(
    selected: NextDestination,
    onSelect: (NextDestination) -> Unit,
) {
    OrbinBottomBar(
        selected = selected,
        onSelect = onSelect,
        downloadBadge = primaryDownloadBadge(),
        watchedBadge = primaryWatchedBadge(),
    )
}

/** New replies on watched threads, for the Library tab. */
@Composable
internal fun primaryWatchedBadge(): Int {
    val model: WatchedBadgeViewModel = hiltViewModel()
    val count by model.newReplies.collectAsStateWithLifecycle()
    return count
}

internal val threadRouteSaver =
    listSaver<Route.Thread?, Any>(
        save = { thread ->
            thread?.let { listOf(it.provider, it.board, it.thread, it.title) } ?: emptyList()
        },
        restore = { fields ->
            if (fields.isEmpty()) {
                null
            } else {
                Route.Thread(
                    provider = fields[0] as String,
                    board = fields[1] as String,
                    thread = fields[2] as Long,
                    title = fields[3] as String,
                )
            }
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

/** Resolves primary tabs and Library sections; Search and Settings open over the current page. */
internal fun NavHostController.openDestination(
    destination: NextDestination,
    memory: LibraryMemory? = null,
) {
    when (destination) {
        NextDestination.FEED -> navigateToTab(Route.NextFeed)
        NextDestination.SEARCH -> navigateToTab(Route.Search)
        NextDestination.BOARDS -> navigateToTab(Route.BoardGallery)
        NextDestination.LIBRARY -> openLibrary(memory?.section ?: LibrarySection.DEFAULT, memory)
        NextDestination.SAVED -> openLibrary(LibrarySection.SAVED, memory)
        NextDestination.WATCHED -> openLibrary(LibrarySection.WATCHED, memory)
        NextDestination.DOWNLOADS -> openLibrary(LibrarySection.DOWNLOADS, memory)
        NextDestination.SETTINGS -> navigate(Route.Settings)
    }
}

/** Settings returns to a retained Settings page; every other destination is a tab. */
internal fun NavHostController.openPrimaryDestination(
    destination: NextDestination,
    memory: LibraryMemory? = null,
) {
    if (destination != NextDestination.SETTINGS) return openDestination(destination, memory)
    if (currentDestination?.hasRoute(Route.Settings::class) == true) return
    navigate(Route.Settings) { launchSingleTop = true }
}

/** Choosing the board already in view retains its paging generation, sort and scroll state. */
internal fun NavHostController.selectCatalogBoard(board: Route.Board) {
    val current = currentBackStackEntry?.takeIf { it.destination.hasRoute(Route.Board::class) }?.toRoute<Route.Board>()
    if (current?.provider == board.provider && current.board == board.board) return
    navigate(board) {
        popUpTo<Route.Board> { inclusive = true }
        launchSingleTop = true
    }
}
