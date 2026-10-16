package tv.own.owntv.player
import org.junit.Assert.*
import org.junit.Test
class MpvCacheRefillTest {
    @Test fun slowRefillDoesNotBecomeAStallButADeadCacheDoes() {
        val refill = MpvCacheRefill()
        assertFalse(refill.isGrowing(true, 0.0))
        for (n in 1..15) assertTrue(refill.isGrowing(true, n.toDouble()))
        assertFalse(refill.isGrowing(true, 15.0))
        assertFalse(refill.isGrowing(true, 14.0))
    }
    @Test fun progressOutsideCachePauseIsNotRefilling() {
        val refill = MpvCacheRefill()
        assertFalse(refill.isGrowing(false, 1.0))
        assertFalse(refill.isGrowing(false, 2.0))
    }
    @Test fun invalidSamplesAndNewTuneCannotReusePreviousEvidence() {
        val refill = MpvCacheRefill()
        refill.isGrowing(true, 10.0)
        assertFalse(refill.isGrowing(true, Double.NaN))
        assertFalse(refill.isGrowing(true, 11.0))
        assertFalse(MpvCacheRefill().isGrowing(true, 12.0))
    }
}
