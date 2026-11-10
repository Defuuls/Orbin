package com.orbin.ios

/** Immersive routes have black chrome; onboarding and privacy covers use the selected palette. */
internal fun lightStatusBarIcons(
    route: Route,
    darkTheme: Boolean,
    firstRun: Boolean,
    lockState: LockState,
): Boolean =
    darkTheme ||
        (!firstRun && !lockState.locked && !lockState.obscured && (route == Route.Feed || route is Route.Media))
