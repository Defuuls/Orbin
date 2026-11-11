package com.orbin.app.navigation

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.compose.composable
import androidx.navigation.createGraph
import androidx.navigation.toRoute
import com.google.common.truth.Truth.assertThat
import com.orbin.uinext.NextDestination
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TabNavigationStateTest {
    @Test
    fun switchingTabsRestoresSearchStateWithoutRestoringCoveredSettings() {
        val nav = controller()
        nav.navigateToTab(Route.Search)
        nav.currentBackStackEntry!!.savedStateHandle["query"] = "quiet landscapes"
        nav.navigate(Route.Settings)

        nav.navigateToTab(Route.Watched)
        assertThat(nav.currentDestination!!.hasRoute(Route.Watched::class)).isTrue()
        nav.navigateToTab(Route.Search)

        assertThat(nav.currentDestination!!.hasRoute(Route.Search::class)).isTrue()
        assertThat(nav.currentBackStackEntry!!.savedStateHandle.get<String>("query")).isEqualTo("quiet landscapes")
        nav.popBackStack()
        assertThat(nav.currentDestination!!.hasRoute(Route.NextFeed::class)).isTrue()
    }

    @Test
    fun settingsCanOpenBoardsAsItsPrimaryTabAndReselectingDoesNotDuplicateIt() {
        val nav = controller()
        nav.navigateToTab(Route.Saved)
        nav.navigate(Route.Settings)
        nav.navigateToTab(Route.BoardGallery)
        nav.navigateToTab(Route.BoardGallery)
        assertThat(nav.currentDestination!!.hasRoute(Route.BoardGallery::class)).isTrue()
        nav.popBackStack()
        assertThat(nav.currentDestination!!.hasRoute(Route.NextFeed::class)).isTrue()
    }

    @Test
    fun drawerSwitchingFromAThreadReturnsToItsSelectedRootWithoutRetainingTheThread() {
        val nav = controller()
        nav.navigateToTab(Route.BoardGallery)
        nav.navigate(Route.Thread("fourchan", "g", 42L, "Topic"))

        nav.openDestination(NextDestination.SEARCH)
        assertThat(nav.currentDestination!!.hasRoute(Route.Search::class)).isTrue()
        nav.popBackStack()
        assertThat(nav.currentDestination!!.hasRoute(Route.NextFeed::class)).isTrue()
    }

    @Test
    fun drawerSettingsAndDownloadsUseHierarchicalBack() {
        val nav = controller()
        nav.openDestination(NextDestination.WATCHED)
        nav.openDestination(NextDestination.SETTINGS)
        nav.popBackStack()
        assertThat(nav.currentDestination!!.hasRoute(Route.Watched::class)).isTrue()
        nav.openDestination(NextDestination.DOWNLOADS)
        assertThat(nav.currentDestination!!.hasRoute(Route.Downloads::class)).isTrue()
        nav.popBackStack()
        assertThat(nav.currentDestination!!.hasRoute(Route.Watched::class)).isTrue()
    }

    @Test
    fun choosingTheCurrentCatalogBoardPreservesItsSavedStateAndBackStackEntry() {
        val nav = controller()
        nav.navigateToTab(Route.BoardGallery)
        nav.navigate(Route.Board("fourchan", "g", "Technology"))
        val entry = nav.currentBackStackEntry!!
        entry.savedStateHandle["catalogSort"] = "IMAGE_COUNT"
        entry.savedStateHandle["scrollIndex"] = 12

        nav.selectCatalogBoard(Route.Board("fourchan", "g", "Technology"))

        assertThat(nav.currentBackStackEntry).isSameInstanceAs(entry)
        assertThat(entry.savedStateHandle.get<String>("catalogSort")).isEqualTo("IMAGE_COUNT")
        assertThat(entry.savedStateHandle.get<Int>("scrollIndex")).isEqualTo(12)
    }

    @Test
    fun choosingAnotherSiteWithTheSameBoardPathReplacesTheCatalog() {
        val nav = controller()
        nav.navigateToTab(Route.BoardGallery)
        nav.navigate(Route.Board("fourchan", "g", "Technology"))
        nav.selectCatalogBoard(Route.Board("bbwchan", "g", "Discussion"))

        assertThat(nav.currentBackStackEntry!!.toRoute<Route.Board>().provider).isEqualTo("bbwchan")
        nav.popBackStack()
        assertThat(nav.currentDestination!!.hasRoute(Route.BoardGallery::class)).isTrue()
    }

    private fun controller(): NavHostController {
        val owner =
            object : LifecycleOwner {
                override val lifecycle = LifecycleRegistry(this).apply { currentState = Lifecycle.State.RESUMED }
            }
        return NavHostController(RuntimeEnvironment.getApplication()).apply {
            setLifecycleOwner(owner)
            setViewModelStore(ViewModelStore())
            navigatorProvider.addNavigator(ComposeNavigator())
            graph =
                createGraph(startDestination = Route.NextFeed) {
                    composable<Route.NextFeed> {}
                    composable<Route.BoardGallery> {}
                    composable<Route.Watched> {}
                    composable<Route.Search> {}
                    composable<Route.Saved> {}
                    composable<Route.Settings> {}
                    composable<Route.Downloads> {}
                    composable<Route.Thread> {}
                    composable<Route.Board> {}
                }
        }
    }
}
