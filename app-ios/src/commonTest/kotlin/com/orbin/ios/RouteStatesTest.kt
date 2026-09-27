package com.orbin.ios

import com.orbin.core.model.BoardId
import com.orbin.core.model.ProviderId
import com.orbin.core.model.ThreadId
import com.orbin.core.model.ThreadKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RouteStatesTest {
    private fun thread(number: Long) = Route.ThreadPage(ThreadKey(ProviderId("p"), BoardId("g"), ThreadId(number)))

    @Test
    fun aThreadLeftForTheFeedKeepsItsPlace() {
        val states = RouteStates(limit = 2)

        states.visit(listOf(Route.Feed, thread(1)))

        assertTrue(states.visit(listOf(Route.Feed)).isEmpty())
    }

    @Test
    fun theLeastRecentlyVisitedThreadIsForgottenPastTheLimit() {
        val states = RouteStates(limit = 2)

        states.visit(listOf(Route.Feed, thread(1)))
        states.visit(listOf(Route.Feed, thread(2)))
        states.visit(listOf(Route.Feed, thread(1)))

        assertEquals(listOf(thread(2).stateKey), states.visit(listOf(Route.Feed, thread(3))))
    }

    @Test
    fun routesOnTheBackStackAreNeverForgotten() {
        val states = RouteStates(limit = 1)

        val evicted = states.visit(listOf(Route.Feed, thread(1), thread(2)))

        assertTrue(evicted.isEmpty())
    }

    @Test
    fun tabsAreNeverForgotten() {
        val states = RouteStates(limit = 1)

        states.visit(listOf(Route.Feed))
        states.visit(listOf(Route.Boards))

        assertTrue(states.visit(listOf(Route.Feed, thread(1))).none { it == Route.Feed.stateKey })
    }
}
