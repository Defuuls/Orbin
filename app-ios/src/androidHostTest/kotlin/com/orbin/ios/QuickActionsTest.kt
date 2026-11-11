package com.orbin.ios

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class QuickActionsTest {
    @Test
    fun eachQuickActionOpensItsTabAsAnAndroidShortcutDoes() {
        assertEquals(Route.Feed, QuickActions.tabFor(QuickActions.FEED))
        assertEquals(Route.Downloads, QuickActions.tabFor(QuickActions.DOWNLOADS))
        assertEquals(Route.Boards, QuickActions.tabFor(QuickActions.BOARDS))
    }

    @Test
    fun anotherAppsActionIsNotTakenAndLeavesNothingPending() {
        QuickActions.opened()

        assertFalse(QuickActions.perform("com.example.other"))
        assertNull(QuickActions.pending.value)

        assertTrue(QuickActions.perform(QuickActions.DOWNLOADS))
        assertEquals(Route.Downloads, QuickActions.pending.value)
        QuickActions.opened()
        assertNull(QuickActions.pending.value)
    }
}
