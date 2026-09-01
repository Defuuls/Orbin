package com.orbin.uinext

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Renders the proposed interface at real phone size so it can be judged before it is wired to
 * anything. These are screenshots of the actual composables, not mockups — whatever is wrong in
 * the picture is wrong in the code, and can be fixed there.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xhdpi")
class ProposalScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun feed() =
        capture("next_feed") { FeedScreen(rows = feedRows(), subtitle = SAMPLE_SUBTITLE, railDetail = "7 boards") }

    @Test
    fun feedDark() =
        capture("next_feed_dark", dark = true) {
            FeedScreen(rows = feedRows(), subtitle = SAMPLE_SUBTITLE, railDetail = "7 boards")
        }

    @Test
    fun feedAmoled() =
        capture("next_feed_amoled", dark = true, amoled = true) {
            FeedScreen(rows = feedRows(), subtitle = SAMPLE_SUBTITLE, railDetail = "7 boards")
        }

    @Test
    fun feedLargeText() =
        capture("next_feed_large_text", fontScale = XL_FONT_SCALE) {
            FeedScreen(rows = feedRows(), subtitle = SAMPLE_SUBTITLE, railDetail = "7 boards")
        }

    @Test
    fun feedMaxText() =
        capture("next_feed_max_text", fontScale = MAX_FONT_SCALE) {
            FeedScreen(rows = feedRows(), subtitle = SAMPLE_SUBTITLE, railDetail = "7 boards")
        }

    @Test
    fun feedInShell() =
        capture("next_feed_in_shell") {
            FeedScreen(rows = feedRows(), subtitle = SAMPLE_SUBTITLE, showRail = false)
        }

    @Test
    fun feedGrid() =
        capture("next_feed_grid") {
            FeedScreen(
                rows = feedRows(),
                subtitle = SAMPLE_SUBTITLE,
                railDetail = "7 boards",
                layout = FeedLayout.GRID,
            )
        }

    @Test
    fun feedImages() =
        capture("next_feed_images") {
            FeedScreen(
                rows = feedRows(),
                subtitle = SAMPLE_SUBTITLE,
                railDetail = "7 boards",
                layout = FeedLayout.IMAGES,
            )
        }

    @Test
    fun feedFiltered() =
        capture("next_feed_filtered") {
            FeedScreen(
                rows = feedRows().filter { it.board == "/g/" },
                subtitle = "5 threads across 7 boards",
                railDetail = "7 boards",
                filter = "thinkpad",
            )
        }

    @Test
    fun board() =
        capture("next_board") {
            val rows = boardRows()
            BoardScreen(
                board = "/g/",
                description = "Technology",
                itemCount = rows.size,
                rowAt = { index -> rows.getOrNull(index) },
            )
        }

    @Test
    fun thread() =
        capture("next_thread") {
            ThreadScreen(
                subject = "Anyone else running a home server on ARM?",
                board = "/g/",
                posts = posts(),
                watching = true,
            )
        }

    @Test
    fun threadDark() =
        capture("next_thread_dark", dark = true) {
            ThreadScreen(
                subject = "Anyone else running a home server on ARM?",
                board = "/g/",
                posts = posts(),
                watching = true,
            )
        }

    @Test
    fun threadMaxText() =
        capture("next_thread_max_text", fontScale = MAX_FONT_SCALE) {
            ThreadScreen(
                subject = "Anyone else running a home server on ARM?",
                board = "/g/",
                posts = posts(),
                watching = true,
            )
        }

    @Test
    fun threadCollapsed() =
        capture("next_thread_collapsed") {
            ThreadScreen(
                subject = "Anyone else running a home server on ARM?",
                board = "/g/",
                posts = posts(),
                watching = true,
                collapsed = setOf("No.4471028", "No.4471040"),
            )
        }

    @Test
    fun threadFiles() =
        capture("next_thread_files") {
            ThreadScreen(
                subject = "Anyone else running a home server on ARM?",
                board = "/g/",
                posts = posts(),
                watching = true,
                layout = ThreadLayout.FILES,
                files = mediaCells().take(7),
            )
        }

    @Test
    fun command() = capture("next_command") { commandContent() }

    @Test
    fun commandMaxText() = capture("next_command_max_text", fontScale = MAX_FONT_SCALE) { commandContent() }

    @Test
    fun settings() =
        capture("next_settings") {
            SettingsScreen(groups = settingsGroups(), expandedId = "colorTheme")
        }

    @Test
    fun settingsMaxText() =
        capture("next_settings_max_text", fontScale = MAX_FONT_SCALE) {
            SettingsScreen(groups = settingsGroups(), expandedId = "colorTheme")
        }

    @Test
    fun settingsEditing() =
        capture("next_settings_editing") {
            SettingsScreen(groups = settingsGroups(), expandedId = "hiddenTags")
        }

    @Test
    fun settingsEditingMaxText() =
        capture("next_settings_editing_max_text", fontScale = MAX_FONT_SCALE) {
            SettingsScreen(groups = settingsGroups(), expandedId = "hiddenTags")
        }

    @Test
    fun mediaWall() =
        capture("next_media") {
            MediaWallScreen(
                scanned = 42,
                total = 70,
                failed = 3,
                scanning = true,
                cells = mediaCells(),
            )
        }

    @Test
    fun mediaWallMaxText() =
        capture("next_media_max_text", fontScale = MAX_FONT_SCALE) {
            MediaWallScreen(
                scanned = 42,
                total = 70,
                failed = 3,
                scanning = true,
                cells = mediaCells(),
            )
        }

    @Test
    fun boardPicker() =
        capture("next_boards") {
            BoardPickerScreen(boards = boardChoices(), subtitle = "3 of 7 in your feed")
        }

    @Test
    fun boardPickerMaxText() =
        capture("next_boards_max_text", fontScale = MAX_FONT_SCALE) {
            BoardPickerScreen(boards = boardChoices(), subtitle = "3 of 7 in your feed")
        }

    @androidx.compose.runtime.Composable
    private fun commandContent() {
        FeedScreen(rows = feedRows(), subtitle = SAMPLE_SUBTITLE, showRail = false)
        CommandSheet(
            query = "auto",
            results =
                listOf(
                    Command("Autoplay videos", "setting", "Media · currently on"),
                    Command("Autoplay videos in feed", "setting", "Media · currently off"),
                    Command("Auto-rotate video", "setting", "Media · currently on"),
                    Command("/aco/", "board", "Adult Cartoons · subscribed"),
                    Command("Automotive threads", "search", "12 saved results"),
                    Command("Automotive detailing general", "thread", "/o/ · 84 replies · open"),
                ),
        )
    }

    private fun capture(
        name: String,
        dark: Boolean = false,
        amoled: Boolean = false,
        fontScale: Float = 1f,
        content: @androidx.compose.runtime.Composable () -> Unit,
    ) {
        composeRule.setContent {
            NextTheme(darkTheme = dark, amoled = amoled, fontScale = fontScale) {
                Surface(modifier = Modifier.size(411.dp, 891.dp)) {
                    Box { content() }
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }

    private fun boardChoices() =
        listOf(
            BoardChoice("g", "Technology", subscribed = true),
            BoardChoice("a", "Anime & Manga", subscribed = true),
            BoardChoice("ck", "Food & Cooking", subscribed = true),
            BoardChoice("lit", "Literature"),
            BoardChoice("p", "Photography"),
            BoardChoice("sci", "Science & Math"),
            BoardChoice("wsg", "Worksafe Gifs, with a title long enough that it has to truncate"),
        )

    private companion object {
        const val XL_FONT_SCALE = 1.2f
        const val MAX_FONT_SCALE = 2.0f
        const val SAMPLE_SUBTITLE = "8 threads across 7 boards"
    }

    private fun feedRows() =
        listOf(
            FeedRow("Anyone else running a home server on ARM?", "/g/", "4m", 218, 31),
            FeedRow("Weekly desktop thread", "/g/", "12m", 94, 88),
            FeedRow("What did you cook this week", "/ck/", "31m", 47, 22, read = true),
            FeedRow("Film photography general — grain edition", "/p/", "1h", 156, 140),
            FeedRow("Old ThinkPads that still earn their keep", "/g/", "2h", 63, 19),
            FeedRow("Reading list for winter", "/lit/", "3h", 28, 4, hasPreview = false, read = true),
            FeedRow("Post your desk setup", "/g/", "4h", 311, 205),
            FeedRow("Cheap mechanical keyboards worth having", "/g/", "6h", 88, 17),
        )

    private fun boardRows() =
        listOf(
            FeedRow("Anyone else running a home server on ARM?", "/g/", "4m", 218, 31),
            FeedRow("Weekly desktop thread", "/g/", "12m", 94, 88),
            FeedRow("Old ThinkPads that still earn their keep", "/g/", "31m", 63, 19),
            FeedRow("Post your desk setup", "/g/", "1h", 311, 205, read = true),
            FeedRow("Cheap mechanical keyboards worth having", "/g/", "2h", 88, 17),
            FeedRow("Self-hosting what you actually use", "/g/", "3h", 41, 6, hasPreview = false),
        )

    private fun posts() =
        listOf(
            Post(
                "No.4471028",
                "14:02",
                "Been running a small NAS on an ARM board for about eight months now. Idle draw is " +
                    "under four watts and it has not fallen over once.",
                hasMedia = true,
                replies = 6,
            ),
            Post("No.4471033", "14:09", "Which board? The cheap ones tend to have terrible SATA.", depth = 1),
            Post(
                "No.4471040",
                "14:15",
                "Not the one you are thinking of. It has a proper PCIe lane rather than USB behind " +
                    "a bridge, which is the whole difference.",
                depth = 2,
                replies = 2,
            ),
            Post("No.4471051", "14:22", "", depth = 1, spoiler = true),
            Post(
                "No.4471066",
                "14:40",
                "Power figures over a month, if anyone wants them.",
                hasMedia = true,
            ),
        )

    private fun settingsGroups() =
        listOf(
            "Content & feed" to
                listOf(
                    SettingItem("filter", "Built-in content filter", "Always on", SettingKind.INFO),
                    SettingItem(
                        id = "hiddenTags",
                        label = "Hidden tags",
                        value = "3",
                        kind = SettingKind.TEXT,
                        text = "spoilers, politics, meta",
                        hint = "Comma-separated. Threads matching any of them are hidden.",
                    ),
                    SettingItem("hideNsfw", "Hide NSFW boards", "Off", SettingKind.TOGGLE),
                    SettingItem(
                        id = "threadLimit",
                        label = "Threads per board",
                        value = "All",
                        kind = SettingKind.CHOICE,
                        options = listOf("6", "12", "18", "All"),
                        selected = 3,
                    ),
                ),
            "Storage & backup" to
                listOf(
                    SettingItem(
                        id = "importBackup",
                        label = "Import data",
                        value = "Restore",
                        kind = SettingKind.ACTION,
                        hint =
                            "Merges a backup into what is already here, so a restore cannot " +
                                "destroy an existing setup.",
                    ),
                ),
            "Appearance" to
                listOf(
                    SettingItem(
                        id = "colorTheme",
                        label = "Color theme",
                        value = "Yotsuba",
                        kind = SettingKind.CHOICE,
                        options = listOf("Orbin", "Yotsuba", "Tomorrow", "Photon"),
                        selected = 1,
                    ),
                    SettingItem("dynamicColor", "Dynamic color", "Off", SettingKind.TOGGLE),
                    SettingItem("amoled", "AMOLED black", "On", SettingKind.TOGGLE),
                    SettingItem(
                        id = "fontScale",
                        label = "Font size",
                        value = "Default",
                        kind = SettingKind.CHOICE,
                        options = listOf("Small", "Default", "Large", "XL"),
                        selected = 1,
                    ),
                ),
            "Media & playback" to
                listOf(
                    SettingItem("autoplay", "Autoplay videos", "On", SettingKind.TOGGLE),
                    SettingItem("autoplayFeed", "Autoplay videos in feed", "Off", SettingKind.TOGGLE),
                    SettingItem("mute", "Mute by default", "On", SettingKind.TOGGLE),
                ),
        )

    private fun mediaCells() =
        List(15) { index ->
            MediaCell(id = "cell-$index", board = listOf("/g/", "/ck/", "/p/", "/lit/")[index % 4])
        }
}
