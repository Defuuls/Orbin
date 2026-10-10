package com.orbin.data.sync

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MergeSyncTest {
    private fun visit(
        thread: Long,
        at: Long,
        post: Long? = null,
    ) = SyncedVisit("site", "g", thread, "t$thread", null, at, post)

    private fun mark(
        thread: Long,
        watched: Boolean,
        seen: Int = 0,
        created: Long = 1,
    ) = SyncedBookmark("site", "g", thread, "t$thread", null, created, watched, seen)

    private fun doc(
        visits: List<SyncedVisit> = emptyList(),
        marks: List<SyncedBookmark> = emptyList(),
    ) = SyncDocument(visits = visits, bookmarks = marks)

    @Test
    fun firstSyncIsTheUnionOfBothSides() {
        val merged = mergeSync(null, doc(listOf(visit(1, 10))), doc(listOf(visit(2, 20))))
        assertEquals(setOf(1L, 2L), merged.visits.map { it.thread }.toSet())
    }

    @Test
    fun theLaterVisitWinsWithThePostItStoppedAt() {
        val merged =
            mergeSync(
                base = doc(listOf(visit(1, 10, post = 100))),
                local = doc(listOf(visit(1, 30, post = 300))),
                remote = doc(listOf(visit(1, 20, post = 200))),
            )
        assertEquals(300L, merged.visits.single().lastReadPostId)
    }

    @Test
    fun aThreadRemovedHereIsRemovedEverywhere() {
        val agreed = doc(listOf(visit(1, 10), visit(2, 10)))
        val merged = mergeSync(agreed, local = doc(listOf(visit(1, 10))), remote = agreed)
        assertEquals(listOf(1L), merged.visits.map { it.thread })
    }

    @Test
    fun aThreadRemovedElsewhereIsRemovedHere() {
        val agreed = doc(marks = listOf(mark(1, watched = true)))
        val merged = mergeSync(agreed, local = agreed, remote = doc())
        assertTrue(merged.bookmarks.isEmpty())
    }

    @Test
    fun anEditBeatsARemoval() {
        val agreed = doc(listOf(visit(1, 10)))
        val merged = mergeSync(agreed, local = doc(listOf(visit(1, 50))), remote = doc())
        assertEquals(50L, merged.visits.single().lastVisitedMillis)
    }

    @Test
    fun watchingFollowsTheSideThatChangedIt() {
        val agreed = doc(marks = listOf(mark(1, watched = true)))
        val unwatchedElsewhere =
            mergeSync(agreed, local = agreed, remote = doc(marks = listOf(mark(1, watched = false))))
        assertEquals(false, unwatchedElsewhere.bookmarks.single().watched)
        val unwatchedHere = mergeSync(agreed, local = doc(marks = listOf(mark(1, watched = false))), remote = agreed)
        assertEquals(false, unwatchedHere.bookmarks.single().watched)
    }

    @Test
    fun repliesSeenIsTheLargerCountAndCreationTheEarlier() {
        val merged =
            mergeSync(
                null,
                local = doc(marks = listOf(mark(1, watched = true, seen = 5, created = 40))),
                remote = doc(marks = listOf(mark(1, watched = true, seen = 9, created = 20))),
            )
        assertEquals(9, merged.bookmarks.single().lastSeenReplyCount)
        assertEquals(20L, merged.bookmarks.single().createdAtMillis)
    }
}
