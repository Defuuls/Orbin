package com.orbin.app

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.font.FontWeight
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
import com.orbin.app.navigation.TopLevelDestination
import com.orbin.core.designsystem.component.ModernNavigationBar
import com.orbin.core.designsystem.component.ModernNavigationBarItem
import com.orbin.core.model.ThreadPresentation
import com.orbin.feature.settings.SettingsSection

/**
 * Root composable: a [Scaffold] whose bottom navigation bar is shown only on the top-level
 * destinations. Detail screens (board, thread, settings) take over the full screen.
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
        val tabletFeedChrome = maxWidth >= TABLET_MIN_WIDTH && maxHeight >= TABLET_MIN_HEIGHT
        val compactTabletDock = maxWidth < COMPACT_TABLET_DOCK_WIDTH
        val twoPaneBoardDetail = maxWidth >= TWO_PANE_MIN_WIDTH
        val backStackEntry by navController.currentBackStackEntryAsState()
        val currentDestination = backStackEntry?.destination
        val snackbarHostState = LocalOrbinSnackbarHostState.current

        val topLevel = TopLevelDestination.entries
        val isNextFeed = currentDestination?.hasRoute(Route.NextFeed::class) == true
        // The redesigned feed carries its own rail, and the rail is what replaces this bar. Showing
        // both would stack two pieces of navigation chrome on a screen whose whole argument is that
        // it needs none.
        val showBottomBar =
            !isNextFeed && topLevel.any { dest -> currentDestination?.hasRoute(dest.route::class) == true }
        val isSubscribedFeed = currentDestination?.hasRoute(Route.SubscribedFeed::class) == true
        val feedChromeHidesOnScroll =
            (isSubscribedFeed || isNextFeed) && (fullScreenFeedChrome || tabletFeedChrome)
        var feedChromeVisible by rememberSaveable { mutableStateOf(true) }
        var feedScrollToTopRequest by rememberSaveable { mutableIntStateOf(0) }
        var feedRefreshRequest by rememberSaveable { mutableIntStateOf(0) }
        var commandsOpen by rememberSaveable { mutableStateOf(false) }
        val bottomBarVisible = showBottomBar && (!feedChromeHidesOnScroll || feedChromeVisible)
        // All three navigation surfaces ask the same question; the null-safe call also drops a
        // redundant guard the compiler could already prove true in the tablet-dock branch.
        val destinationMatches: (TopLevelDestination) -> Boolean = { destination ->
            currentDestination?.hasRoute(destination.route::class) == true
        }
        val useTabletDock = showBottomBar && tabletFeedChrome
        val useTabletFeedDock = isSubscribedFeed && tabletFeedChrome

        LaunchedEffect(feedChromeHidesOnScroll) {
            if (!feedChromeHidesOnScroll) {
                feedChromeVisible = true
            }
        }

        // True full screen: while the feed chrome is scrolled away, also hide the status and
        // navigation bars so the feed uses the entire display instead of leaving inset strips.
        val view = LocalView.current
        val immersiveFeed = (isSubscribedFeed || isNextFeed) && fullScreenFeedChrome && !feedChromeVisible
        DisposableEffect(view, immersiveFeed) {
            val window = view.context.findActivity()?.window
            val controller = window?.let { WindowCompat.getInsetsController(it, view) }
            if (controller != null) {
                if (immersiveFeed) {
                    controller.systemBarsBehavior =
                        WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    controller.hide(WindowInsetsCompat.Type.systemBars())
                } else {
                    controller.show(WindowInsetsCompat.Type.systemBars())
                }
            }
            onDispose {
                if (immersiveFeed) {
                    controller?.show(WindowInsetsCompat.Type.systemBars())
                }
            }
        }

        Column(modifier = Modifier.fillMaxSize()) {
            AnimatedVisibility(
                visible = !isOnline,
                enter = fadeIn() + slideInVertically(),
                exit = slideOutVertically() + fadeOut(),
            ) {
                OfflineBanner()
            }
            Scaffold(
                modifier = Modifier.weight(1f),
                snackbarHost = { SnackbarHost(snackbarHostState) },
                // Each destination owns its insets via its own top bar / scaffold; applying the
                // default insets here as well double-pads content with status/navigation-bar strips.
                contentWindowInsets = WindowInsets(0),
                bottomBar = {
                    AnimatedVisibility(
                        visible = bottomBarVisible,
                        enter = slideInVertically { it } + fadeIn(),
                        exit = slideOutVertically { it } + fadeOut(),
                    ) {
                        if (useTabletFeedDock) {
                            TabletFeedDock(
                                topLevel = topLevel,
                                currentDestinationMatches = destinationMatches,
                                compact = compactTabletDock,
                                onNavigate = navController::navigateToTopLevel,
                                onScrollToTop = { feedScrollToTopRequest++ },
                                onRefresh = { feedRefreshRequest++ },
                                onOpenSettings = { navController.navigate(Route.Settings) },
                            )
                        } else if (useTabletDock) {
                            TabletNavigationDock(
                                topLevel = topLevel,
                                currentDestinationMatches = destinationMatches,
                                onNavigate = navController::navigateToTopLevel,
                            )
                        } else {
                            PhoneNavigationBar(
                                topLevel = topLevel,
                                currentDestinationMatches = destinationMatches,
                                onNavigate = navController::navigateToTopLevel,
                            )
                        }
                    }
                },
            ) { padding ->
                OrbinNavHost(
                    navController = navController,
                    modifier = Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding),
                    startDestination = if (startWithOnboarding) Route.Onboarding else Route.NextFeed,
                    subscribedFeedChromeHidesOnScroll = feedChromeHidesOnScroll,
                    subscribedFeedShowBoardHeaders = !fullScreenFeedChrome,
                    hideSubscribedFeedTopBar = useTabletFeedDock,
                    tabletSubscribedFeedLayout = useTabletFeedDock,
                    twoPaneBoardDetail = twoPaneBoardDetail,
                    subscribedFeedScrollToTopRequest = feedScrollToTopRequest,
                    subscribedFeedRefreshRequest = feedRefreshRequest,
                    threadPresentation = threadPresentation,
                    onFeedChromeVisibleChange = { feedChromeVisible = it },
                    onOpenCommands = { commandsOpen = true },
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
) {
    when (target) {
        is CommandTarget.OpenBoard -> navigate(Route.Board(target.provider, target.board, target.title))
        is CommandTarget.OpenThread ->
            navigate(Route.Thread(target.provider, target.board, target.thread, target.label))

        is CommandTarget.OpenSetting -> navigate(target.section.route())
        is CommandTarget.Go -> navigate(target.destination.route())
        is CommandTarget.Act ->
            when (target.action) {
                CommandAction.REFRESH_FEED -> onRefreshFeed()
                CommandAction.SCROLL_TO_TOP -> onScrollToTop()
                // Served inside the command surface itself: it holds the lock controller, and
                // locking must not depend on which screen is behind the sheet.
                CommandAction.LOCK_NOW -> Unit
            }
    }
}

private fun SettingsSection.route(): Route =
    when (this) {
        SettingsSection.CONTENT -> Route.SettingsContent
        SettingsSection.NOTIFICATIONS -> Route.SettingsNotifications
        SettingsSection.APPEARANCE -> Route.SettingsAppearance
        SettingsSection.MEDIA -> Route.SettingsMedia
        SettingsSection.PRIVACY -> Route.SettingsPrivacy
        SettingsSection.ADVANCED -> Route.SettingsAdvanced
        SettingsSection.STORAGE -> Route.SettingsStorage
    }

private fun CommandDestination.route(): Route =
    when (this) {
        CommandDestination.GALLERY -> Route.GalleryBrowser
        CommandDestination.ALL_MEDIA -> Route.AllMedia
        CommandDestination.BOARDS -> Route.BoardGallery
        CommandDestination.SUBSCRIPTIONS -> Route.Subscriptions
        CommandDestination.HISTORY -> Route.History
        CommandDestination.DOWNLOADS -> Route.Downloads
        CommandDestination.SEARCH -> Route.Search
        CommandDestination.SETTINGS -> Route.Settings
        CommandDestination.CLASSIC_FEED -> Route.SubscribedFeed
    }

@Composable
private fun OfflineBanner() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.errorContainer,
    ) {
        Text(
            text = "You're offline",
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            color = MaterialTheme.colorScheme.onErrorContainer,
            style = MaterialTheme.typography.labelMedium,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun PhoneNavigationBar(
    topLevel: List<TopLevelDestination>,
    currentDestinationMatches: (TopLevelDestination) -> Boolean,
    onNavigate: (TopLevelDestination) -> Unit,
) {
    ModernNavigationBar {
        TopLevelNavigationItems(
            topLevel = topLevel,
            currentDestinationMatches = currentDestinationMatches,
            onNavigate = onNavigate,
        )
    }
}

@Composable
private fun TabletNavigationDock(
    topLevel: List<TopLevelDestination>,
    currentDestinationMatches: (TopLevelDestination) -> Boolean,
    onNavigate: (TopLevelDestination) -> Unit,
) {
    FloatingDockSurface {
        ModernNavigationBar(modifier = Modifier.fillMaxWidth()) {
            TopLevelNavigationItems(
                topLevel = topLevel,
                currentDestinationMatches = currentDestinationMatches,
                onNavigate = onNavigate,
            )
        }
    }
}

@Composable
private fun TabletFeedDock(
    topLevel: List<TopLevelDestination>,
    currentDestinationMatches: (TopLevelDestination) -> Boolean,
    compact: Boolean,
    onNavigate: (TopLevelDestination) -> Unit,
    onScrollToTop: () -> Unit,
    onRefresh: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    FloatingDockSurface {
        if (compact) {
            Column(modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)) {
                FeedDockTopActions(
                    modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp),
                    onScrollToTop = onScrollToTop,
                    onRefresh = onRefresh,
                    onOpenSettings = onOpenSettings,
                )
                ModernNavigationBar(modifier = Modifier.fillMaxWidth()) {
                    TopLevelNavigationItems(
                        topLevel = topLevel,
                        currentDestinationMatches = currentDestinationMatches,
                        onNavigate = onNavigate,
                    )
                }
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp).padding(start = 20.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                FeedDockTopActions(
                    modifier = Modifier.weight(0.72f),
                    onScrollToTop = onScrollToTop,
                    onRefresh = onRefresh,
                    onOpenSettings = onOpenSettings,
                )
                ModernNavigationBar(
                    modifier = Modifier.weight(1.28f),
                ) {
                    TopLevelNavigationItems(
                        topLevel = topLevel,
                        currentDestinationMatches = currentDestinationMatches,
                        onNavigate = onNavigate,
                    )
                }
            }
        }
    }
}

@Composable
private fun FloatingDockSurface(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Surface(
            modifier = Modifier.widthIn(max = 920.dp),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp,
            shadowElevation = 8.dp,
        ) {
            content()
        }
    }
}

@Composable
private fun FeedDockTopActions(
    modifier: Modifier = Modifier,
    onScrollToTop: () -> Unit,
    onRefresh: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "Subscribed",
            modifier = Modifier.weight(1f).clickable(onClickLabel = "Scroll to top", onClick = onScrollToTop),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        IconButton(onClick = onRefresh) {
            Icon(Icons.Filled.Refresh, contentDescription = "Refresh feed")
        }
        IconButton(onClick = onOpenSettings) {
            Icon(Icons.Filled.Settings, contentDescription = "Settings")
        }
    }
}

@Composable
private fun RowScope.TopLevelNavigationItems(
    topLevel: List<TopLevelDestination>,
    currentDestinationMatches: (TopLevelDestination) -> Boolean,
    onNavigate: (TopLevelDestination) -> Unit,
) {
    topLevel.forEach { dest ->
        ModernNavigationBarItem(
            icon = dest.icon,
            label = dest.label,
            selected = currentDestinationMatches(dest),
            onClick = { onNavigate(dest) },
        )
    }
}

private tailrec fun Context.findActivity(): Activity? =
    when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }

private fun NavHostController.navigateToTopLevel(destination: TopLevelDestination) {
    navigate(destination.route) {
        // Single instance per tab, preserving each tab's own back stack and scroll state.
        popUpTo(graph.startDestinationId) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

private val TABLET_MIN_WIDTH = 600.dp
private val TABLET_MIN_HEIGHT = 480.dp
private val COMPACT_TABLET_DOCK_WIDTH = 720.dp

/**
 * Width at which the catalog and a thread are shown side by side.
 *
 * Material's expanded breakpoint, and higher than [TABLET_MIN_WIDTH] on purpose: 600dp is enough
 * to justify a roomier dock but not to split into two readable columns. Height is not part of the
 * test — two columns work in landscape on a short viewport, where the tablet feed chrome does not.
 */
private val TWO_PANE_MIN_WIDTH = 840.dp
