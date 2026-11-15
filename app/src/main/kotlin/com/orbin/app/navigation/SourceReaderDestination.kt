package com.orbin.app.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.orbin.core.model.ThreadPresentation
import com.orbin.core.model.readerPaneWidths
import com.orbin.feature.thread.NextThreadScreen
import com.orbin.uinext.NextDestination
import com.orbin.uinext.ReaderScaffold
import com.orbin.uinext.ReaderTwoPane
import kotlinx.serialization.Serializable
import kotlin.math.roundToInt

internal typealias OpenSourceThread = (provider: String, board: String, thread: Long, title: String) -> Unit

/** Gallery returns target the source entry, since its selected thread lives in the detail graph. */
@Composable
internal fun SourceReaderRouteDestination(
    navController: NavHostController,
    backStackEntry: NavBackStackEntry,
    twoPane: Boolean,
    title: String,
    destination: NextDestination?,
    onDestination: ((NextDestination) -> Unit)? = null,
    sourceContent: @Composable (OpenSourceThread) -> Unit,
) {
    val scrollIndex by backStackEntry.savedStateHandle
        .getStateFlow(THREAD_MEDIA_SCROLL_INDEX_KEY, NO_THREAD_MEDIA_SCROLL_INDEX)
        .collectAsStateWithLifecycle()
    val focusPost by backStackEntry.savedStateHandle
        .getStateFlow(INITIAL_THREAD_POST_ID_KEY, -1L)
        .collectAsStateWithLifecycle()
    val focusOwner by backStackEntry.savedStateHandle
        .getStateFlow(INITIAL_THREAD_POST_OWNER_KEY, "")
        .collectAsStateWithLifecycle()
    SourceReaderDestination(
        twoPane = twoPane,
        title = title,
        destination = destination,
        onOpenThread = { provider, board, thread, heading ->
            navController.navigate(Route.Thread(provider, board, thread, heading))
            val pendingPost = backStackEntry.savedStateHandle.get<Long>(INITIAL_THREAD_POST_ID_KEY)
            val pendingOwner = backStackEntry.savedStateHandle.get<String>(INITIAL_THREAD_POST_OWNER_KEY)
            if (pendingPost != null && pendingPost > 0 && pendingOwner == "$provider/$board/$thread") {
                navController.currentBackStackEntry?.savedStateHandle?.set(INITIAL_THREAD_POST_ID_KEY, pendingPost)
                navController.currentBackStackEntry?.savedStateHandle?.set(INITIAL_THREAD_POST_OWNER_KEY, pendingOwner)
                backStackEntry.savedStateHandle[INITIAL_THREAD_POST_ID_KEY] = -1L
                backStackEntry.savedStateHandle[INITIAL_THREAD_POST_OWNER_KEY] = ""
            }
            // A gallery may return after the window narrowed while the source was covered.
            if (scrollIndex != NO_THREAD_MEDIA_SCROLL_INDEX) {
                navController.currentBackStackEntry?.savedStateHandle?.set(THREAD_MEDIA_SCROLL_INDEX_KEY, scrollIndex)
                backStackEntry.savedStateHandle[THREAD_MEDIA_SCROLL_INDEX_KEY] = NO_THREAD_MEDIA_SCROLL_INDEX
            }
        },
        onOpenGallery = {
            provider,
            board,
            thread,
            index,
            ->
            navController.navigate(Route.Gallery(provider, board, thread, index))
        },
        onOpenFilteredGallery = { provider, board, thread, index, filter ->
            navController.navigate(Route.FilteredMedia(provider, board, thread, index, filter))
        },
        mediaScrollIndex = scrollIndex.takeIf { it != NO_THREAD_MEDIA_SCROLL_INDEX },
        onMediaScrollConsumed = {
            backStackEntry.savedStateHandle[THREAD_MEDIA_SCROLL_INDEX_KEY] =
                NO_THREAD_MEDIA_SCROLL_INDEX
        },
        onDestination = onDestination,
        sourceContent = sourceContent,
        initialPostId = focusPost.takeIf { it > 0 },
        initialPostOwner = focusOwner,
        onInitialPostConsumed = {
            if (backStackEntry.savedStateHandle.get<Long>(INITIAL_THREAD_POST_ID_KEY) == focusPost &&
                backStackEntry.savedStateHandle.get<String>(INITIAL_THREAD_POST_OWNER_KEY) == focusOwner
            ) {
                backStackEntry.savedStateHandle[INITIAL_THREAD_POST_ID_KEY] = -1L
                backStackEntry.savedStateHandle[INITIAL_THREAD_POST_OWNER_KEY] = ""
            }
        },
    )
}

/**
 * Reuses the source back-stack entry for its query/list state while the selected thread has its
 * own nested entry and Hilt ViewModel. A narrow window promotes the selection into normal navigation.
 */
