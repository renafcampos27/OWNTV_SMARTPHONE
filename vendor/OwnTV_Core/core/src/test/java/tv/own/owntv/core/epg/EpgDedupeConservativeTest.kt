package tv.own.owntv.core.epg

import org.junit.Assert.*
import org.junit.Test
import tv.own.owntv.core.database.entity.EpgProgrammeEntity

class EpgDedupeConservativeTest {
    private fun row(id: Long, source: Long, start: Long=0, end: Long=3_600_000, description: String?=null, key: String="epg") =
        EpgProgrammeEntity(id, source, key, start, end, "News", description)
    @Test fun ownSourceWinsKeepingOriginalClockAndIdentity() {
        val own = row(2, 2, 60_000, 3_500_000)
        assertSame(own, EpgDedupe.collapse(listOf(row(1, 1), own), 2).single())
    }
    @Test fun marginalOverlapOfRepeatedNewsIsNotDuplicate() {
        assertEquals(2, EpgDedupe.collapse(listOf(row(1,1), row(2,2,3_590_000,7_200_000))).size)
    }
    @Test fun conflictingKnownSynopsisKeepsBoth() {
        assertEquals(2,EpgDedupe.collapse(listOf(row(1,1,description="Edition A"), row(2,2,description="Edition B"))).size)
    }
    @Test fun missingSynopsisDoesNotDefeatStrongTimeMatch() {
        assertEquals(1,EpgDedupe.collapse(listOf(row(1,1,description="Edition A"), row(2,2))).size)
    }
    @Test fun shortEventsUseScaledStartDistance() {
        assertEquals(1,EpgDedupe.collapse(listOf(row(1,1,0,30_000), row(2,2,1_000,31_000))).size)
        assertEquals(2,EpgDedupe.collapse(listOf(row(1,1,0,30_000), row(2,2,25_000,55_000))).size)
    }
    @Test fun distinctChannelsNeverMergeEvenWhenInterleaved() {
        assertEquals(2,EpgDedupe.collapse(listOf(row(1,1), row(2,2,key="other"), row(3,3))).size)
    }
    @Test fun invalidDurationsAreNotDuplicates() {
        assertEquals(2,EpgDedupe.collapse(listOf(row(1,1,5,5), row(2,2,5,5))).size)
    }
}
