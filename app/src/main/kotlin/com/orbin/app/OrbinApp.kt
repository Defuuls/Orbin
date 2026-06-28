package com.orbin.app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
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
) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val snackbarHostState = LocalOrbinSnackbarHostState.current

    val topLevel = TopLevelDestination.entries
    val showBottomBar = topLevel.any { dest -> currentDestination?.hasRoute(dest.route::class) == true }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    topLevel.forEach { dest ->
                        val selected = currentDestination?.hasRoute(dest.route::class) == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = { navController.navigateToTopLevel(dest) },
                            icon = { Icon(dest.icon, contentDescription = dest.label) },
                            label = { Text(dest.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        OrbinNavHost(
            navController = navController,
            modifier = Modifier.fillMaxSize().padding(padding),
            startDestination = if (startWithOnboarding) Route.Onboarding else Route.SubscribedFeed,
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
