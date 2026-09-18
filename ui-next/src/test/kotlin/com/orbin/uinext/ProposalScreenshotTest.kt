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
        capture("next_feed") {
            FeedScreen(
                rows = feedRows(),
                subtitle = SAMPLE_SUBTITLE,
                onOpenBoards = {},
                onOpenMedia = {},
                onSettings = {},
            )
        }

    @Test
    fun feedDark() =
        capture("next_feed_dark", dark = true) {
            FeedScreen(
                rows = feedRows(),
                subtitle = SAMPLE_SUBTITLE,
                onOpenBoards = {},
                onOpenMedia = {},
                onSettings = {},
            )
        }

    @Test
    fun feedAmoled() =
        capture("next_feed_amoled", dark = true, amoled = true) {
            FeedScreen(
                rows = feedRows(),
                subtitle = SAMPLE_SUBTITLE,
                onOpenBoards = {},
                onOpenMedia = {},
                onSettings = {},
            )
        }

    @Test
    fun feedLargeText() =
        capture("next_feed_large_text", fontScale = XL_FONT_SCALE) {
            FeedScreen(
                rows = feedRows(),
                subtitle = SAMPLE_SUBTITLE,
                onOpenBoards = {},
                onOpenMedia = {},
                onSettings = {},
            )
        }

    @Test
    fun feedMaxText() =
        capture("next_feed_max_text", fontScale = MAX_FONT_SCALE) {
            FeedScreen(
                rows = feedRows(),
                subtitle = SAMPLE_SUBTITLE,
                onOpenBoards = {},
                onOpenMedia = {},
                onSettings = {},
            )
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
                layout = FeedLayout.GRID,
                onOpenBoards = {},
                onOpenMedia = {},
                onSettings = {},
            )
        }

    @Test
    fun feedImages() =
        capture("next_feed_images") {
            FeedScreen(
                rows = feedRows(),
                subtitle = SAMPLE_SUBTITLE,
                layout = FeedLayout.IMAGES,
                onOpenBoards = {},
                onOpenMedia = {},
                onSettings = {},
            )
        }

    @Test
    fun feedFiltered() =
        capture("next_feed_filtered") {
            FeedScreen(
                rows = feedRows().filter { it.board == "/g/" },
                subtitle = "5 threads across 7 boards",
                filter = "thinkpad",
                onOpenBoards = {},
                onOpenMedia = {},
                onSettings = {},
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
    fun boardsBrowse() =
        capture("next_boards") {
            BoardsScreen(
                boards =
                    listOf(
                        BoardTile("g", "/g/", "Technology"),
                        BoardTile("ck", "/ck/", "Food & Cooking"),
                        BoardTile("p", "/p/", "Photography"),
                        BoardTile("lit", "/lit/", "Literature"),
                        BoardTile("a", "/a/", "Anime & Manga", nsfw = false),
                        BoardTile("wg", "/wg/", "Wallpapers/General"),
                    ),
                onOpenFeed = {},
                onOpenMedia = {},
                onOpenSettings = {},
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

    /** The list at rest: every row closed. */
    @Test
    fun settings() =
        capture("next_settings") {
            SettingsScreen(
                groups = settingsGroups(),
                onOpenFeed = {},
                onOpenBoards = {},
                onOpenMedia = {},
                onOpenSearch = {},
                onOpenDownloads = {},
                onOpenCommands = {},
            )
        }

    @Test
    fun settingsMaxText() =
        capture("next_settings_max_text", fontScale = MAX_FONT_SCALE) {
            SettingsScreen(
                groups = settingsGroups(),
                onOpenFeed = {},
                onOpenBoards = {},
                onOpenMedia = {},
                onOpenSearch = {},
                onOpenDownloads = {},
                onOpenCommands = {},
            )
        }

    /**
     * A row being changed where it stands, which is the only way this list edits anything: the
     * choice opens its options underneath rather than opening a screen over them.
     */
    @Test
    fun settingsEditing() =
        capture("next_settings_editing") {
            SettingsScreen(
                groups = settingsGroups(),
                expandedId = "themeMode",
                onOpenFeed = {},
                onOpenBoards = {},
                onOpenMedia = {},
                onOpenSearch = {},
                onOpenDownloads = {},
                onOpenCommands = {},
            )
        }

    @Test
    fun settingsEditingMaxText() =
        capture("next_settings_editing_max_text", fontScale = MAX_FONT_SCALE) {
            SettingsScreen(
                groups = settingsGroups(),
                expandedId = "themeMode",
                onOpenFeed = {},
                onOpenBoards = {},
                onOpenMedia = {},
                onOpenSearch = {},
                onOpenDownloads = {},
                onOpenCommands = {},
            )
        }

    /** Keeps [SettingTextEditor] covered; see [textEditorGroup]. */
    @Test
    fun settingsTextEditor() =
        capture("next_settings_text_editor") {
            SettingsScreen(
                groups = textEditorGroup(),
                expandedId = "userAgent",
                onOpenFeed = {},
                onOpenBoards = {},
                onOpenMedia = {},
                onOpenSearch = {},
                onOpenDownloads = {},
                onOpenCommands = {},
            )
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
                onOpenFeed = {},
                onOpenBoards = {},
                onOpenSettings = {},
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
                onOpenFeed = {},
                onOpenBoards = {},
                onOpenSettings = {},
            )
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

    /**
     * The settings surface as `:feature:settings` actually builds it.
     *
     * Ids, labels, headings and order mirror `buildSettings`, so these captures show the list
     * people really get rather than a list this file invented. `SettingsIndexTest` holds the
     * registry and its search index together; nothing holds this fixture to either, so it is kept
     * short on purpose — enough of each heading to show the shape, and every row real.
     */
    private fun settingsGroups() =
        listOf(
            "General" to
                listOf(
                    SettingItem("personalized", "Personalized feed", "On", SettingKind.TOGGLE),
                    SettingItem("hideNsfw", "Hide NSFW boards", "Off", SettingKind.TOGGLE),
                    SettingItem(
                        id = "feedSort",
                        label = "Feed sort",
                        value = "Board",
                        kind = SettingKind.CHOICE,
                        options = listOf("Board", "Active", "Replies", "Images", "Created", "A-Z"),
                        selected = 0,
                    ),
                    SettingItem("fullScreenFeed", "Full-screen browsing", "Off", SettingKind.TOGGLE),
                ),
            "Display & Media" to
                listOf(
                    SettingItem(
                        id = "themeMode",
                        label = "Theme",
                        value = "System",
                        kind = SettingKind.CHOICE,
                        options = listOf("System", "Light", "Dark"),
                        selected = 0,
                    ),
                    SettingItem("amoled", "True black", "Off", SettingKind.TOGGLE),
                    SettingItem(
                        id = "fontScale",
                        label = "Text size",
                        value = "Default",
                        kind = SettingKind.CHOICE,
                        options = listOf("Small", "Default", "Large", "XL"),
                        selected = 1,
                    ),
                    SettingItem("autoplay", "Autoplay videos", "Off", SettingKind.TOGGLE),
                    SettingItem("mute", "Mute by default", "On", SettingKind.TOGGLE),
                ),
            "Privacy & Data" to
                listOf(
                    SettingItem("biometric", "App lock", "Off", SettingKind.TOGGLE),
                    SettingItem("recentSearches", "Save recent searches", "Off", SettingKind.TOGGLE),
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
        )

    /**
     * A text row, which the settings list no longer has one of.
     *
     * [SettingTextEditor] is still live in `:ui-next` and the registry still carries the commit
     * plumbing, so the editor keeps a capture of its own rather than riding on a settings fixture
     * that would have to misrepresent the list to provide it. Deliberately not [settingsGroups]:
     * this is a component under test, not the app's settings surface.
     */
    private fun textEditorGroup() =
        listOf(
            "Text row" to
                listOf(
                    SettingItem(
                        id = "userAgent",
                        label = "Custom user agent",
                        value = "Default",
                        kind = SettingKind.TEXT,
                        text = "Orbin/1.0",
                        hint = "Sent with every request. Leave empty to use Orbin's default.",
                    ),
                ),
        )

    private fun mediaCells() =
        List(15) { index ->
            MediaCell(id = "cell-$index", board = listOf("/g/", "/ck/", "/p/", "/lit/")[index % 4])
        }
}
