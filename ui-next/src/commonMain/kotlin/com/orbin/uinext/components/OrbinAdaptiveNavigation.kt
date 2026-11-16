package com.orbin.uinext.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.LayoutDirection

/** Primary screens omit their bottom navigation when the root reserves a real side rail. */
val LocalOrbinNavigationAtSide = staticCompositionLocalOf { false }

object OrbinNavigationTags {
    const val RAIL = "orbin-navigation-rail"
    const val BODY = "orbin-navigation-body"

    /** Each primary tab is tagged with this prefix and its [NextDestination] name, on both platforms. */
    const val ENTRY = "reader-navigation-entry-"
    const val LABEL = "reader-navigation-label"
    const val TOOLTIP = "reader-navigation-tooltip"
    const val BADGE = "reader-navigation-badge"
}

/**
 * NekoSurf's wide-window navigation: a physically reserved left rail from 840dp onwards.
 *
 * Hosts enable it only for primary pages; setup, lock and full-screen media keep the whole
 * viewport. The content stays at the same composition location while resizing, so its state
 * and player ownership are retained. The rail owns the window's left safe inset.
 */
@Composable
fun OrbinAdaptiveNavigation(
    enabled: Boolean,
    modifier: Modifier = Modifier,
    navigation: @Composable () -> Unit,
    content: @Composable () -> Unit,
) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        val atSide = enabled && NavWindow.of(maxWidth) == NavWindow.EXPANDED
        val readingDirection = LocalLayoutDirection.current
        CompositionLocalProvider(
            LocalOrbinNavigationAtSide provides atSide,
            // The reference rail stays at the physical left; each pane keeps its reading direction.
            LocalLayoutDirection provides LayoutDirection.Ltr,
        ) {
            Row(Modifier.fillMaxSize()) {
                if (atSide) {
                    Box(Modifier.fillMaxHeight().testTag(OrbinNavigationTags.RAIL)) {
                        CompositionLocalProvider(LocalLayoutDirection provides readingDirection) { navigation() }
                    }
                }
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        // Keep the closed drawer's offscreen sheet inside its own pane.
                        .clipToBounds()
                        .then(
                            if (atSide) {
                                Modifier.consumeWindowInsets(WindowInsets.safeDrawing.only(WindowInsetsSides.Left))
                            } else {
                                Modifier
                            },
                        ).testTag(OrbinNavigationTags.BODY),
                ) {
                    CompositionLocalProvider(LocalLayoutDirection provides readingDirection) { content() }
                }
            }
        }
    }
}
