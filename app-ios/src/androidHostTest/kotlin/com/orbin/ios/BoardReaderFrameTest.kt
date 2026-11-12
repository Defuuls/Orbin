package com.orbin.ios

import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.orbin.core.model.Board
import com.orbin.core.model.BoardId
import com.orbin.core.model.ProviderId
import com.orbin.core.model.ThreadId
import com.orbin.core.model.ThreadKey
import com.orbin.uinext.LocalReaderOpenDrawer
import com.orbin.uinext.NextDestination
import com.orbin.uinext.NextPlatform
import com.orbin.uinext.NextTheme
import com.orbin.uinext.ReaderAppBar
import com.orbin.uinext.ReaderDrawerTags
import com.orbin.uinext.ReaderNavigation
import com.orbin.uinext.ReaderScaffold
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Exercises the production iOS reader frame on the Android host without network or media services. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w1280dp-h800dp-xhdpi")
class BoardReaderFrameTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val catalog = Route.Catalog(SiteBoard(ProviderId("site"), "Site", Board(BoardId("g"), "Technology")))
    private val selected = Route.ThreadPage(ThreadKey(catalog.board.provider, catalog.board.board.id, ThreadId(7)))
    private val observed = ObservedContent()

    @Test
    fun catalogScrollAndCompositionSurviveBothResizeDirections() {
        var width by mutableStateOf(700.dp)
        showFrame(width = { width })
        composeRule.onNodeWithTag(CATALOG).performScrollToIndex(20)
        val initialState = observed.catalogState
        val initialIdentity = observed.catalogIdentity

        composeRule.runOnIdle { width = 960.dp }
        composeRule.onNodeWithText("Catalog wide=true selected=none").assertIsDisplayed()
        composeRule.onNodeWithText("Select a thread").assertIsDisplayed()
        composeRule.runOnIdle {
            assertEquals(20, observed.catalogState.firstVisibleItemIndex)
            assertSame(initialState, observed.catalogState)
            assertSame(initialIdentity, observed.catalogIdentity)
            assertEquals(1, observed.catalogMounts)
            assertEquals(0, observed.catalogDisposals)
            width = 700.dp
        }

        composeRule.onNodeWithText("Catalog wide=false selected=none").assertIsDisplayed()
        composeRule.runOnIdle {
            assertEquals(20, observed.catalogState.firstVisibleItemIndex)
            assertSame(initialState, observed.catalogState)
            assertSame(initialIdentity, observed.catalogIdentity)
            assertEquals(0, observed.catalogDisposals)
        }
    }

    @Test
    fun threadFilesAndCollapseStateSurviveBothResizeDirections() {
        var width by mutableStateOf(700.dp)
        showFrame(width = { width }, route = { BoardReaderRoute(catalog, selected) })
        composeRule.onNodeWithTag(FILES).performClick()
        composeRule.onNodeWithTag(COLLAPSE).performClick()
        val initialIdentity = observed.threadIdentity
        composeRule.onNodeWithTag(CATALOG).assertDoesNotExist()

        composeRule.runOnIdle { width = 960.dp }
        composeRule.onNodeWithText("Catalog wide=true selected=7").assertIsDisplayed()
        assertThreadReadingState()
        composeRule.onNodeWithText("Select a thread").assertDoesNotExist()
        composeRule.runOnIdle {
            assertSame(initialIdentity, observed.threadIdentity)
            assertEquals(1, observed.threadMounts)
            assertEquals(0, observed.threadDisposals)
            width = 700.dp
        }

        composeRule.onNodeWithTag(CATALOG).assertDoesNotExist()
        assertThreadReadingState()
        composeRule.runOnIdle {
            assertSame(initialIdentity, observed.threadIdentity)
            assertEquals(0, observed.threadDisposals)
        }
    }

    @Test
    fun inactiveNarrowCatalogUnmountsWhileSavedScrollAndThreadStateRestoreOnReturn() {
        var width by mutableStateOf(960.dp)
        var route by mutableStateOf(BoardReaderRoute(catalog))
        showFrame(width = { width }, route = { route })
        composeRule.onNodeWithTag(CATALOG).performScrollToIndex(20)
        composeRule.onNodeWithText("Select a thread").assertIsDisplayed()

        composeRule.runOnIdle { route = BoardReaderRoute(catalog, selected) }
        composeRule.onNodeWithTag(FILES).performClick()
        composeRule.onNodeWithTag(COLLAPSE).performClick()
        composeRule.runOnIdle { width = 700.dp }
        composeRule.onNodeWithTag(CATALOG).assertDoesNotExist()
        composeRule.runOnIdle { assertEquals(1, observed.catalogDisposals) }
        assertThreadReadingState()

        composeRule.runOnIdle { width = 960.dp }
        composeRule.onNodeWithText("Catalog wide=true selected=7").assertIsDisplayed()
        composeRule.runOnIdle { assertEquals(20, observed.catalogState.firstVisibleItemIndex) }
        assertThreadReadingState()

        composeRule.runOnIdle { route = BoardReaderRoute(catalog) }
        composeRule.onNodeWithText("Catalog wide=true selected=none").assertIsDisplayed()
        composeRule.onNodeWithText("Select a thread").assertIsDisplayed()
        composeRule.onNodeWithTag(FILES).assertDoesNotExist()
        composeRule.runOnIdle {
            assertEquals(20, observed.catalogState.firstVisibleItemIndex)
            route = BoardReaderRoute(catalog, selected)
        }
        assertThreadReadingState()
    }

    @Test
    fun theWideDrawerUsesTheWindowAndBlocksTheThreadUntilBackClosesIt() {
        var threadTaps = 0
        var threadBacks = 0
        composeRule.setContent {
            NextTheme(platform = NextPlatform.IOS) {
                val stateHolder = rememberSaveableStateHolder()
                Box(Modifier.size(960.dp, 700.dp)) {
                    BoardReaderFrame(
                        route = BoardReaderRoute(catalog, selected),
                        stateHolder = stateHolder,
                        onDestination = {},
                        catalogContent = { _, _, _ ->
                            // The production catalog owns its bar while the outer split owns its drawer.
                            ReaderScaffold(
                                title = "Catalog",
                                destination = NextDestination.BOARDS,
                                onDestination = {},
                                drawAppBar = false,
                            ) {
                                ReaderAppBar(
                                    title = "Catalog",
                                    navigation = ReaderNavigation.MENU,
                                    onNavigation = LocalReaderOpenDrawer.current,
                                )
                            }
                        },
                        threadContent = {
                            BackHandler { threadBacks++ }
                            Box(Modifier.fillMaxSize().testTag(THREAD_INPUT).clickable { threadTaps++ })
                        },
                    )
                }
            }
        }
        val detailBounds = composeRule.onNodeWithTag(THREAD_INPUT).getUnclippedBoundsInRoot()
        val detailCenter =
            with(composeRule.density) {
                Offset(
                    ((detailBounds.left + detailBounds.right) / 2).toPx(),
                    ((detailBounds.top + detailBounds.bottom) / 2).toPx(),
                )
            }
        composeRule.onNodeWithContentDescription("Menu").performClick()
        composeRule.waitUntil(timeoutMillis = 1_000) {
            val bounds = composeRule.onNodeWithTag(ReaderDrawerTags.DRAWER).getUnclippedBoundsInRoot()
            kotlin.math.abs(bounds.left.value) <= 0.5f && kotlin.math.abs(bounds.right.value - 336f) <= 0.5f
        }
        val drawerBounds = composeRule.onNodeWithTag(ReaderDrawerTags.DRAWER).getUnclippedBoundsInRoot()
        assertEquals(336f, (drawerBounds.right - drawerBounds.left).value, 0.5f)

        // A pointer tap on the detail hits the full-window modal scrim, not the thread underneath.
        composeRule.onRoot().performTouchInput { click(detailCenter) }
        composeRule.onNodeWithTag(ReaderDrawerTags.DRAWER).assertIsNotDisplayed()
        composeRule.runOnIdle {
            assertEquals(0, threadTaps)
            assertEquals(0, threadBacks)
        }

        composeRule.onNodeWithContentDescription("Menu").performClick()
        composeRule.waitUntil(timeoutMillis = 1_000) {
            val bounds = composeRule.onNodeWithTag(ReaderDrawerTags.DRAWER).getUnclippedBoundsInRoot()
            kotlin.math.abs(bounds.left.value) <= 0.5f && kotlin.math.abs(bounds.right.value - 336f) <= 0.5f
        }
        composeRule.runOnUiThread { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
        composeRule.onNodeWithTag(ReaderDrawerTags.DRAWER).assertIsNotDisplayed()
        composeRule.runOnIdle { assertEquals(0, threadBacks) }
        composeRule.runOnUiThread { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
        composeRule.runOnIdle { assertEquals(1, threadBacks) }
        composeRule.onRoot().performTouchInput { click(detailCenter) }
        composeRule.runOnIdle { assertEquals(1, threadTaps) }
    }

    @Test
    fun renderedPaneVisibilityTracksSelectionResizeAndDisposal() {
        var width by mutableStateOf(960.dp)
        var route by mutableStateOf(BoardReaderRoute(catalog))
        var visible by mutableStateOf(true)
        val reports = mutableListOf<Triple<Boolean, Boolean, Boolean>>()
        composeRule.setContent {
            NextTheme {
                val stateHolder = rememberSaveableStateHolder()
                if (visible) {
                    Box(Modifier.size(width, 700.dp)) {
                        BoardReaderFrame(
                            route = route,
                            stateHolder = stateHolder,
                            catalogContent = { _, _, _ -> Box(Modifier.fillMaxSize()) },
                            threadContent = { Box(Modifier.fillMaxSize()) },
                            onVisiblePanesChanged = { wide, catalogShowing, threadShowing ->
                                reports.add(Triple(wide, catalogShowing, threadShowing))
                            },
                        )
                    }
                }
            }
        }
        composeRule.runOnIdle {
            assertEquals(Triple(true, true, false), reports.last())
            route = BoardReaderRoute(catalog, selected)
        }
        composeRule.runOnIdle {
            assertEquals(Triple(true, true, true), reports.last())
            width = 700.dp
        }
        composeRule.runOnIdle {
            assertEquals(Triple(false, false, true), reports.last())
            route = BoardReaderRoute(catalog)
        }
        composeRule.runOnIdle {
            assertEquals(Triple(false, true, false), reports.last())
            visible = false
        }
        composeRule.runOnIdle { assertEquals(Triple(false, false, false), reports.last()) }
    }

    private fun assertThreadReadingState() {
        composeRule.onNodeWithText("Files").assertIsDisplayed()
        composeRule.onNodeWithText("Collapsed").assertIsDisplayed()
    }

    private fun showFrame(
        width: () -> Dp,
        route: () -> BoardReaderRoute = { BoardReaderRoute(catalog) },
    ) {
        composeRule.setContent {
            NextTheme {
                val stateHolder = rememberSaveableStateHolder()
                Box(Modifier.size(width(), 700.dp)) {
                    BoardReaderFrame(
                        route = route(),
                        stateHolder = stateHolder,
                        catalogContent = { _, wide, thread -> CatalogContent(wide, thread) },
                        threadContent = { ThreadContent() },
                    )
                }
            }
        }
    }

    @Composable
    private fun CatalogContent(
        wide: Boolean,
        thread: ThreadKey?,
    ) {
        val listState = rememberLazyListState()
        val identity = remember { Any() }
        SideEffect {
            observed.catalogState = listState
            observed.catalogIdentity = identity
        }
        DisposableEffect(Unit) {
            observed.catalogMounts++
            onDispose { observed.catalogDisposals++ }
        }
        Column(Modifier.fillMaxSize()) {
            Text("Catalog wide=$wide selected=${thread?.thread?.value ?: "none"}")
            LazyColumn(Modifier.testTag(CATALOG).weight(1f), state = listState) {
                items(100) { index -> Text("Row $index", Modifier.height(48.dp)) }
            }
        }
    }

    @Composable
    private fun ThreadContent() {
        var files by rememberSaveable { mutableStateOf(false) }
        var collapsed by rememberSaveable { mutableStateOf(false) }
        val identity = remember { Any() }
        SideEffect { observed.threadIdentity = identity }
        DisposableEffect(Unit) {
            observed.threadMounts++
            onDispose { observed.threadDisposals++ }
        }
        Column(Modifier.fillMaxSize()) {
            Text(
                if (files) "Files" else "Posts",
                Modifier.testTag(FILES).clickable { files = !files },
            )
            Text(
                if (collapsed) "Collapsed" else "Expanded",
                Modifier.testTag(COLLAPSE).clickable { collapsed = !collapsed },
            )
        }
    }

    private class ObservedContent {
        lateinit var catalogState: LazyListState
        lateinit var catalogIdentity: Any
        lateinit var threadIdentity: Any
        var catalogMounts = 0
        var catalogDisposals = 0
        var threadMounts = 0
        var threadDisposals = 0
    }

    private companion object {
        const val CATALOG = "test-reader-catalog"
        const val FILES = "test-reader-files"
        const val COLLAPSE = "test-reader-collapse"
        const val THREAD_INPUT = "test-reader-thread-input"
    }
}
