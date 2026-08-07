package com.orbin.app.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.orbin.core.ui.state.EmptyView
import com.orbin.feature.board.BoardScreen
import com.orbin.feature.thread.ThreadScreen
import kotlinx.serialization.Serializable

/**
 * The catalog and the selected thread side by side, for viewports wide enough to carry both.
 *
 * On a phone, opening a thread replaces the catalog and Back brings it back. That is the right
 * trade when the screen only fits one, and the wrong one when it fits two: a tablet reader loses
 * the catalog they are working through every time they open a reply.
 *
 * The detail pane is its own [NavHost] rather than a plain composable. [ThreadScreen]'s ViewModel
 * reads `provider`/`board`/`thread` out of its `SavedStateHandle`, which only a navigation entry
 * populates — rendering it inline would hand it the *catalog's* arguments and load thread 0. A
 * nested graph also gives the pane a back stack of its own, so Back closes the thread and leaves
 * the catalog standing.
 */
@Composable
fun BoardDetailTwoPane(
    boardEntry: NavBackStackEntry,
    selectedThread: Route.Thread?,
    onThreadSelected: (Route.Thread?) -> Unit,
    onOpenGallery: (provider: String, board: String, thread: Long, index: Int) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val detailNavController = rememberNavController()
    val detailEntry by detailNavController.currentBackStackEntryAsState()
    val threadOpen = detailEntry?.destination?.hasRoute(Route.Thread::class) == true

    // The single place the detail pane is navigated: a pick from the catalog, a switch to another
    // thread, or a restore after the panes were torn down (returning from the gallery, or a
    // rotation that stayed wide). launchSingleTop makes the restore case a no-op when the pane
    // already has the right thread, and popUpTo keeps the pane's stack one deep so Back is always
    // a single step back to the catalog rather than a walk through everything read so far.
    LaunchedEffect(selectedThread) {
        selectedThread?.let { thread ->
            detailNavController.navigate(thread) {
                popUpTo<DetailPaneEmpty>()
                launchSingleTop = true
            }
        }
    }

    // Back closes the open thread first and only then leaves the catalog, matching what the panes
    // show: the thread is the most recent thing the reader opened.
    BackHandler(enabled = threadOpen) {
        onThreadSelected(null)
        detailNavController.popBackStack()
    }

    Row(modifier = modifier.fillMaxSize()) {
        Box(
            modifier =
                Modifier
                    .widthIn(max = LIST_PANE_MAX_WIDTH)
                    .weight(LIST_PANE_WEIGHT)
                    .fillMaxHeight(),
        ) {
            BoardScreen(
                onOpenThread = { provider, board, thread, title ->
                    // Recorded as well as navigated: the record is what survives the panes
                    // collapsing on rotation, and it drives the navigate through the LaunchedEffect
                    // above rather than duplicating it here.
                    onThreadSelected(Route.Thread(provider, board, thread, title))
                },
                onBack = onBack,
            )
        }

        VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        Box(modifier = Modifier.weight(DETAIL_PANE_WEIGHT).fillMaxHeight()) {
            NavHost(
                navController = detailNavController,
                startDestination = DetailPaneEmpty,
                modifier = Modifier.fillMaxSize(),
            ) {
                composable<DetailPaneEmpty> {
                    EmptyView("Pick a thread from the catalog", Modifier.fillMaxSize())
                }

                composable<Route.Thread> { entry ->
                    val route = entry.toRoute<Route.Thread>()
                    // The media scroll index round-trips through the *catalog* entry, because the
                    // gallery opens on the outer navigation graph and writes its page back to its
                    // own previous entry — which in two-pane mode is the catalog, not this pane.
                    val mediaScrollIndex by
                        boardEntry.savedStateHandle
                            .getStateFlow(THREAD_MEDIA_SCROLL_INDEX_KEY, NO_THREAD_MEDIA_SCROLL_INDEX)
                            .collectAsStateWithLifecycle()

                    ThreadScreen(
                        onBack = {
                            onThreadSelected(null)
                            detailNavController.popBackStack()
                        },
                        onOpenMedia = { index ->
                            onOpenGallery(route.provider, route.board, route.thread, index)
                        },
                        mediaScrollIndex = mediaScrollIndex.takeIf { it != NO_THREAD_MEDIA_SCROLL_INDEX },
                        onMediaScrollConsumed = {
                            boardEntry.savedStateHandle[THREAD_MEDIA_SCROLL_INDEX_KEY] =
                                NO_THREAD_MEDIA_SCROLL_INDEX
                        },
                    )
                }
            }
        }
    }
}

/** Placeholder destination shown in the detail pane before a thread is picked. */
@Serializable
private data object DetailPaneEmpty

// A catalog wider than this stops being easier to scan and just spreads the same rows further
// apart, so surplus width goes to the thread instead.
private val LIST_PANE_MAX_WIDTH = 420.dp
private const val LIST_PANE_WEIGHT = 1f
private const val DETAIL_PANE_WEIGHT = 1.4f
