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
import com.google.common.truth.Truth.assertThat
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
                }
        }
    }
}
