package com.orbin.uinext

import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** Rule: every navigation surface is derived from the one spec, and it matches what ships today. */
class NavigationSpecTest {
    @Test
    fun `every destination is defined exactly once`() {
        assertThat(
            ReaderNavigationSpec.entries.map { it.destination },
        ).containsExactlyElementsIn(NextDestination.entries)
    }

    @Test
    fun `bar and rail show the same primary destinations in the same order`() {
        val primary =
            listOf(NextDestination.BOARDS, NextDestination.FEED, NextDestination.DOWNLOADS, NextDestination.SETTINGS)
        assertThat(
            ReaderNavigationSpec.destinations(NavSurface.BAR, NavWindow.COMPACT),
        ).containsExactlyElementsIn(primary).inOrder()
        assertThat(
            ReaderNavigationSpec.destinations(NavSurface.RAIL, NavWindow.EXPANDED),
        ).containsExactlyElementsIn(primary).inOrder()
        assertThat(ReaderNavigationSpec.destinations(NavSurface.BAR, NavWindow.EXPANDED)).isEmpty()
    }

    @Test
    fun `drawer keeps its current order`() {
        assertThat(ReaderNavigationSpec.destinations(NavSurface.DRAWER, NavWindow.COMPACT))
            .containsExactly(
                NextDestination.FEED,
                NextDestination.WATCHED,
                NextDestination.BOARDS,
                NextDestination.SEARCH,
                NextDestination.SAVED,
                NextDestination.SETTINGS,
            ).inOrder()
    }

    @Test
    fun `window class switches at the rail breakpoint`() {
        assertThat(NavWindow.of(839.dp)).isEqualTo(NavWindow.COMPACT)
        assertThat(NavWindow.of(840.dp)).isEqualTo(NavWindow.EXPANDED)
    }
}
