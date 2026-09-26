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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.orbin.app.navigation.OrbinNavHost
import com.orbin.app.navigation.Route
import com.orbin.uinext.NextSnackbarHost
import com.orbin.uinext.next
import com.orbin.uinext.tokens.NextMotion
import com.orbin.uinext.tokens.NextType

/**
 * Root composable. Feed, Downloads and Boards own DestinationPill chrome; Settings, threads,
 * catalogs and Search draw no bottom chrome, since each already carries its own large title.
 */
@Composable
fun OrbinApp(
    navController: NavHostController = rememberNavController(),
    startWithOnboarding: Boolean = false,
    isOnline: Boolean = true,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val twoPaneBoardDetail = maxWidth >= TWO_PANE_MIN_WIDTH
        val backStackEntry by navController.currentBackStackEntryAsState()
        val currentDestination = backStackEntry?.destination
        val snackbarHostState = LocalOrbinSnackbarHostState.current

        val isNextFeed = currentDestination?.hasRoute(Route.NextFeed::class) == true
        // The three screens built out of the same scrolling list and the same floating rail. The
        // setting used to reach only the feed, so a reader who had turned it on watched the rail
        // slide away there and stay pinned on a catalog drawn from the identical row — which reads
        // as a bug rather than as a distinction, because it is not one.
        val scrollAwayScreen =
            isNextFeed ||
                currentDestination?.hasRoute(Route.Board::class) == true ||
                currentDestination?.hasRoute(Route.BoardGallery::class) == true
        val chromeHidesOnScroll = scrollAwayScreen
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
                chromeHidesOnScroll = chromeHidesOnScroll,
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
private val TWO_PANE_MIN_WIDTH = 840.dp
