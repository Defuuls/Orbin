package com.orbin.minimal

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.orbin.feature.gallery.GalleryScreen
import com.orbin.feature.thread.ThreadScreen
import kotlinx.serialization.Serializable

/**
 * Four destinations, no bottom bar, no tabs, no drawer: feed, boards, thread, image.
 *
 * The full client's graph carries fifteen-odd destinations behind a bottom navigation bar. This
 * one is a stack you go down and come back up, which is the entire interaction model of the app.
 */
@Composable
fun MinimalNavHost() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = MinimalRoute.Feed) {
        composable<MinimalRoute.Feed> {
            MinimalFeedScreen(
                onOpenThread = { provider, board, thread, title ->
                    navController.navigate(MinimalRoute.Thread(provider, board, thread, title))
                },
                onOpenBoards = { navController.navigate(MinimalRoute.Boards) },
            )
        }

        composable<MinimalRoute.Boards> {
            MinimalBoardsScreen(onBack = navController::navigateUp)
        }

        composable<MinimalRoute.Thread> { backStackEntry ->
            val route = backStackEntry.toRoute<MinimalRoute.Thread>()
            ThreadScreen(
                onBack = navController::navigateUp,
                // The thread renders media inline; this is the tap that opens it full screen.
                // Without it the images in a thread would be a dead tap, which is a worse kind of
                // minimal than simply leaving a feature out.
                onOpenMedia = { index ->
                    navController.navigate(
                        MinimalRoute.Media(route.provider, route.board, route.thread, index),
                    )
                },
            )
        }

        composable<MinimalRoute.Media> {
            GalleryScreen(onClose = navController::navigateUp)
        }
    }
}

/**
 * Its own route type rather than a share of the full client's: those live in `:app`, and the point
 * of a second application module is that neither app's navigation constrains the other's.
 */
sealed interface MinimalRoute {
    @Serializable
    data object Feed : MinimalRoute

    @Serializable
    data object Boards : MinimalRoute

    @Serializable
    data class Thread(
        val provider: String,
        val board: String,
        val thread: Long,
        val title: String,
    ) : MinimalRoute

    /**
     * Argument names match what `GalleryViewModel` and `ThreadViewModel` read out of their
     * `SavedStateHandle`, which is how these screens are reused unchanged.
     */
    @Serializable
    data class Media(
        val provider: String,
        val board: String,
        val thread: Long,
        val startIndex: Int,
    ) : MinimalRoute
}
