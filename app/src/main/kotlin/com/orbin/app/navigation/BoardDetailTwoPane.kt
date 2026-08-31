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
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.orbin.core.ui.state.EmptyView
import com.orbin.feature.board.NextBoardScreen
import com.orbin.feature.thread.NextThreadScreen
import kotlinx.serialization.Serializable

/**
 * The catalog and the selected thread side by side, for viewports wide enough to carry both.
 *
 * On a phone, opening a thread replaces the catalog and Back brings it back. That is the right
 * trade when the screen only fits one, and the wrong one when it fits two: a tablet reader loses
 * the catalog they are working through every time they open a reply.
 *
 * The detail pane is its own [NavHost] rather than a plain composable. [NextThreadScreen]'s
 * ViewModel reads `provider`/`board`/`thread` out of its `SavedStateHandle`, which only a
 * navigation entry populates — rendering it inline would hand it the *catalog's* arguments and
 * load thread 0. A nested graph also gives the pane a back stack of its own, so Back closes the
 * thread and leaves the catalog standing.
 */
@Composable
fun BoardDetailTwoPane(
    selectedThread: Route.Thread?,
    onThreadSelected: (Route.Thread?) -> Unit,
    onOpenGallery: (provider: String, board: String, thread: Long, index: Int) -> Unit,
    onOpenCommands: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val detailNavController = rememberNavController()
    val detailEntry by detailNavController.currentBackStackEntryAsState()
    val threadOpen = detailEntry?.destination?.hasRoute(Route.Thread::class) == true

    LaunchedEffect(selectedThread) {
        selectedThread?.let { thread ->
            detailNavController.navigate(thread) {
                popUpTo<DetailPaneEmpty>()
                launchSingleTop = true
            }
        }
    }

    BackHandler {
        if (threadOpen) {
            onThreadSelected(null)
            detailNavController.popBackStack()
        } else {
            onBack()
        }
    }

    Row(modifier = modifier.fillMaxSize()) {
        Box(
            modifier =
                Modifier
                    .widthIn(max = LIST_PANE_MAX_WIDTH)
                    .weight(LIST_PANE_WEIGHT)
                    .fillMaxHeight(),
        ) {
            NextBoardScreen(
                onOpenThread = { provider, board, thread, title ->
                    onThreadSelected(Route.Thread(provider, board, thread, title))
                },
                onOpenCommands = onOpenCommands,
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
                    NextThreadScreen(
                        onOpenMedia = { index ->
                            onOpenGallery(route.provider, route.board, route.thread, index)
                        },
                        onOpenCommands = onOpenCommands,
                    )
                }
            }
        }
    }
}

@Serializable
private data object DetailPaneEmpty

private val LIST_PANE_MAX_WIDTH = 420.dp
private const val LIST_PANE_WEIGHT = 1f
private const val DETAIL_PANE_WEIGHT = 1.4f