@Composable
internal fun SourceReaderDestination(
    twoPane: Boolean,
    title: String,
    destination: NextDestination?,
    onOpenThread: OpenSourceThread,
    onOpenGallery: (provider: String, board: String, thread: Long, index: Int) -> Unit,
    sourceContent: @Composable (OpenSourceThread) -> Unit,
    onDestination: ((NextDestination) -> Unit)? = null,
    onOpenFilteredGallery: (
        (
            provider: String,
            board: String,
            thread: Long,
            index: Int,
            filter: String,
        ) -> Unit
    )? = null,
    mediaScrollIndex: Int? = null,
    onMediaScrollConsumed: () -> Unit = {},
    initialPostId: Long? = null,
    initialPostOwner: String? = null,
    onInitialPostConsumed: () -> Unit = {},
    threadContent: (@Composable (Route.Thread, () -> Unit) -> Unit)? = null,
) {
    var selectedThread by rememberSaveable(stateSaver = threadRouteSaver) { mutableStateOf<Route.Thread?>(null) }
    val detailNavController = rememberNavController()
    val detailEntry by detailNavController.currentBackStackEntryAsState()
    val threadOpen = selectedThread != null && detailEntry?.destination?.hasRoute(Route.Thread::class) == true
    val sourcePane =
        remember {
            movableContentOf<
                OpenSourceThread,
                @Composable (
                    OpenSourceThread,
                ) -> Unit,
            > { open, content -> content(open) }
        }
    val openInPane: OpenSourceThread = { provider, board, thread, heading ->
        selectedThread = Route.Thread(provider, board, thread, heading)
    }
    LaunchedEffect(twoPane, selectedThread) {
        if (!twoPane) {
            selectedThread?.let { thread ->
                // The controller survives the narrow layout; remove its hidden reader as we promote it.
                detailNavController.popBackStack()
                selectedThread = null
                onOpenThread(thread.provider, thread.board, thread.thread, thread.title)
            }
        }
    }
    LaunchedEffect(twoPane, selectedThread, detailEntry) {
        if (!twoPane || detailEntry == null) return@LaunchedEffect
        if (selectedThread == null && detailEntry?.destination?.hasRoute(Route.Thread::class) == true) {
            detailNavController.popBackStack()
            return@LaunchedEffect
        }
        selectedThread?.let { thread ->
            val current = runCatching { detailNavController.currentBackStackEntry?.toRoute<Route.Thread>() }.getOrNull()
            if (current != thread) {
                detailNavController.navigate(thread) { popUpTo<SourceReaderEmpty> { inclusive = false } }
            }
        }
    }
    val closeThread = {
        selectedThread = null
        detailNavController.popBackStack()
        Unit
    }
    BackHandler(enabled = twoPane && selectedThread != null, onBack = closeThread)
    if (!twoPane) {
        sourcePane(onOpenThread, sourceContent)
        return
    }
    ReaderScaffold(
        title = title,
        destination = destination,
        onDestination = onDestination,
        drawAppBar = false,
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            ReaderTwoPane(
                catalogWidth = readerPaneWidths(maxWidth.value.roundToInt()).catalogDp.dp,
                threadOpen = threadOpen,
                modifier = Modifier.fillMaxSize(),
                listContent = { sourcePane(openInPane, sourceContent) },
                detailContent = {
                    NavHost(
                        navController = detailNavController,
                        startDestination = SourceReaderEmpty,
                        modifier = Modifier.fillMaxSize(),
                        enterTransition = { nextEnter(ThreadPresentation.PAGE) },
                        exitTransition = { nextExit(ThreadPresentation.PAGE) },
                        popEnterTransition = { nextPopEnter(ThreadPresentation.PAGE) },
                        popExitTransition = { nextPopExit(ThreadPresentation.PAGE) },
                    ) {
                        composable<SourceReaderEmpty> {}
                        composable<Route.Thread> { entry ->
                            val route = entry.toRoute<Route.Thread>()
                            if (threadContent != null) {
                                threadContent(route, closeThread)
                            } else {
                                NextThreadScreen(
                                    onBack = closeThread,
                                    onOpenMedia = { index ->
                                        onOpenGallery(route.provider, route.board, route.thread, index)
                                    },
                                    onOpenMediaFiltered =
                                        onOpenFilteredGallery?.let { open ->
                                            {
                                                index,
                                                filter,
                                                ->
                                                open(route.provider, route.board, route.thread, index, filter.name)
                                            }
                                        },
                                    mediaScrollIndex = mediaScrollIndex,
                                    onMediaScrollConsumed = onMediaScrollConsumed,
                                    initialPostId =
                                        initialPostId.takeIf {
                                            initialPostOwner ==
                                                "${route.provider}/${route.board}/${route.thread}"
                                        },
                                    onInitialPostConsumed = onInitialPostConsumed,
                                )
                            }
                        }
                    }
                },
            )
        }
    }
}

@Serializable
internal data object SourceReaderEmpty
