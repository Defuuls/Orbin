package com.orbin.uinext

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SetupWizardTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun multiStepSetupNavigatesFromSplashToBoardsAndPersistsChoices() {
        var selectedTheme = SetupThemeOption.SYSTEM
        var hideNsfw = true
        var coverViolent = true
        var started = false

        compose.setContent {
            NextTheme {
                OnboardingScreen(
                    boards = OnboardingBoards.Ready(listOf(OnboardingBoard("g", "Technology", "Tech", false))),
                    following = setOf("g"),
                    sites = listOf("4chan"),
                    selectedSite = 0,
                    onSelectSite = {},
                    onFollow = { _, _ -> },
                    onRetry = {},
                    onStart = { started = true },
                    setupWizard =
                        OnboardingSetupConfig(
                            theme = selectedTheme,
                            hideNsfwBoards = hideNsfw,
                            coverViolentMedia = coverViolent,
                            onThemeChange = { selectedTheme = it },
                            onHideNsfwChange = { hideNsfw = it },
                            onCoverViolentMediaChange = { coverViolent = it },
                        ),
                )
            }
        }

        // Step 1: Full-screen welcome splash
        compose.onNodeWithText("Get started").performClick()

        // Step 2: Theme selection
        compose.onNodeWithText("Choose your theme").assertExists()
        compose.onNodeWithText("AMOLED — True Black").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(SetupThemeOption.AMOLED, selectedTheme) }
        compose.onNodeWithText("Continue").performClick()

        // Step 3: Privacy & content
        compose.onNodeWithText("Privacy & content").assertExists()
        compose.onNodeWithText("Hide adult (NSFW) boards").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(false, hideNsfw) }
        compose.onNodeWithText("Continue").performClick()

        // Step 4: Follow boards
        compose.onNodeWithText("/g/").assertExists()
        compose.onNodeWithText("Start browsing").performClick()
        compose.runOnIdle { assertTrue(started) }
    }

    @Test
    fun updateSetupScreenAllowsThemeMigrationAndCompletesOnDone() {
        val seen = MutableStateFlow<String?>("205-Old")
        var migratedTheme = SetupThemeOption.SYSTEM
        var savedVersion: String? = null
        val notes = ReleaseNotes("206-Sea-Beet", listOf(ReleaseNoteSection("Added", listOf("Midnight Electric"))))

        compose.setContent {
            NextTheme {
                WhatsNewOnUpdate(
                    lastSeen = seen,
                    onSeen = { savedVersion = it },
                    visible = true,
                    notes = notes,
                    themeConfig =
                        UpdateThemeConfig(
                            theme = migratedTheme,
                            onThemeChange = { migratedTheme = it },
                        ),
                )
            }
        }

        compose.onNodeWithText("Midnight Electric theme").assertExists()
        compose.onNodeWithText("Dark").performClick()
        compose.runOnIdle { assertEquals(SetupThemeOption.DARK, migratedTheme) }
        compose.onNodeWithText("Done").performClick()
        compose.runOnIdle { assertEquals("206-Sea-Beet", savedVersion) }
    }
}
