package com.orbin.app

import androidx.compose.foundation.clickable
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.orbin.app.navigation.Route
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OnboardingNavigationTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun successfulCompletionIsNotRedirectedWhileTheParentStillRequiresSetup() {
        lateinit var controller: NavHostController
        var acknowledgements = 0
        compose.setContent {
            val navigation = rememberNavController()
            controller = navigation
            OnboardingNavigation(
                navController = navigation,
                startWithOnboarding = true,
                pendingRoute = null,
                onPendingRouteHandled = {},
                onOnboardingCompleted = { acknowledgements++ },
            ) { acknowledgeCompletion ->
                TestDestinations(navigation, acknowledgeCompletion)
            }
        }

        compose.onNodeWithText("Finish persisted setup").performClick()
        compose.waitForIdle()

        compose.onNodeWithText("Initialized Home").assertExists()
        compose.runOnIdle {
            assertEquals(1, acknowledgements)
            assertTrue(controller.currentDestination!!.hasRoute(Route.NextFeed::class))
        }
    }

    @Test
    fun queuedRouteWaitsForTheSuccessfulCallbackThenOpensWithAStaleParentFlag() {
        var handledRoutes = 0
        compose.setContent {
            val navigation = rememberNavController()
            var pendingRoute by remember { mutableStateOf<Route?>(Route.Search) }
            OnboardingNavigation(
                navController = navigation,
                startWithOnboarding = true,
                pendingRoute = pendingRoute,
                onPendingRouteHandled = {
                    pendingRoute = null
                    handledRoutes++
                },
                onOnboardingCompleted = {},
            ) { acknowledgeCompletion ->
                TestDestinations(navigation, acknowledgeCompletion)
            }
        }
        compose.onNodeWithText("Finish persisted setup").assertExists()
        compose.runOnIdle { assertEquals(0, handledRoutes) }

        compose.onNodeWithText("Finish persisted setup").performClick()
        compose.waitForIdle()

        compose.onNodeWithText("Queued Search").assertExists()
        compose.runOnIdle { assertEquals(1, handledRoutes) }
    }
}

@androidx.compose.runtime.Composable
private fun TestDestinations(
    navigation: NavHostController,
    acknowledgeCompletion: () -> Unit,
) {
    NavHost(navigation, startDestination = Route.Onboarding) {
        composable<Route.Onboarding> {
            Text(
                "Finish persisted setup",
                Modifier.clickable {
                    // Matches the production NavHost: acknowledge successful persistence first,
                    // then leave the wizard while its parent's collected settings may be stale.
                    acknowledgeCompletion()
                    navigation.navigate(Route.NextFeed) {
                        popUpTo(navigation.graph.id) { inclusive = true }
                    }
                },
            )
        }
        composable<Route.NextFeed> { Text("Initialized Home") }
        composable<Route.Search> { Text("Queued Search") }
    }
}
