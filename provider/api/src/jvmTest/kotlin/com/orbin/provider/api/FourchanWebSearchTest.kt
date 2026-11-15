package com.orbin.provider.api

import com.orbin.core.model.AppSettings
import com.orbin.core.model.Board
import com.orbin.core.model.BoardId
import com.orbin.core.model.ProviderId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FourchanWebSearchTest {
    private val provider =
        ProviderMetadata(ProviderId("fourchan"), "4chan", "https://boards.4chan.org", engine = EngineKind.VICHAN)

    private fun page(
        number: Long,
        next: Int? = null,
    ) =
        """{"recognized":true,"hits":[{"url":"https://boards.4chan.org/g/thread/$number#p$number","subject":"Test","comment":"Text","name":"Anonymous"}],"next":$next}"""

    @Test
    fun realSearchUrlsEncodeTheWholeQueryAndRequireTheRegisteredCanonicalSite() {
        assertEquals(
            "https://find.4chan.org/?q=cats%20%26%20dogs%20%23%20%E7%8C%AB&o=25",
            fourchanWebSearchUrl("cats & dogs # 猫", 25),
        )
        assertNull(fourchanWebSearchUrl("  "))
        assertEquals(provider, fourchanWebSearchProvider(listOf(provider)))
        assertNull(fourchanWebSearchProvider(listOf(provider.copy(baseUrl = "https://example.test"))))
        assertFalse(isFourchanWebSearchUrl("https://find.4chan.org.evil.test/?q=x"))
        assertFalse(isFourchanWebSearchUrl("http://find.4chan.org/?q=x"))
        assertFalse(isFourchanWebSearchUrl("https://user@find.4chan.org/?q=x"))
    }

    @Test
    fun unknownResponsesAreErrorsWhileVerificationIsExplicit() {
        assertNull(parseFourchanWebSearchPage("{}", provider))
        val session = FourchanSearchSession(provider) { true }
        session.search("cats")
        val url = session.state.value.requestUrl
        session.page(url, """{"challenge":true}""")
        assertTrue(session.state.value.verifying)
        assertFalse(session.state.value.loading)
        session.started(url)
        assertFalse(session.state.value.verifying)
        session.page(url, "{}")
        assertTrue(session.state.value.error != null)
    }

    @Test
    fun paginationDeduplicatesHitsAndLateDifferentQueriesDoNotReplaceResults() {
        val session = FourchanSearchSession(provider) { true }
        session.search("one")
        val old = session.state.value.requestUrl
        session.search("two")
        val current = session.state.value.requestUrl
        session.page(old, page(10))
        assertTrue(
            session.state.value.hits
                .isEmpty(),
        )
        session.page(current, page(20, 25))
        session.more()
        assertEquals(25, session.state.value.offset)
        session.page(session.state.value.requestUrl, page(20))
        assertEquals(1, session.state.value.hits.size)
        val before = session.state.value.requestId
        session.retry()
        assertTrue(session.state.value.requestId > before)
    }

    @Test
    fun unknownAndHiddenBoardsCannotAppearAndVisibleTextStillUsesPermanentPolicy() {
        val hit = parseFourchanWebSearchPage(page(20), provider)!!.hits.single()
        val safe = Board(BoardId("g"), "Technology")
        assertFalse(hit.isVisibleInOrbin(AppSettings.Default, emptyList()))
        assertTrue(hit.isVisibleInOrbin(AppSettings.Default, listOf(safe)))
        assertFalse(
            hit.isVisibleInOrbin(AppSettings.Default.copy(hideNsfwBoards = true), listOf(safe.copy(isNsfw = true))),
        )
        assertFalse(hit.copy(comment = "gore video").isVisibleInOrbin(AppSettings.Default, listOf(safe)))
        assertFalse(hit.isVisibleInOrbin(AppSettings.Default.copy(hiddenThreadPatterns = listOf("Te.t")), listOf(safe)))
    }

    @Test
    fun reopeningLoadedSearchDoesNotRequestAgainButAnExplicitRepeatRejectsOldCallbacks() {
        val session = FourchanSearchSession(provider) { true }
        session.submit("cats")
        val request = session.state.value
        session.page(request.requestUrl, page(10, 25), request.requestId)
        session.search("cats")
        assertEquals(request.requestId, session.state.value.requestId)
        assertEquals(
            10L,
            session.state.value.hits
                .single()
                .link.key.thread.value,
        )
        session.submit("cats")
        val repeated = session.state.value.requestId
        assertTrue(repeated > request.requestId)
        session.page(request.requestUrl, page(999), request.requestId)
        session.fail(request.requestUrl, "stale failure", request.requestId)
        assertEquals(
            10L,
            session.state.value.hits
                .single()
                .link.key.thread.value,
        )
        assertNull(session.state.value.error)
        session.page(request.requestUrl, page(20, 25), repeated)
        assertEquals(
            20L,
            session.state.value.hits
                .single()
                .link.key.thread.value,
        )
        session.more()
        val pageRequest = session.state.value.requestId
        session.more()
        assertEquals("Double taps cannot duplicate an in-flight page", pageRequest, session.state.value.requestId)
    }
}
