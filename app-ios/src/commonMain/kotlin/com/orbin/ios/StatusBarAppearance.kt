package com.orbin.ios

/** Reader app bars and immersive routes use light icons; privacy covers use their selected palette. */
internal fun lightStatusBarIcons(
    route: Route,
    darkTheme: Boolean,
    firstRun: Boolean,
    lockState: LockState,
): Boolean = darkTheme || (!firstRun && !lockState.locked && !lockState.obscured && route.hasReaderChrome)

private val Route.hasReaderChrome: Boolean
    get() =
        when (this) {
            Route.Feed, Route.Boards, Route.Watched, Route.Saved, Route.Search,
            Route.Settings, Route.Downloads, Route.History,
            is Route.Catalog, is Route.BoardMedia, is Route.ThreadPage,
            is Route.Media, is Route.ThreadVideos,
            -> true
        }
