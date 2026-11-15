package com.orbin.ios

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.SaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.orbin.core.model.ThreadKey
import com.orbin.core.model.readerPaneWidths
import com.orbin.core.model.showsTwoPanes
import com.orbin.ios.resources.Res
import com.orbin.ios.resources.ios_collection_history
import com.orbin.ios.resources.ios_collection_saved
import com.orbin.ios.resources.ios_collection_watched
import com.orbin.uinext.NextDestination
import com.orbin.uinext.ReaderScaffold
import com.orbin.uinext.ReaderTwoPane
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

/** Collections and search retain their own identity while selecting threads from any board/site. */
internal data class SourceReaderRoute(
    val source: Route,
    val thread: Route.ThreadPage? = null,
) {
    init {
        require(source.isThreadReaderSource)
    }
}

internal val Route.isThreadReaderSource: Boolean
    get() = this == Route.Saved || this == Route.Watched || this == Route.History || this == Route.Search

/** Only the immediately preceding source can own the selected thread, preserving normal Back. */
internal fun sourceReaderRoute(backStack: List<Route>): SourceReaderRoute? =
    when (val route = backStack.lastOrNull()) {
        null -> null
        is Route.ThreadPage ->
            backStack
                .getOrNull(backStack.lastIndex - 1)
                ?.takeIf { it.isThreadReaderSource }
                ?.let { SourceReaderRoute(it, route) }
        else -> route.takeIf { it.isThreadReaderSource }?.let(::SourceReaderRoute)
    }

@Composable
internal fun SourceReaderDestination(
    browser: Browser,
    route: SourceReaderRoute,
    downloads: MediaDownloads,
    pages: ThreadPages?,
    stateHolder: SaveableStateHolder,
) {
    SourceReaderFrame(
        route = route,
        stateHolder = stateHolder,
        sourceContent = { source, _ ->
            if (source == Route.Search) SearchDestination(browser) else ThreadCollectionsDestination(browser, source)
        },
        threadContent = { ThreadDestination(browser, downloads, pages) },
        onDestination = browser::openDestination,
    )
}

/** Source/query/scroll and thread state move with their providers when the window changes size. */
@Composable
internal fun SourceReaderFrame(
    route: SourceReaderRoute,
    stateHolder: SaveableStateHolder,
    sourceContent: @Composable (Route, ThreadKey?) -> Unit,
    threadContent: @Composable (Route.ThreadPage) -> Unit,
    onDestination: ((NextDestination) -> Unit)? = null,
) {
    val sourcePane =
        remember(stateHolder) {
            movableContentOf<SourceReaderRoute, @Composable (Route, ThreadKey?) -> Unit> { reader, content ->
                stateHolder.SaveableStateProvider(reader.source.stateKey) { content(reader.source, reader.thread?.key) }
            }
        }
    val threadPane =
        remember(stateHolder) {
            movableContentOf<Route.ThreadPage, @Composable (Route.ThreadPage) -> Unit> { thread, content ->
                stateHolder.SaveableStateProvider(thread.stateKey) { content(thread) }
            }
        }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val width = maxWidth.value.roundToInt()
        val wide = showsTwoPanes(width, maxHeight.value.roundToInt())
        val thread = route.thread
        if (wide) {
            ReaderScaffold(
                title =
                    when (route.source) {
                        Route.Search -> "Search"
                        Route.Watched -> stringResource(Res.string.ios_collection_watched)
                        Route.History -> stringResource(Res.string.ios_collection_history)
                        else -> stringResource(Res.string.ios_collection_saved)
                    },
                destination =
                    when (route.source) {
                        Route.Search -> NextDestination.SEARCH
                        Route.Watched -> NextDestination.WATCHED
                        else -> NextDestination.SAVED
                    },
                onDestination = onDestination,
                drawAppBar = false,
            ) {
                ReaderTwoPane(
                    catalogWidth = readerPaneWidths(width).catalogDp.dp,
                    threadOpen = thread != null,
                    modifier = Modifier.fillMaxSize(),
                    listContent = { sourcePane(route, sourceContent) },
                    detailContent = { thread?.let { threadPane(it, threadContent) } },
                )
            }
        } else if (thread == null) {
            sourcePane(route, sourceContent)
        } else {
            threadPane(thread, threadContent)
        }
    }
}
