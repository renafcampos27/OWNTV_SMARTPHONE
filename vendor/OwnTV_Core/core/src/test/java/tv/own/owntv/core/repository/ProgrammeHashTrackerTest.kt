package tv.own.owntv.core.repository

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.own.owntv.core.database.entity.EpgHashProjection

class ProgrammeHashTrackerTest {
    private fun row(id: Long, start: Long, hash: Int) = EpgHashProjection(id, "guide", start, hash)

    @Test fun `unchanged seen rows stay and unseen rows are stale`() = runBlocking {
        val tracker = ProgrammeHashTracker(10) { _, _ -> listOf(row(1, 100, 5), row(2, 200, 6)) }
        assertEquals(ProgrammeDecision.Unchanged, tracker.observe("guide", 100, 5))
        assertEquals(listOf(2L), tracker.staleTrackedIds())
    }

    @Test fun `changed duplicate keeps the last content and original id`() = runBlocking {
        val tracker = ProgrammeHashTracker(10) { _, _ -> listOf(row(9, 100, 5)) }
        assertEquals(ProgrammeDecision.Changed(9), tracker.observe("guide", 100, 6))
        assertEquals(ProgrammeDecision.Changed(9), tracker.observe("guide", 100, 7))
        assertEquals(ProgrammeDecision.Unchanged, tracker.observe("guide", 100, 7))
        assertTrue(tracker.staleTrackedIds().isEmpty())
    }

    @Test fun `new duplicate updates content without adding a second entry`() = runBlocking {
        val tracker = ProgrammeHashTracker(10) { _, _ -> emptyList() }
        assertEquals(ProgrammeDecision.New, tracker.observe("guide", 100, 1))
        assertEquals(ProgrammeDecision.Changed(0), tracker.observe("guide", 100, 2))
        assertEquals(ProgrammeDecision.Unchanged, tracker.observe("guide", 100, 2))
        assertEquals(1, tracker.trackedEntries)
        assertTrue(tracker.staleTrackedIds().isEmpty())
    }

    @Test fun `oversized loaded channel writes through without partial stale pruning`() = runBlocking {
        var requestedLimit = 0
        var calls = 0
        val tracker = ProgrammeHashTracker(2) { _, limit ->
            requestedLimit = limit
            calls++
            listOf(row(1, 100, 1), row(2, 200, 2), row(3, 300, 3))
        }
        assertEquals(ProgrammeDecision.WriteThrough, tracker.observe("guide", 100, 4))
        assertEquals(ProgrammeDecision.WriteThrough, tracker.observe("guide", 200, 5))
        assertEquals(3, requestedLimit)
        assertEquals(1, calls)
        assertEquals(0, tracker.trackedEntries)
        assertTrue(tracker.staleTrackedIds().isEmpty())
        assertTrue(tracker.overflowed)
    }

    @Test fun `fresh single channel cannot grow beyond cap`() = runBlocking {
        val tracker = ProgrammeHashTracker(2) { _, _ -> emptyList() }
        assertEquals(ProgrammeDecision.New, tracker.observe("guide", 100, 1))
        assertEquals(ProgrammeDecision.New, tracker.observe("guide", 200, 2))
        assertEquals(ProgrammeDecision.WriteThrough, tracker.observe("guide", 300, 3))
        assertEquals(ProgrammeDecision.Changed(0), tracker.observe("guide", 100, 4))
        assertEquals(2, tracker.trackedEntries)
        assertTrue(tracker.overflowed)
    }

    @Test fun `loaded rows and new rows share one global budget`() = runBlocking {
        val limits = mutableListOf<Int>()
        val tracker = ProgrammeHashTracker(3) { channel, limit ->
            limits.add(limit)
            if (channel == "a") listOf(row(1, 100, 1)) else listOf(row(2, 300, 3))
        }
        tracker.observe("a", 100, 1)
        tracker.observe("a", 200, 2)
        tracker.observe("b", 300, 3)
        assertEquals(listOf(4, 2), limits)
        assertEquals(ProgrammeDecision.WriteThrough, tracker.observe("c", 400, 4))
        assertEquals(3, tracker.trackedEntries)
    }

    @Test fun `zero budget never loads a snapshot`() = runBlocking {
        val tracker = ProgrammeHashTracker(0) { _, _ -> error("Must not load") }
        assertEquals(ProgrammeDecision.WriteThrough, tracker.observe("guide", 0, 0))
        assertTrue(tracker.staleTrackedIds().isEmpty())
    }

    @Test fun `resizing preserves timestamp keys hashes ids and seen flags`() = runBlocking {
        val rows = (1L..200L).map { row(it, it * 60_000, it.toInt()) }
        val tracker = ProgrammeHashTracker(500) { _, _ -> rows }
        for (i in 1L..100L) assertEquals(ProgrammeDecision.Unchanged, tracker.observe("guide", i * 60_000, i.toInt()))
        for (i in 201L..400L) assertEquals(ProgrammeDecision.New, tracker.observe("guide", i * 60_000, i.toInt()))
        assertEquals((101L..200L).toSet(), tracker.staleTrackedIds().toSet())
        assertEquals(400, tracker.trackedEntries)
        assertFalse(tracker.overflowed)
    }

    @Test fun `zero negative and extreme timestamps are valid keys`() = runBlocking {
        val tracker = ProgrammeHashTracker(10) { _, _ -> emptyList() }
        val keys = listOf(0L, -1L, Long.MIN_VALUE, Long.MAX_VALUE)
        keys.forEach { assertEquals(ProgrammeDecision.New, tracker.observe("guide", it, 1)) }
        keys.forEach { assertEquals(ProgrammeDecision.Unchanged, tracker.observe("guide", it, 1)) }
        assertEquals(keys.size, tracker.trackedEntries)
    }
}
