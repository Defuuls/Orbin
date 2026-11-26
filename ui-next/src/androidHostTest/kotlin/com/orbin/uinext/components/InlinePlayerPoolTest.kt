package com.orbin.uinext.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class InlinePlayerPoolTest {
    private val released = mutableListOf<Int>()
    private val resets = mutableListOf<Int>()
    private var next = 0
    private val pool =
        InlinePlayerPool(
            create = { next++ },
            reset = { resets += it },
            release = { released += it },
        )

    @Test
    fun aRecycledPlayerIsResetAndReused() {
        val first = pool.acquire()
        pool.recycle(first)
        assertEquals(listOf(first), resets)
        assertSame(first, pool.acquire())
        assertEquals(1, pool.created)
    }

    @Test
    fun onlyOneSpareIsKept() {
        val first = pool.acquire()
        val second = pool.acquire()
        pool.recycle(first)
        pool.recycle(second)
        assertEquals(listOf(second), released)
        assertEquals(1, pool.idleCount)
    }

    @Test
    fun aPlayerThatFailsToResetIsReleased() {
        val failing = InlinePlayerPool(create = { next++ }, reset = { error("stuck") }, release = { released += it })
        val player = failing.acquire()
        failing.recycle(player)
        assertEquals(listOf(player), released)
        assertEquals(0, failing.idleCount)
    }

    @Test
    fun afterTheScreenLeavesEverythingIsReleased() {
        val playing = pool.acquire()
        val spare = pool.acquire()
        pool.recycle(spare)
        pool.releaseAll()
        assertEquals(listOf(spare), released)
        pool.recycle(playing)
        assertEquals(listOf(spare, playing), released)
        assertEquals(0, pool.idleCount)
    }
}
