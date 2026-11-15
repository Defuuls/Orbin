package com.orbin.uinext

import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import com.orbin.uinext.components.LibrarySection
import com.orbin.uinext.components.NavSurface
import com.orbin.uinext.components.NavWindow
import com.orbin.uinext.components.NextDestination
import com.orbin.uinext.components.ReaderNavigationSpec
import com.orbin.uinext.components.primary
import org.junit.Test

/** Rule: the bar and rail are derived from the one spec; Library groups Watched, Saved and Downloads. */
class NavigationSpecTest {
    private val primary =
        listOf(NextDestination.BOARDS, NextDestination.FEED, NextDestination.LIBRARY, NextDestination.SETTINGS)

    @Test
    fun `the spec defines the four primary destinations once`() {
        assertThat(ReaderNavigationSpec.entries.map { it.destination }).containsExactlyElementsIn(primary).inOrder()
    }

    @Test
    fun `bar and rail show the same primary destinations in the same order`() {
        assertThat(
            ReaderNavigationSpec.destinations(NavSurface.BAR, NavWindow.COMPACT),
        ).containsExactlyElementsIn(primary).inOrder()
        assertThat(
            ReaderNavigationSpec.destinations(NavSurface.RAIL, NavWindow.EXPANDED),
        ).containsExactlyElementsIn(primary).inOrder()
        assertThat(ReaderNavigationSpec.destinations(NavSurface.BAR, NavWindow.EXPANDED)).isEmpty()
        assertThat(ReaderNavigationSpec.destinations(NavSurface.RAIL, NavWindow.COMPACT)).isEmpty()
    }

    @Test
    fun `every Library section selects the Library tab`() {
        LibrarySection.entries.forEach { section ->
            assertThat(section.destination.primary).isEqualTo(NextDestination.LIBRARY)
            assertThat(LibrarySection.of(section.destination)).isEqualTo(section)
        }
        assertThat(LibrarySection.DEFAULT).isEqualTo(LibrarySection.WATCHED)
        assertThat(NextDestination.SEARCH.primary).isNull()
        primary.forEach { assertThat(it.primary).isEqualTo(it) }
    }

    @Test
    fun `window class switches at the rail breakpoint`() {
        assertThat(NavWindow.of(839.dp)).isEqualTo(NavWindow.COMPACT)
        assertThat(NavWindow.of(840.dp)).isEqualTo(NavWindow.EXPANDED)
    }
}
