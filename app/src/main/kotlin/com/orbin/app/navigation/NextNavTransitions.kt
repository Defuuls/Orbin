package com.orbin.app.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.ui.unit.IntOffset
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import com.orbin.core.model.ThreadPresentation
import com.orbin.uinext.tokens.NextMotion

/** @see NextMotion.Ease */
internal val NextNavEasing = NextMotion.Ease

private fun offsetSpec(push: Boolean) =
    tween<IntOffset>(
        durationMillis = if (push) NextMotion.PUSH_MS else NextMotion.TAB_MS,
        easing = NextMotion.Ease,
    )

private fun fadeSpec(push: Boolean) =
    tween<Float>(
        durationMillis = if (push) NextMotion.PUSH_MS else NextMotion.TAB_MS,
        easing = NextMotion.Ease,
    )

/**
 * DestinationPill tabs — Feed / Downloads / Boards, in pill order. Swaps between these should
 * crossfade softly, not push like a hierarchical drill-in. Settings is not a tab; it slides over.
 */
internal fun NavDestination.isPrimaryTab(): Boolean =
    hasRoute(Route.NextFeed::class) ||
        hasRoute(Route.Downloads::class) ||
        hasRoute(Route.BoardGallery::class)

private const val TAB_FEED = 0
private const val TAB_DOWNLOADS = 1
private const val TAB_BOARDS = 2
private const val TAB_UNKNOWN = -1

internal fun NavDestination.primaryTabIndex(): Int =
    when {
        hasRoute(Route.NextFeed::class) -> TAB_FEED
        hasRoute(Route.Downloads::class) -> TAB_DOWNLOADS
        hasRoute(Route.BoardGallery::class) -> TAB_BOARDS
        else -> TAB_UNKNOWN
    }

internal fun NavDestination.slidesOver(threadPresentation: ThreadPresentation): Boolean =
    hasRoute(Route.Settings::class) ||
        (threadPresentation == ThreadPresentation.OVERLAY && hasRoute(Route.Thread::class))

internal fun areSiblingTabs(
    initial: NavDestination,
    target: NavDestination,
): Boolean = initial.isPrimaryTab() && target.isPrimaryTab()

internal fun AnimatedContentTransitionScope<NavBackStackEntry>.nextEnter(
    threadPresentation: ThreadPresentation,
): EnterTransition {
    val initial = initialState.destination
    val target = targetState.destination
    return when {
        target.slidesOver(threadPresentation) ->
            slideInHorizontally(offsetSpec(push = true)) { width -> width } + fadeIn(fadeSpec(push = true))
        areSiblingTabs(initial, target) -> siblingEnter(initial, target)
        else ->
            slideIntoContainer(
                AnimatedContentTransitionScope.SlideDirection.Start,
                offsetSpec(push = true),
            ) + fadeIn(fadeSpec(push = true))
    }
}

internal fun AnimatedContentTransitionScope<NavBackStackEntry>.nextExit(
    threadPresentation: ThreadPresentation,
): ExitTransition {
    val initial = initialState.destination
    val target = targetState.destination
    return when {
        target.slidesOver(threadPresentation) -> ExitTransition.None
        areSiblingTabs(initial, target) -> siblingExit(initial, target)
        else ->
            slideOutOfContainer(
                AnimatedContentTransitionScope.SlideDirection.Start,
                offsetSpec(push = true),
            ) + fadeOut(fadeSpec(push = true))
    }
}

internal fun AnimatedContentTransitionScope<NavBackStackEntry>.nextPopEnter(
    threadPresentation: ThreadPresentation,
): EnterTransition {
    val initial = initialState.destination
    val target = targetState.destination
    return when {
        initial.slidesOver(threadPresentation) -> EnterTransition.None
        areSiblingTabs(initial, target) -> siblingEnter(initial, target)
        else ->
            slideIntoContainer(
                AnimatedContentTransitionScope.SlideDirection.End,
                offsetSpec(push = true),
            ) + fadeIn(fadeSpec(push = true))
    }
}

internal fun AnimatedContentTransitionScope<NavBackStackEntry>.nextPopExit(
    threadPresentation: ThreadPresentation,
): ExitTransition {
    val initial = initialState.destination
    val target = targetState.destination
    return when {
        initial.slidesOver(threadPresentation) ->
            slideOutHorizontally(offsetSpec(push = true)) { width -> width } + fadeOut(fadeSpec(push = true))
        areSiblingTabs(initial, target) -> siblingExit(initial, target)
        else ->
            slideOutOfContainer(
                AnimatedContentTransitionScope.SlideDirection.End,
                offsetSpec(push = true),
            ) + fadeOut(fadeSpec(push = true))
    }
}

private fun AnimatedContentTransitionScope<NavBackStackEntry>.siblingEnter(
    initial: NavDestination,
    target: NavDestination,
): EnterTransition {
    val forward = target.primaryTabIndex() >= initial.primaryTabIndex()
    val direction =
        if (forward) {
            AnimatedContentTransitionScope.SlideDirection.Start
        } else {
            AnimatedContentTransitionScope.SlideDirection.End
        }
    return slideIntoContainer(direction, offsetSpec(push = false)) { full ->
        (full * NextMotion.TAB_NUDGE).toInt().coerceAtLeast(1)
    } + fadeIn(fadeSpec(push = false))
}

private fun AnimatedContentTransitionScope<NavBackStackEntry>.siblingExit(
    initial: NavDestination,
    target: NavDestination,
): ExitTransition {
    val forward = target.primaryTabIndex() >= initial.primaryTabIndex()
    val direction =
        if (forward) {
            AnimatedContentTransitionScope.SlideDirection.Start
        } else {
            AnimatedContentTransitionScope.SlideDirection.End
        }
    return slideOutOfContainer(direction, offsetSpec(push = false)) { full ->
        (full * NextMotion.TAB_NUDGE).toInt().coerceAtLeast(1)
    } + fadeOut(fadeSpec(push = false))
}
