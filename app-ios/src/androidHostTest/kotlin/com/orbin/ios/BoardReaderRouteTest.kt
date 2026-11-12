package com.orbin.ios

import com.orbin.core.model.Board
import com.orbin.core.model.BoardId
import com.orbin.core.model.ProviderId
import com.orbin.core.model.ThreadId
import com.orbin.core.model.ThreadKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

class BoardReaderRouteTest {
    private val catalog = Route.Catalog(SiteBoard(ProviderId("site"), "Site", Board(BoardId("g"), "Technology")))

    @Test
    fun aCatalogWithoutAThreadUsesTheReaderWithAnEmptyDetail() {
        val reader = boardReaderRoute(listOf(Route.Boards, catalog))
        assertEquals(BoardReaderRoute(catalog), reader)
        assertNull(reader?.thread)
    }

    @Test
    fun openingSwitchingAndClosingThreadsRetainsTheCatalogStateKey() {
        val empty = boardReaderRoute(listOf(Route.Boards, catalog))!!
        val first = thread(7)
        val selected = boardReaderRoute(listOf(Route.Boards, catalog, first))!!
        val second = boardReaderRoute(listOf(Route.Boards, catalog, thread(8)))!!
        val closed = boardReaderRoute(listOf(Route.Boards, catalog))!!

        assertSame(catalog, empty.catalog)
        assertSame(empty.catalog, selected.catalog)
        assertSame(selected.catalog, second.catalog)
        assertSame(second.catalog, closed.catalog)
        assertEquals(catalog.stateKey, selected.catalog.stateKey)
        assertEquals(catalog.stateKey, closed.catalog.stateKey)
        assertEquals(first.stateKey, selected.thread?.stateKey)
    }

    @Test
    fun onlyTheImmediatelyPrecedingCatalogCanShareAThread() {
        val thread = thread(7)
        assertNull(boardReaderRoute(listOf(Route.Feed, thread)))
        assertNull(boardReaderRoute(listOf(Route.Boards, catalog, Route.Settings, thread)))
        assertNull(boardReaderRoute(listOf(Route.Boards, catalog, thread(7, provider = "other"))))
        assertNull(boardReaderRoute(listOf(Route.Boards, catalog, thread(7, board = "v"))))
    }

    @Test
    fun mediaAndOtherDestinationsKeepTheirOwnRouteState() {
        assertNull(boardReaderRoute(emptyList()))
        assertNull(boardReaderRoute(listOf(Route.Boards)))
        assertNull(boardReaderRoute(listOf(Route.Boards, catalog, Route.BoardMedia(catalog.board))))
        assertNull(boardReaderRoute(listOf(Route.Boards, catalog, thread(7), Route.Media(thread(7).key, 0))))
        assertNull(boardReaderRoute(listOf(Route.Feed, Route.ThreadVideos(thread(7).key))))
    }

    @Test
    fun closingTheDetailRetainsCatalogStateWithinTheExistingBoundedPruner() {
        val states = RouteStates(limit = 1)
        val selected = thread(7)
        states.visit(listOf(Route.Boards, catalog, selected))
        assertEquals(listOf(selected.stateKey), states.visit(listOf(Route.Boards, catalog)))
        assertEquals(emptyList(), states.visit(listOf(Route.Boards, catalog)))
        assertEquals(listOf(catalog.stateKey), states.visit(listOf(Route.Feed, thread(8))))
    }

    private fun thread(
        number: Long,
        provider: String = "site",
        board: String = "g",
    ) = Route.ThreadPage(ThreadKey(ProviderId(provider), BoardId(board), ThreadId(number)))
}
