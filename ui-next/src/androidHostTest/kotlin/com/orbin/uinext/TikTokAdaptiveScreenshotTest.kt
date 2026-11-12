package com.orbin.uinext

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Non-Home settings and setup captures; ReaderHomeScreenshotTest owns the new Home matrix. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w390dp-h844dp-mdpi")
class TikTokAdaptiveScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun darkSettings() = capture("tiktok_settings_dark", 390.dp, 844.dp, dark = true) { SettingsFixture() }

    @Test
    fun fourchanOnboarding() =
        capture("tiktok_onboarding_fourchan", 390.dp, 844.dp) { OnboardingFixture(selectedSite = 0) }

    @Test
    fun bbwchanOnboarding() =
        capture("tiktok_onboarding_bbwchan", 390.dp, 844.dp) { OnboardingFixture(selectedSite = 1) }

    @Test
    @Config(qualifiers = "w960dp-h720dp-mdpi")
    fun wideSettings() = capture("tiktok_settings_wide", 960.dp, 720.dp) { SettingsFixture() }

    @Test
    @Config(qualifiers = "w248dp-h600dp-mdpi")
    fun narrowPlaybackAtMaximumText() =
        capture("tiktok_playback_narrow_max_text", 248.dp, 600.dp, fontScale = 2f) {
            SettingsFixture(playback = true)
        }

    private fun capture(
        name: String,
        width: Dp,
        height: Dp,
        dark: Boolean = false,
        fontScale: Float = 1f,
        content: @Composable () -> Unit,
    ) {
        composeRule.setContent {
            NextTheme(darkTheme = dark, fontScale = fontScale, platform = NextPlatform.IOS) {
                Surface(Modifier.size(width, height), color = next.background, content = content)
            }
        }
        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage("src/androidHostTest/screenshots/$name.png")
    }
}

@Composable
private fun OnboardingFixture(selectedSite: Int) {
    val boards =
        if (selectedSite == 0) {
            listOf(
                OnboardingBoard("g", "Technology", "Local technology discussions.", false),
                OnboardingBoard("p", "Photography", "Share a favorite view.", false),
                OnboardingBoard("wg", "Wallpapers", "Original desktop artwork.", false),
            )
        } else {
            listOf(
                OnboardingBoard("gen", "General Discussion", "A fictional weekly discussion.", false),
                OnboardingBoard("bbwai", "BBW AI", "A local artwork fixture.", true),
                OnboardingBoard("bbwdraw", "BBW Drawn", "Original drawings and sketches.", true),
            )
        }
    OnboardingScreen(
        boards = OnboardingBoards.Ready(boards),
        following = emptySet(),
        sites = listOf("4chan", "BBWChan"),
        selectedSite = selectedSite,
        onSelectSite = {},
        onFollow = { _, _ -> },
        onRetry = {},
        onStart = {},
    )
}

@Composable
private fun SettingsFixture(playback: Boolean = false) {
    SettingsScreen(
        groups =
            listOf(
                "Appearance" to
                    listOf(
                        SettingItem(
                            "themeMode",
                            "Theme",
                            "System",
                            SettingKind.CHOICE,
                            listOf("System", "Light", "Dark"),
                        ),
                    ),
                "Privacy" to
                    listOf(
                        SettingItem("screenPrivacy", "Screen privacy", "On", checked = true),
                        SettingItem("saveBrowsingHistory", "Save browsing history", "On", checked = true),
                    ),
                "Playback" to
                    listOf(
                        SettingItem("startMuted", "Start muted", "On", checked = true),
                        SettingItem("loopVideos", "Loop videos", "On", checked = true),
                        SettingItem("autoplay", "Autoplay", "Off", SettingKind.CHOICE, listOf("Off", "On")),
                        SettingItem(
                            "preloadNextClip",
                            "Preload next clip",
                            "On",
                            SettingKind.CHOICE,
                            listOf("Off", "On"),
                            selected = 1,
                        ),
                    ),
                "Storage" to
                    listOf(SettingItem("exportBackup", "Export data", "Save", SettingKind.ACTION)),
                "About" to
                    listOf(SettingItem("version", "Version", "Deterministic preview", SettingKind.INFO)),
            ),
        enabledSiteCount = 2,
        expandedId = "startMuted".takeIf { playback },
        onBack = {},
        onNavigate = {},
        onActivate = {},
        onSelectOption = { _, _ -> },
    )
}
