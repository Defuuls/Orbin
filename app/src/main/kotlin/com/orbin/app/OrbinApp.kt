package com.orbin.app

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.orbin.app.command.CommandAction
import com.orbin.app.command.CommandDestination
import com.orbin.app.command.CommandHost
import com.orbin.app.command.CommandTarget
import com.orbin.app.navigation.OrbinNavHost
import com.orbin.app.navigation.Route
import com.orbin.core.model.ThreadPresentation
import com.orbin.uinext.NextSnackbarHost
import com.orbin.uinext.next
import com.orbin.uinext.tokens.NextMotion
import com.orbin.uinext.tokens.NextType

/**
 * Root composable. Primary destinations (Feed, Boards, Media, Settings) own DestinationPill
 * chrome; Thread / catalog / Search / Downloads use ContextRail. Search and Downloads open through
 * Command only.
 */
@Composable
fun OrbinApp(
    navController: NavHostController = rememberNavController(),
    startWithOnboarding: Boolean = false,
    fullScreenFeedChrome: Boolean = false,
    threadPresentation: ThreadPresentation = ThreadPresentation.PAGE,
    isOnline: Boolean = true,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val twoPaneBoardDetail = maxWidth >= TWO_PANE_MIN_WIDTH
        val backStackEntry by navController.currentBackStackEntryAsState()
        val currentDestination = backStackEntry?.destination
        val snackbarHostState = LocalOrbinSnackbarHostState.current

        val isNextFeed = currentDestination?.hasRoute(Route.NextFeed::class) == true
        val isAllMedia = currentDestination?.hasRoute(Route.AllMedia::class) == true
        // The three screens built out of the same scrolling list and the same floating rail. The
        // setting used to reach only the feed, so a reader who had turned it on watched the rail
        // slide away there and stay pinned on a catalog drawn from the identical row — which reads
        // as a bug rather than as a distinction, because it is not one.
        val scrollAwayScreen =
            isNextFeed ||
                currentDestination?.hasRoute(Route.Board::class) == true ||
                isAllMedia
        val chromeHidesOnScroll = scrollAwayScreen
        var chromeVisible by rememberSaveable { mutableStateOf(true) }
        var feedScrollToTopRequest by rememberSaveable { mutableIntStateOf(0) }
        var feedRefreshRequest by rememberSaveable { mutableIntStateOf(0) }
        var commandsOpen by rememberSaveable { mutableStateOf(false) }
        var feedFilter by rememberSaveable { mutableStateOf("") }

        LaunchedEffect(chromeHidesOnScroll) {
            if (!chromeHidesOnScroll) {
                chromeVisible = true
            }
        }
        // Leaving a scroll-away screen must not carry its hidden state onto the next one, which
        // may have no rail to bring back.
        LaunchedEffect(scrollAwayScreen) {
            if (!scrollAwayScreen) {
                chromeVisible = true
            }
        }

        // True full screen: while the rail is scrolled away, also hide the status and navigation
        // bars so the screen uses the entire display instead of leaving inset strips.
        val view = LocalView.current
        val immersive = scrollAwayScreen && fullScreenFeedChrome && !chromeVisible
        DisposableEffect(view, immersive) {
            val window = view.context.findActivity()?.window
            val controller = window?.let { WindowCompat.getInsetsController(it, view) }
            if (controller != null) {
                if (immersive) {
                    controller.systemBarsBehavior =
                        WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    controller.hide(WindowInsetsCompat.Type.systemBars())
                } else {
                    controller.show(WindowInsetsCompat.Type.systemBars())
                }
            }
            onDispose {
                if (immersive) {
                    controller?.show(WindowInsetsCompat.Type.systemBars())
                }
            }
        }

        // The banner sits above everything, so it is the thing that has to clear the status bar
        // while it is showing — and then say so, or the screen below it pads for a status bar that
        // is no longer over any of its content.
        val statusBarInset = WindowInsets.safeDrawing.only(WindowInsetsSides.Top)
        Column(modifier = Modifier.fillMaxSize()) {
            AnimatedVisibility(
                visible = !isOnline,
                enter =
                    fadeIn(tween(NextMotion.CHROME_MS, easing = NextMotion.Ease)) +
                        slideInVertically(tween(NextMotion.CHROME_MS, easing = NextMotion.Ease)),
                exit =
                    slideOutVertically(tween(NextMotion.CHROME_MS, easing = NextMotion.Ease)) +
                        fadeOut(tween(NextMotion.CHROME_MS, easing = NextMotion.Ease)),
            ) {
                OfflineBanner(modifier = Modifier.windowInsetsPadding(statusBarInset))
            }
            Box(
                modifier =
                    Modifier
                        .weight(1f)
                        .then(if (isOnline) Modifier else Modifier.consumeWindowInsets(statusBarInset)),
            ) {
                // Each destination owns its insets via its own top bar / chrome; the root no longer
                // uses Material Scaffold — NextSnackbarHost is the toast layer.
                OrbinNavHost(
                    navController = navController,
                    modifier = Modifier.fillMaxSize(),
                    startDestination = if (startWithOnboarding) Route.Onboarding else Route.NextFeed,
                    chromeHidesOnScroll = chromeHidesOnScroll,
                    twoPaneBoardDetail = twoPaneBoardDetail,
                    subscribedFeedScrollToTopRequest = feedScrollToTopRequest,
                    subscribedFeedRefreshRequest = feedRefreshRequest,
                    threadPresentation = threadPresentation,
                    onChromeVisibleChange = { chromeVisible = it },
                    onOpenCommands = { commandsOpen = true },
                    feedFilter = feedFilter,
                    onClearFeedFilter = { feedFilter = "" },
                )
                NextSnackbarHost(
                    hostState = snackbarHostState,
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
        }
        if (commandsOpen) {
            CommandHost(
                onDismiss = { commandsOpen = false },
                onSelect = { target ->
                    commandsOpen = false
                    navController.follow(
                        target = target,
                        onRefreshFeed = { feedRefreshRequest++ },
                        onScrollToTop = { feedScrollToTopRequest++ },
                        onFilterFeed = { query -> feedFilter = query },
                    )
                },
            )
        }
    }
}

/**
 * Sends the user wherever a command points, or performs it if it is not a place.
 *
 * Kept out of [OrbinApp] so the root composable stays a layout rather than also being the
 * navigation table for every command.
 */
private fun NavHostController.follow(
    target: CommandTarget,
    onRefreshFeed: () -> Unit,
    onScrollToTop: () -> Unit,
    onFilterFeed: (String) -> Unit,
) {
    when (target) {
        is CommandTarget.OpenBoard -> navigate(Route.Board(target.provider, target.board, target.title))
        is CommandTarget.OpenThread ->
            navigate(Route.Thread(target.provider, target.board, target.thread, target.label))

        is CommandTarget.OpenSetting -> navigate(Route.Settings(focus = target.settingId))
        is CommandTarget.Go -> navigate(target.destination.route())
        is CommandTarget.Act ->
            when (target.action) {
                CommandAction.REFRESH_FEED -> onRefreshFeed()
                CommandAction.SCROLL_TO_TOP -> onScrollToTop()
                // Served inside the command surface itself: it holds the lock controller, and
                // locking must not depend on which screen is behind the sheet.
                CommandAction.LOCK_NOW -> Unit
                CommandAction.FILTER_FEED -> onFilterFeed(target.query)
            }
    }
}

private fun CommandDestination.route(): Route =
    when (this) {
        CommandDestination.FEED -> Route.NextFeed
        CommandDestination.ALL_MEDIA -> Route.AllMedia
        CommandDestination.BOARDS -> Route.BoardGallery
        CommandDestination.DOWNLOADS -> Route.Downloads
        CommandDestination.SEARCH -> Route.Search
        CommandDestination.SETTINGS -> Route.Settings()
    }

@Composable
private fun OfflineBanner(modifier: Modifier = Modifier) {
    Text(
        text = stringResource(R.string.offline_banner),
        modifier =
            modifier
                .fillMaxWidth()
                .background(next.accentSoft)
                .padding(horizontal = 16.dp, vertical = 8.dp),
        color = next.accent,
        style = NextType.footnote,
        textAlign = TextAlign.Center,
    )
}

private tailrec fun Context.findActivity(): Activity? =
    when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }

/**
 * Width at which the catalog and a thread are shown side by side.
 *
 * Material's expanded breakpoint: 840dp is enough to split into two readable columns.
 */
private val TWO_PANE_MIN_WIDTH = 840.dp
