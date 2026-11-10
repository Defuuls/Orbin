package com.orbin.app

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.orbin.app.navigation.OrbinNavHost
import com.orbin.app.navigation.Route
import com.orbin.app.navigation.navigateToTab
import com.orbin.core.model.showsTwoPanes
import com.orbin.uinext.NextSnackbarHost
import com.orbin.uinext.next
import com.orbin.uinext.tokens.NextMotion
import com.orbin.uinext.tokens.NextType

/**
 * Root composable. Home, Boards, Watched, Search and Saved own the shared five-tab chrome;
 * settings, downloads, threads and catalogs use hierarchical back navigation.
 */
@Composable
fun OrbinApp(
    navController: NavHostController = rememberNavController(),
    startWithOnboarding: Boolean = false,
    isOnline: Boolean = true,
    pendingRoute: Route? = null,
    onPendingRouteHandled: () -> Unit = {},
    onOnboardingCompleted: () -> Unit = {},
) {
    val initialStartDestination = remember { if (startWithOnboarding) Route.Onboarding else Route.NextFeed }
    val currentEntry by navController.currentBackStackEntryAsState()
    val immersive =
        currentEntry?.destination?.let {
            it.hasRoute(Route.NextFeed::class) ||
                it.hasRoute(Route.Gallery::class) ||
                it.hasRoute(Route.ThreadVideos::class)
        } ?: !startWithOnboarding
    SystemBarAppearance(immersive = immersive)

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val twoPaneBoardDetail = showsTwoPanes(maxWidth.value.toInt(), maxHeight.value.toInt())
        val snackbarHostState = LocalOrbinSnackbarHostState.current
        // The five primary destinations stay reachable while their content scrolls.
        // The banner floats over the top of whatever screen is showing rather than pushing it
        // down: losing the network should not make the whole layout jump, twice.
        val statusBarInset = WindowInsets.safeDrawing.only(WindowInsetsSides.Top)
        Box(modifier = Modifier.fillMaxSize()) {
            // Each destination owns its insets via its own top bar / chrome; the root no longer
            // uses Material Scaffold — NextSnackbarHost is the toast layer.
            OnboardingNavigation(
                navController = navController,
                startWithOnboarding = startWithOnboarding,
                pendingRoute = pendingRoute,
                onPendingRouteHandled = onPendingRouteHandled,
                onOnboardingCompleted = onOnboardingCompleted,
            ) { acknowledgeCompletion ->
                OrbinNavHost(
                    navController = navController,
                    modifier = Modifier.fillMaxSize(),
                    startDestination = initialStartDestination,
                    chromeHidesOnScroll = false,
                    twoPaneBoardDetail = twoPaneBoardDetail,
                    onOnboardingCompleted = acknowledgeCompletion,
                )
            }
            AnimatedVisibility(
                visible = !isOnline,
                modifier = Modifier.align(Alignment.TopCenter),
                enter =
                    fadeIn(tween(NextMotion.CHROME_MS, easing = NextMotion.Ease)) +
                        slideInVertically(tween(NextMotion.CHROME_MS, easing = NextMotion.Ease)),
                exit =
                    slideOutVertically(tween(NextMotion.CHROME_MS, easing = NextMotion.Ease)) +
                        fadeOut(tween(NextMotion.CHROME_MS, easing = NextMotion.Ease)),
            ) {
                OfflineBanner(modifier = Modifier.windowInsetsPadding(statusBarInset))
            }
            NextSnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

/** A successful wizard callback takes effect before asynchronously collected settings catch up. */
@Composable
internal fun OnboardingNavigation(
    navController: NavHostController,
    startWithOnboarding: Boolean,
    pendingRoute: Route?,
    onPendingRouteHandled: () -> Unit,
    onOnboardingCompleted: () -> Unit,
    content: @Composable (onCompleted: () -> Unit) -> Unit,
) {
    // Deliberately not saved: a new process must validate its actual persisted board follows.
    var onboardingAcknowledged by remember { mutableStateOf(false) }
    val onCompleted by rememberUpdatedState(onOnboardingCompleted)
    val acknowledgeCompletion =
        remember {
            {
                onboardingAcknowledged = true
                onCompleted()
            }
        }
    val needsSetup = startWithOnboarding && !onboardingAcknowledged
    val currentEntry by navController.currentBackStackEntryAsState()
    LaunchedEffect(needsSetup, currentEntry) {
        if (needsSetup && currentEntry?.destination?.hasRoute(Route.Onboarding::class) == false) {
            navController.navigate(Route.Onboarding) {
                popUpTo(navController.graph.id) { inclusive = true }
                launchSingleTop = true
            }
        }
    }
    LaunchedEffect(pendingRoute, needsSetup, currentEntry) {
        if (needsSetup || currentEntry?.destination?.hasRoute(Route.Onboarding::class) != false) {
            return@LaunchedEffect
        }
        val route = pendingRoute ?: return@LaunchedEffect
        when (route) {
            Route.NextFeed, Route.BoardGallery, Route.Watched, Route.Search, Route.Saved ->
                navController.navigateToTab(
                    route,
                )
            else -> navController.navigate(route)
        }
        onPendingRouteHandled()
    }
    content(acknowledgeCompletion)
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

/** Match native system icons to the actual surface, including a black Home in a light app. */
@Composable
internal fun SystemBarAppearance(immersive: Boolean = false) {
    val activity = LocalView.current.context.findActivity() as? ComponentActivity ?: return
    val darkIcons = !next.dark && !immersive
    SideEffect {
        val style =
            if (darkIcons) {
                SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
            } else {
                SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
            }
        activity.enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
    }
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
