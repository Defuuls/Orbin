package com.orbin.uinext

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
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

/** Deterministic shared iOS-mode captures at the reference width, split view, and tablet width. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w390dp-h844dp-mdpi")
class TikTokAdaptiveScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun portraitHome() = capture("tiktok_home_390", 390.dp, 844.dp) { HomeFixture() }

    @Test
    @Config(qualifiers = "w248dp-h600dp-mdpi")
    fun narrowHomeAtMaximumText() =
        capture("tiktok_home_narrow_max_text", 248.dp, 600.dp, fontScale = 2f) { HomeFixture() }

    @Test
    @Config(qualifiers = "w960dp-h720dp-mdpi")
    fun wideHome() = capture("tiktok_home_wide", 960.dp, 720.dp) { HomeFixture() }

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
private fun HomeFixture() {
    FeedScreen(
        rows =
            listOf(
                FeedRow(
                    subject = "A quiet place to pause",
                    board = "/p/",
                    activity = "8m",
                    replies = 128,
                    media = 42,
                    id = "fourchan/p/482917",
                    site = "4chan",
                    mediaLabel = "PNG",
                    mediaAspectRatio = 9f / 16f,
                    excerpt = "Original local artwork. Share a view that made your day.",
                ),
                FeedRow(
                    subject = "Weekly discussion",
                    board = "/gen/",
                    activity = "5m",
                    replies = 24,
                    media = 0,
                    hasPreview = false,
                    id = "bbwchan/gen/230145",
                    site = "BBWChan",
                    excerpt = "A fictional discussion for shared layout testing.",
                ),
            ),
        hideRailOnScroll = false,
        sourceLabels = listOf("All sites", "4chan", "BBWChan"),
        thumbnail = { _, modifier -> PortraitFixture(modifier) },
        onOpenRow = {},
        onOpenBoards = {},
        onOpenSearch = {},
        onOpenSaved = {},
        onOpenWatched = {},
        onSettings = {},
        onToggleWatch = {},
        onToggleSaved = {},
        onShareRow = {},
    )
}

/** Original geometry, constrained like portrait media; no network or real user media. */
@Composable
private fun PortraitFixture(modifier: Modifier) {
    BoxWithConstraints(modifier.background(Color.Black), contentAlignment = Alignment.Center) {
        val portrait =
            if (maxWidth / maxHeight > 9f / 16f) {
                Modifier.fillMaxHeight().aspectRatio(9f / 16f)
            } else {
                Modifier.fillMaxWidth().aspectRatio(9f / 16f)
            }
        Canvas(portrait) {
            drawRect(Color(0xFF42647A))
            drawCircle(
                Color(0xFFE3B58D),
                radius = size.width * 0.18f,
                center =
                    Offset(
                        size.width * 0.65f,
                        size.height * 0.26f,
                    ),
            )
            val ridge =
                Path().apply {
                    moveTo(0f, size.height * 0.65f)
                    lineTo(size.width * 0.25f, size.height * 0.35f)
                    lineTo(size.width * 0.56f, size.height * 0.62f)
                    lineTo(size.width * 0.8f, size.height * 0.45f)
                    lineTo(size.width, size.height * 0.7f)
                    lineTo(size.width, size.height)
                    lineTo(0f, size.height)
                    close()
                }
            drawPath(ridge, Color(0xFF21444A))
            drawRect(
                Color(0xFF7C9C9C),
                topLeft = Offset(0f, size.height * 0.75f),
                size = Size(size.width, size.height * 0.25f),
            )
        }
    }
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
