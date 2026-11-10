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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
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
 * Root composable. Home, Search, Boards and Saved own the shared four-tab chrome;
 * settings, downloads, threads and catalogs use hierarchical back navigation.
 */
@Composable
fun OrbinApp(
    navController: NavHostController = rememberNavController(),
    startWithOnboarding: Boolean = false,
    isOnline: Boolean = true,
    pendingRoute: Route? = null,
    onPendingRouteHandled: () -> Unit = {},
) {
    LaunchedEffect(pendingRoute, startWithOnboarding) {
        if (startWithOnboarding) return@LaunchedEffect
        val route = pendingRoute ?: return@LaunchedEffect
        when (route) {
            Route.NextFeed, Route.Search, Route.Saved, Route.BoardGallery -> navController.navigateToTab(route)
            else -> navController.navigate(route)
        }
        onPendingRouteHandled()
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val twoPaneBoardDetail = showsTwoPanes(maxWidth.value.toInt(), maxHeight.value.toInt())
        val snackbarHostState = LocalOrbinSnackbarHostState.current
        // The four primary destinations stay reachable while their content scrolls.
        // The banner floats over the top of whatever screen is showing rather than pushing it
        // down: losing the network should not make the whole layout jump, twice.
        val statusBarInset = WindowInsets.safeDrawing.only(WindowInsetsSides.Top)
        Box(modifier = Modifier.fillMaxSize()) {
            // Each destination owns its insets via its own top bar / chrome; the root no longer
            // uses Material Scaffold — NextSnackbarHost is the toast layer.
            OrbinNavHost(
                navController = navController,
                modifier = Modifier.fillMaxSize(),
                startDestination = if (startWithOnboarding) Route.Onboarding else Route.NextFeed,
                chromeHidesOnScroll = false,
                twoPaneBoardDetail = twoPaneBoardDetail,
            )
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
