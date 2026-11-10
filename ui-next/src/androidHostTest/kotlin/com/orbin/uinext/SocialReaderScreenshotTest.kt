package com.orbin.uinext

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.orbin.uinext.tokens.NextType
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Shared screens in iOS mode, using fictional local fixtures and deterministic scenic artwork. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xhdpi")
class SocialReaderScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun homeAcrossBothSites() =
        capture("social_ios_home") {
            FeedScreen(
                rows =
                    listOf(
                        FeedRow(
                            subject = "Quiet landscapes",
                            board = "/wg/",
                            activity = "8m",
                            replies = 128,
                            media = 42,
                            id = "fourchan/wg/482917",
                            site = "4chan",
                            mediaLabel = "JPG",
                            mediaAspectRatio = 2.6f,
                            excerpt = "A place to share a favorite view.",
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
                            excerpt = "What's everyone working on this week?",
                        ),
                    ),
                columns = 1,
                hideRailOnScroll = false,
                sourceLabels = listOf("All sites", "4chan", "BBWChan"),
                selectedSource = 0,
                onSelectSource = {},
                thumbnail = { _, modifier -> ScenicPlaceholder(modifier) },
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

    @Test
    fun boardDirectory() =
        capture("social_ios_boards") {
            BoardsScreen(
                boards =
                    listOf(
                        BoardTile("bbwchan/gen", "/gen/", "General Discussion", followed = true, site = "BBWChan"),
                        BoardTile("bbwchan/bbw", "/bbw/", "BBW Real", site = "BBWChan"),
                        BoardTile("bbwchan/bbwdraw", "/bbwdraw/", "BBW Drawn", site = "BBWChan"),
                        BoardTile("bbwchan/ee", "/ee/", "Everything Else", site = "BBWChan"),
                    ),
                headerContent = { PlatformSegments(listOf("4chan", "BBWChan"), 1, {}) },
                hideRailOnScroll = false,
                onOpenBoard = {},
                onFollowBoard = { _, _ -> },
                onOpenFeed = {},
                onOpenSearch = {},
                onOpenSaved = {},
                onOpenWatched = {},
                onOpenSettings = {},
            )
        }

    @Test
    fun settingsDirectory() =
        capture("social_ios_settings") {
            SettingsScreen(
                groups = settingsFixtures(),
                enabledSiteCount = 2,
                onBack = {},
                onNavigate = {},
            )
        }

    @Test
    fun boardPlaybackOverrides() =
        capture("social_ios_playback") {
            SettingsScreen(
                groups = settingsFixtures(),
                enabledSiteCount = 2,
                expandedId = "startMuted",
                onBack = {},
                onNavigate = {},
                onActivate = {},
                onSelectOption = { _, _ -> },
            )
        }

    @Test
    fun textLedThread() =
        capture("social_ios_thread") {
            ThreadScreen(
                subject = "Weekly discussion",
                siteName = "BBWChan",
                board = "/gen/",
                posts =
                    listOf(
                        Post(
                            number = "No. 230145",
                            time = "5m",
                            body = "What's everyone working on this week?\nShare a project or a small win.",
                            hasMedia = true,
                            replies = 2,
                        ),
                        Post("No. 230152", "2m", "Working on a new project."),
                        Post("No. 230149", "3m", ">>230145\nTaking the weekend off."),
                    ),
                media = { _, modifier -> ScenicPlaceholder(modifier.height(140.dp)) },
                body = { post ->
                    val text =
                        buildAnnotatedString {
                            post.body.lines().forEachIndexed { index, line ->
                                if (index > 0) append("\n")
                                if (line.startsWith(">>")) {
                                    withStyle(SpanStyle(color = Color(0xFF237A3B))) { append(line) }
                                } else {
                                    append(line)
                                }
                            }
                        }
                    Text(text, style = NextType.body, color = next.ink)
                },
                onBack = {},
                onWatch = {},
                onToggleSaved = {},
                onShare = {},
            )
        }

    @Test
    fun searchAcrossSites() =
        capture("social_ios_search") {
            NextScaffold(where = "Search", destination = NextDestination.SEARCH, onDestination = {}) { padding ->
                Box(Modifier.padding(bottom = padding.calculateBottomPadding())) {
                    SearchScreen(
                        query = "landscape",
                        onQueryChange = {},
                        onSearch = {},
                        state =
                            SearchState.Results(
                                listOf(
                                    SearchRow(
                                        "fourchan/wg/482917",
                                        "Quiet landscapes",
                                        "4chan · /wg/",
                                        "A place to share a favorite view.",
                                    ),
                                    SearchRow(
                                        "bbwchan/gen/230145",
                                        "Weekend landscapes",
                                        "BBWChan · /gen/",
                                        "What is everyone working on this week?",
                                    ),
                                ),
                            ),
                        onOpenRow = {},
                    )
                }
            }
        }

    @Test
    fun watchedThreads() =
        capture("social_ios_watched") {
            ThreadCollectionScreen(
                title = "Watched threads",
                rows =
                    listOf(
                        ThreadCollectionRow("fourchan/wg/482917", "Quiet landscapes", "4chan · /wg/"),
                        ThreadCollectionRow("bbwchan/gen/230145", "Weekly discussion", "BBWChan · /gen/"),
                    ),
                onOpen = {},
                onSettings = {},
                onDestination = {},
                selectedDestination = NextDestination.WATCHED,
            )
        }

    private fun capture(
        name: String,
        content: @Composable () -> Unit,
    ) {
        composeRule.setContent {
            NextTheme(darkTheme = false, platform = NextPlatform.IOS) {
                Surface(modifier = Modifier.size(411.dp, 891.dp), color = next.background, content = content)
            }
        }
        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage("src/androidHostTest/screenshots/$name.png")
    }

    private fun settingsFixtures() =
        listOf(
            "Appearance" to
                listOf(
                    SettingItem(
                        "themeMode",
                        "Theme",
                        "System",
                        SettingKind.CHOICE,
                        listOf("System", "Light", "Dark"),
                        0,
                    ),
                ),
            "Browsing" to
                listOf(
                    SettingItem("saveBrowsingHistory", "Save browsing history", "On", checked = true),
                    SettingItem("hideNsfw", "Hide NSFW boards", "Off", checked = false),
                ),
            "Playback" to
                listOf(
                    SettingItem(
                        "playbackScope",
                        "Scope",
                        "BBWChan · /gen/",
                        SettingKind.CHOICE,
                        options = listOf("App defaults", "BBWChan", "BBWChan · /gen/"),
                        selected = 2,
                        hint = "Changes apply to this board.",
                    ),
                    SettingItem(
                        "inheritPlayback",
                        "Use site settings",
                        "Off",
                        checked = false,
                        hint = "Board overrides are active.",
                    ),
                    SettingItem(
                        "autoplay",
                        "Autoplay",
                        "Off",
                        SettingKind.CHOICE,
                        options = listOf("Off", "On"),
                        selected = 0,
                        hint = "Choose when clips start automatically.",
                    ),
                    SettingItem("startMuted", "Start muted", "On", checked = true, hint = "Play clips without sound."),
                    SettingItem("loopVideos", "Loop videos", "On", checked = true),
                    SettingItem(
                        "preloadNextClip",
                        "Preload next clip",
                        "Off",
                        SettingKind.CHOICE,
                        options = listOf("Off", "On"),
                        selected = 0,
                    ),
                    SettingItem("resetPlayback", "Reset to site settings", "", SettingKind.ACTION),
                ),
            "Storage" to
                listOf(
                    SettingItem("clearImageCache", "Clear media cache", "Clear", SettingKind.ACTION),
                ),
            "About" to listOf(SettingItem("version", "Version", "Preview fixture", SettingKind.INFO)),
        )
}

/** A simple lake and mountain silhouette; no network, copyrighted media, or time-dependent state. */
@Composable
private fun ScenicPlaceholder(modifier: Modifier) {
    Canvas(modifier) {
        drawRect(Color(0xFFD8E9F0))
        val mountains =
            Path().apply {
                moveTo(0f, size.height * 0.64f)
                lineTo(size.width * 0.25f, size.height * 0.16f)
                lineTo(size.width * 0.47f, size.height * 0.59f)
                lineTo(size.width * 0.72f, size.height * 0.25f)
                lineTo(size.width, size.height * 0.64f)
                close()
            }
        drawPath(mountains, Color(0xFF879E9C))
        drawRect(
            Color(0xFFAACED5),
            topLeft =
                androidx.compose.ui.geometry
                    .Offset(0f, size.height * 0.64f),
            size =
                androidx.compose.ui.geometry
                    .Size(size.width, size.height * 0.36f),
        )
    }
}
