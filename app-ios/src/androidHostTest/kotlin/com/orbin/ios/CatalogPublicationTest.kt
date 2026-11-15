package com.orbin.ios

import com.orbin.core.model.BoardId
import com.orbin.core.model.CatalogThread
import com.orbin.core.model.Post
import com.orbin.core.model.PostId
import com.orbin.core.model.ProviderId
import com.orbin.core.model.ThreadId
import com.orbin.core.model.ThreadKey
import com.orbin.core.model.ThreadStats
import org.junit.Assert.assertEquals
import org.junit.Test

class CatalogPublicationTest {
    @Test
    fun refreshKeepsEveryCurrentlyVisibleRowUntilTheNewSnapshotArrives() {
        val old = entry(7)
        val removed = entry(99)
        val fresh = entry(8)
        val previous = listOf(old, removed).associate { it.key to it.toRow(0) }
        val merged = retainVisibleCatalogRows(listOf(old), previous, mapOf(fresh.key to fresh.toRow(0)))
        assertEquals(setOf(old.key, fresh.key), merged.keys)
        assertEquals(old.toRow(0), merged[old.key])
        val next = entry(9)
        val refreshed = retainVisibleCatalogRows(listOf(fresh), merged, mapOf(next.key to next.toRow(0)))
        assertEquals(setOf(fresh.key, next.key), refreshed.keys)
    }

    private fun entry(id: Long): CatalogThread {
        val board = BoardId("g")
        val thread = ThreadId(id)
        return CatalogThread(
            ThreadKey(ProviderId("fourchan"), board, thread),
            Post(PostId(id), board, thread, isOriginalPost = true, subject = "Thread $id"),
            ThreadStats(),
        )
    }
}
