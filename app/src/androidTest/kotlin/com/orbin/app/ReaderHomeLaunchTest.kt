package com.orbin.app

import android.Manifest
import android.os.Build
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.core.view.WindowInsetsControllerCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import com.orbin.core.model.AppThemeMode
import com.orbin.core.model.BoardId
import com.orbin.core.model.CatalogMode
import com.orbin.core.model.ProviderId
import com.orbin.domain.repository.BoardPreferencesRepository
import com.orbin.domain.repository.SettingsRepository
import com.orbin.uinext.ReaderAppBarTags
import com.orbin.uinext.ReaderFeedTags
import com.orbin.uinext.TikTokFeedTags
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import javax.inject.Inject

private const val HOME_READY_TIMEOUT_MS = 20_000L

/** Starts the actual activity with persisted follows, rather than stopping at first-run setup. */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class ReaderHomeLaunchTest {
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val notificationPermission: TestRule =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            TestRule { base, _ -> base }
        }

    @get:Rule(order = 2)
    val composeRule = createEmptyComposeRule()

    @Inject
    lateinit var settings: SettingsRepository

    @Inject
    lateinit var boards: BoardPreferencesRepository

    @Test
    fun initializedLaunchShowsTheReaderHomeInsteadOfAnImmersiveVideoPager() {
        hiltRule.inject()
        runBlocking {
            boards.setSubscribedBoard(ProviderId("fourchan"), BoardId("g"), true)
            settings.setOnboardingCompleted(true)
            settings.setThemeMode(AppThemeMode.LIGHT)
            settings.setCatalogMode(CatalogMode.GRID)
        }

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            // Reader Home exists while providers load or fail as well. The test must not
            // depend on a live site's catalog or wait for videos to become available.
            composeRule.waitUntil(HOME_READY_TIMEOUT_MS) {
                composeRule.onAllNodesWithTag(ReaderFeedTags.GRID).fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithTag(ReaderFeedTags.ROOT).assertIsDisplayed()
            composeRule.onNodeWithTag(ReaderFeedTags.GRID).assertIsDisplayed()
            composeRule.onNodeWithTag(ReaderAppBarTags.BAR).assertIsDisplayed()
            composeRule.onNodeWithContentDescription("Menu").assertExists()
            composeRule.onNodeWithTag(TikTokFeedTags.PAGER).assertDoesNotExist()
            composeRule.onNodeWithTag(TikTokFeedTags.ACTIONS).assertDoesNotExist()

            scenario.onActivity { activity ->
                assertTrue(
                    "Light reader Home needs dark navigation icons over its light ground",
                    WindowInsetsControllerCompat(activity.window, activity.window.decorView)
                        .isAppearanceLightNavigationBars,
                )
            }
        }
    }
}
