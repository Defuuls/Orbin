package com.orbin.uinext

import androidx.collection.MutableIntObjectMap
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.layout.LayoutInfo
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import com.orbin.uinext.components.FeedRow
import com.orbin.uinext.components.LocalClipShapedRowFills
import com.orbin.uinext.components.NextTheme
import com.orbin.uinext.components.clipShapedRowFills
import com.orbin.uinext.screens.BoardScreen
import com.orbin.uinext.screens.BoardScreenActions
import com.orbin.uinext.screens.BoardScreenState
import com.orbin.uinext.screens.CatalogMode
import com.orbin.uinext.screens.Post
import com.orbin.uinext.screens.ThreadScreen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Offscreen graphics layers held by modifiers on the scrolling reader screens.
 *
 * Each layer is an extra offscreen draw per frame, which Core Animation on iPhone and iPad pays for
 * while a list scrolls. On iOS, label pills, media labels, file badges and placeholder tiles paint
 * a shaped background instead of clipping ([LocalClipShapedRowFills] false), so the iOS budgets
 * hold the counts measured after that change (PR 915). Android keeps main's clipped drawing, so
 * its counts must stay exactly main's: grid 108, card 53, list 74 and thread 18. Raise an iOS
 * budget only with a reason a row needs a new layer.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w440dp-h956dp-xxhdpi")
class RowGraphicsLayerBudgetTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val rows =
        List(40) { i ->
            FeedRow(
                subject = "Thread $i",
                board = "/g/",
                activity = "${i}m",
                replies = 10 + i,
                media = 3,
                id = "g/$i",
                unread = if (i % 3 == 0) 2 else 0,
                mediaLabel = "WEBM",
            )
        }

    @Test
    fun `iOS catalog grid stays within its layer budget`() {
        show(clipRowFills = false) { Catalog(CatalogMode.GRID) }
        assertLayersAtMost("iOS catalog grid", budget = 38)
    }

    @Test
    fun `iOS catalog cards stay within their layer budget`() {
        show(clipRowFills = false) { Catalog(CatalogMode.CARD) }
        assertLayersAtMost("iOS catalog cards", budget = 27)
    }

    @Test
    fun `iOS catalog list stays within its layer budget`() {
        show(clipRowFills = false) { Catalog(CatalogMode.LIST) }
        assertLayersAtMost("iOS catalog list", budget = 46)
    }

    @Test
    fun `iOS thread stays within its layer budget`() {
        show(clipRowFills = false) { Thread() }
        assertLayersAtMost("iOS thread", budget = 9)
    }

    @Test
    fun `Android catalog grid keeps main's clipped drawing`() {
        show(clipRowFills = true) { Catalog(CatalogMode.GRID) }
        assertLayersExactly("Android catalog grid", main = 108)
    }

    @Test
    fun `Android catalog cards keep main's clipped drawing`() {
        show(clipRowFills = true) { Catalog(CatalogMode.CARD) }
        assertLayersExactly("Android catalog cards", main = 53)
    }

    @Test
    fun `Android catalog list keeps main's clipped drawing`() {
        show(clipRowFills = true) { Catalog(CatalogMode.LIST) }
        assertLayersExactly("Android catalog list", main = 74)
    }

    @Test
    fun `Android thread keeps main's clipped drawing`() {
        show(clipRowFills = true) { Thread() }
        assertLayersExactly("Android thread", main = 18)
    }

    @Test
    fun `Android keeps clipped row fills by default`() {
        assertEquals(true, clipShapedRowFills)
    }

    private fun show(
        clipRowFills: Boolean,
        content: @Composable () -> Unit,
    ) {
        composeRule.setContent {
            CompositionLocalProvider(LocalClipShapedRowFills provides clipRowFills) {
                NextTheme { content() }
            }
        }
    }

    @Composable
    private fun Thread() {
        ThreadScreen(
            subject = "A thread",
            board = "/g/",
            posts =
                List(30) { i ->
                    Post(number = "No. $i", time = "2m", body = "Post $i", hasMedia = true, mediaCount = 3)
                },
            media = { _, modifier -> Box(modifier) },
        )
    }

    @Composable
    private fun Catalog(mode: CatalogMode) {
        BoardScreen(
            state =
                BoardScreenState(
                    board = "/g/",
                    description = "Technology",
                    itemCount = rows.size,
                    rowAt = rows::getOrNull,
                    mode = mode,
                ),
            actions = BoardScreenActions(onOpenRow = {}),
            thumbnail = { _, modifier -> Box(modifier) },
        )
    }

    private fun assertLayersAtMost(
        screen: String,
        budget: Int,
    ) {
        composeRule.waitForIdle()
        val layers = placedModifierLayers()
        println("$screen: $layers modifier-held graphics layers (budget $budget)")
        assertTrue("$screen holds $layers modifier graphics layers; budget is $budget", layers <= budget)
    }

    private fun assertLayersExactly(
        screen: String,
        main: Int,
    ) {
        composeRule.waitForIdle()
        val layers = placedModifierLayers()
        println("$screen: $layers modifier-held graphics layers (main $main)")
        assertEquals("$screen must hold main's $main modifier graphics layers", main, layers)
    }

    /** Counts modifiers that own a graphics layer (their [androidx.compose.ui.layout.ModifierInfo.extra]) on placed nodes. */
    private fun placedModifierLayers(): Int {
        val root = composeRule.onRoot().fetchSemanticsNode().root ?: error("No compose owner")
        val nodes = root.javaClass.getMethod("getLayoutNodes").invoke(root) as MutableIntObjectMap<*>
        var layers = 0
        nodes.forEachValue { node ->
            val info = node as LayoutInfo
            if (info.isPlaced) layers += info.getModifierInfo().count { it.extra != null }
        }
        return layers
    }
}
