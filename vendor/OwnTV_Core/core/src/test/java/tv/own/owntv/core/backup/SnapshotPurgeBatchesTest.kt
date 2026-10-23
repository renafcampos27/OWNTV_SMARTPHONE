package tv.own.owntv.core.backup

import org.junit.Assert.*
import org.junit.Test
import tv.own.owntv.core.model.MediaType

class SnapshotPurgeBatchesTest {
    private val kinds = setOf("order", "member")

    @Test fun `large duplicate snapshots fit old SQLite limits without losing ids`() {
        val rows = (1L..2_005L).map { SnapshotRow("member", 7, MediaType.LIVE, it) }
        val batches = snapshotPurgeBatches(rows + rows.reversed(), kinds)
        assertEquals(listOf(900, 900, 205), batches.map { it.itemIds.size })
        assertEquals((1L..2_005L).toList(), batches.flatMap { it.itemIds })
    }

    @Test fun `same ids stay separate across kinds profiles and media types`() {
        val rows = kinds.flatMap { kind ->
            listOf(7L, 8L).flatMap { profile ->
                listOf(MediaType.LIVE, MediaType.MOVIE, MediaType.SERIES).map { type ->
                    SnapshotRow(kind, profile, type, 42)
                }
            }
        }
        val batches = snapshotPurgeBatches(rows + SnapshotRow("fav", 7, MediaType.LIVE, 99), kinds)
        assertEquals(rows.toSet(), batches.flatMap { b -> b.itemIds.map { SnapshotRow(b.kind, b.profileId, b.type, it) } }.toSet())
        assertEquals(rows.size, batches.size)
    }

    @Test fun `empty snapshots and excluded operations generate no delete batches`() {
        assertTrue(snapshotPurgeBatches(emptyList(), kinds).isEmpty())
        assertTrue(snapshotPurgeBatches(listOf(SnapshotRow("his", 7, MediaType.LIVE, 42)), kinds).isEmpty())
    }

    @Test fun `chunk ceiling cannot create an oversized SQLite statement`() {
        for (chunk in listOf(0, -1, 901, Int.MAX_VALUE)) {
            assertThrows(IllegalArgumentException::class.java) { snapshotPurgeBatches(emptyList(), kinds, chunk) }
        }
    }
}
