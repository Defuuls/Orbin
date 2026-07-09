package com.orbin.app

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
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.orbin.app.navigation.OrbinNavHost
import com.orbin.app.navigation.Route
import com.orbin.app.navigation.TopLevelDestination

/**
 * Root composable: a [Scaffold] whose bottom navigation bar is shown only on the top-level
 * destinations. Detail screens (board, thread, settings) take over the full screen.
 */
@Composable
fun OrbinApp(
    navController: NavHostController = rememberNavController(),
    startWithOnboarding: Boolean = false,
    fullScreenFeedChrome: Boolean = false,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val tabletFeedChrome = maxWidth >= TABLET_MIN_WIDTH && maxHeight >= TABLET_MIN_HEIGHT
        val compactTabletDock = maxWidth < COMPACT_TABLET_DOCK_WIDTH
        val backStackEntry by navController.currentBackStackEntryAsState()
        val currentDestination = backStackEntry?.destination
        val snackbarHostState = LocalOrbinSnackbarHostState.current

        val topLevel = TopLevelDestination.entries
        val showBottomBar = topLevel.any { dest -> currentDestination?.hasRoute(dest.route::class) == true }
        val isSubscribedFeed = currentDestination?.hasRoute(Route.SubscribedFeed::class) == true
        val feedChromeHidesOnScroll = isSubscribedFeed && (fullScreenFeedChrome || tabletFeedChrome)
        var feedChromeVisible by rememberSaveable { mutableStateOf(true) }
        var feedScrollToTopRequest by rememberSaveable { mutableIntStateOf(0) }
        var feedRefreshRequest by rememberSaveable { mutableIntStateOf(0) }
        val bottomBarVisible = showBottomBar && (!feedChromeHidesOnScroll || feedChromeVisible)
        val useTabletDock = showBottomBar && tabletFeedChrome
        val useTabletFeedDock = isSubscribedFeed && tabletFeedChrome

        LaunchedEffect(feedChromeHidesOnScroll) {
            if (!feedChromeHidesOnScroll) {
                feedChromeVisible = true
            }
        }

        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            bottomBar = {
                AnimatedVisibility(
                    visible = bottomBarVisible,
                    enter = slideInVertically { it } + fadeIn(),
                    exit = slideOutVertically { it } + fadeOut(),
                ) {
                    if (useTabletFeedDock) {
                        TabletFeedDock(
                            topLevel = topLevel,
                            currentDestinationMatches = { destination ->
                                currentDestination?.hasRoute(destination.route::class) == true
                            },
                            compact = compactTabletDock,
                            onNavigate = navController::navigateToTopLevel,
                            onScrollToTop = { feedScrollToTopRequest++ },
                            onRefresh = { feedRefreshRequest++ },
                            onOpenSettings = { navController.navigate(Route.Settings) },
                        )
                    } else if (useTabletDock) {
                        TabletNavigationDock(
                            topLevel = topLevel,
                            currentDestinationMatches = { destination ->
                                currentDestination?.hasRoute(destination.route::class) == true
                            },
                            onNavigate = navController::navigateToTopLevel,
                        )
                    } else {
                        PhoneNavigationBar(
                            topLevel = topLevel,
                            currentDestinationMatches = { destination ->
                                currentDestination?.hasRoute(destination.route::class) == true
                            },
                            onNavigate = navController::navigateToTopLevel,
                        )
                    }
                }
            },
        ) { padding ->
            OrbinNavHost(
                navController = navController,
                modifier = Modifier.fillMaxSize().padding(padding),
                startDestination = if (startWithOnboarding) Route.Onboarding else Route.SubscribedFeed,
                subscribedFeedChromeHidesOnScroll = feedChromeHidesOnScroll,
                hideSubscribedFeedTopBar = useTabletFeedDock,
                tabletSubscribedFeedLayout = useTabletFeedDock,
                subscribedFeedScrollToTopRequest = feedScrollToTopRequest,
                subscribedFeedRefreshRequest = feedRefreshRequest,
                onFeedChromeVisibleChange = { feedChromeVisible = it },
            )
        }
    }
}

@Composable
private fun PhoneNavigationBar(
    topLevel: List<TopLevelDestination>,
    currentDestinationMatches: (TopLevelDestination) -> Boolean,
    onNavigate: (TopLevelDestination) -> Unit,
) {
    NavigationBar {
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
        NavigationBar(containerColor = Color.Transparent, tonalElevation = 0.dp) {
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
                NavigationBar(containerColor = Color.Transparent, tonalElevation = 0.dp) {
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
                NavigationBar(
                    modifier = Modifier.weight(1.28f),
                    containerColor = Color.Transparent,
                    tonalElevation = 0.dp,
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
        NavigationBarItem(
            selected = currentDestinationMatches(dest),
            onClick = { onNavigate(dest) },
            icon = { Icon(dest.icon, contentDescription = dest.label) },
            label = { Text(dest.label) },
        )
    }
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
