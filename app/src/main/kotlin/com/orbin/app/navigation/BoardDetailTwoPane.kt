package com.orbin.app.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
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
import com.orbin.core.model.ThreadPresentation
import com.orbin.core.model.readerPaneWidths
import com.orbin.feature.board.NextBoardScreen
import com.orbin.feature.thread.NextThreadScreen
import com.orbin.uinext.ReaderTwoPane
import kotlinx.serialization.Serializable
import kotlin.math.roundToInt

@Composable
fun BoardDetailTwoPane(
    selectedThread: Route.Thread?,
    onThreadSelected: (Route.Thread?) -> Unit,
    onOpenGallery: (provider: String, board: String, thread: Long, index: Int) -> Unit,
    mediaScrollIndex: Int? = null,
    onMediaScrollConsumed: () -> Unit = {},
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenBoardMedia: ((board: String) -> Unit)? = null,
) {
    val detailNavController = rememberNavController()
    val detailEntry by detailNavController.currentBackStackEntryAsState()
    val threadOpen = detailEntry?.destination?.hasRoute(Route.Thread::class) == true

    LaunchedEffect(selectedThread, detailEntry) {
        // A restored selection can arrive before BoxWithConstraints composes the detail graph.
        // Its first entry confirms that the graph is installed before attempting navigation.
        if (detailEntry == null) return@LaunchedEffect
        selectedThread?.let { thread ->
            val currentThread =
                runCatching { detailNavController.currentBackStackEntry?.toRoute<Route.Thread>() }.getOrNull()
            if (currentThread != thread) {
                detailNavController.navigate(thread) {
                    popUpTo<DetailPaneEmpty> { inclusive = false }
                }
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

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val widths = readerPaneWidths(maxWidth.value.roundToInt())
        ReaderTwoPane(
            catalogWidth = widths.catalogDp.dp,
            threadOpen = threadOpen,
            listContent = {
                NextBoardScreen(
                    onOpenThread = { provider, board, thread, title ->
                        onThreadSelected(Route.Thread(provider, board, thread, title))
                    },
                    onOpenMedia = onOpenBoardMedia,
                    selectedThread = selectedThread?.thread,
                )
            },
            detailContent = {
                // Soft Next push inside the detail pane (empty ↔ thread), not Material defaults.
                val presentation = ThreadPresentation.PAGE
                NavHost(
                    navController = detailNavController,
                    startDestination = DetailPaneEmpty,
                    modifier = Modifier.fillMaxSize(),
                    enterTransition = { nextEnter(presentation) },
                    exitTransition = { nextExit(presentation) },
                    popEnterTransition = { nextPopEnter(presentation) },
                    popExitTransition = { nextPopExit(presentation) },
                ) {
                    // The shared component owns the empty state; keep this graph mounted so the
                    // first selection can navigate after its initial empty destination is installed.
                    composable<DetailPaneEmpty> {}

                    composable<Route.Thread> { entry ->
                        val route = entry.toRoute<Route.Thread>()
                        NextThreadScreen(
                            onBack = {
                                onThreadSelected(null)
                                detailNavController.popBackStack()
                            },
                            onOpenMedia = { index ->
                                onOpenGallery(route.provider, route.board, route.thread, index)
                            },
                            mediaScrollIndex = mediaScrollIndex,
                            onMediaScrollConsumed = onMediaScrollConsumed,
                        )
                    }
                }
            },
        )
    }
}

@Serializable
private data object DetailPaneEmpty
