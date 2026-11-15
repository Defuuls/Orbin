package com.orbin.ios

import com.orbin.core.model.Board
import com.orbin.core.model.BoardId
import com.orbin.core.model.ProviderId
import com.orbin.core.model.ThreadId
import com.orbin.core.model.ThreadKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SourceReaderRouteTest {
    private val thread = Route.ThreadPage(ThreadKey(ProviderId("site"), BoardId("g"), ThreadId(42)))

    @Test
    fun everyCollectionAndSearchCanRetainItsSourceBesideAnyThread() {
        for (source in listOf(Route.Saved, Route.Watched, Route.History, Route.Search)) {
            assertEquals(SourceReaderRoute(source), sourceReaderRoute(listOf(Route.Feed, source)))
            assertEquals(SourceReaderRoute(source, thread), sourceReaderRoute(listOf(Route.Feed, source, thread)))
        }
    }

    @Test
    fun unrelatedEarlierSourcesAndOtherDetailPagesNeverBecomeTheThreadSource() {
        assertNull(sourceReaderRoute(emptyList()))
        assertNull(sourceReaderRoute(listOf(Route.Search, Route.Settings, thread)))
        assertNull(
            sourceReaderRoute(listOf(Route.Saved, thread, Route.ThreadPage(thread.key.copy(thread = ThreadId(43))))),
        )
        assertNull(sourceReaderRoute(listOf(Route.History, thread, Route.Media(thread.key, 0))))
        val catalog = Route.Catalog(SiteBoard(ProviderId("site"), "Site", Board(BoardId("g"), "Technology")))
        assertNull(sourceReaderRoute(listOf(catalog, thread)))
    }
}
