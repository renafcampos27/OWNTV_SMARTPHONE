package tv.own.owntv.mobile.ui.screens.guide

import org.junit.Assert.*
import org.junit.Test

class GuideRefreshPolicyTest {
    @Test fun firstRealChangeIsNeverDiscarded() {
        val policy = GuideRefreshPolicy(10_000)
        policy.changed(0)
        assertFalse(policy.takeReady(9_999, true))
        assertTrue(policy.takeReady(10_000, true))
        assertFalse(policy.takeReady(20_000, true))
    }
    @Test fun batchesAtZeroTwoFiveNineSecondsRefreshOnceAfterLastBatch() {
        val policy = GuideRefreshPolicy(10_000)
        listOf(0L,2_000,5_000,9_000).forEach { time -> policy.changed(time); assertFalse(policy.takeReady(time, true)) }
        assertFalse(policy.takeReady(18_999, true))
        assertTrue(policy.takeReady(19_000, true))
        assertFalse(policy.takeReady(30_000, true))
    }
    @Test fun sameCountTitleOrTimeEditStillInvalidates() {
        val policy = GuideRefreshPolicy(10_000)
        policy.changed(0); assertTrue(policy.takeReady(10_000, true))
        policy.changed(20_000); assertTrue(policy.takeReady(30_000, true))
    }
    @Test fun hiddenScreenDefersWorkAndReturningRefreshesImmediatelyOnce() {
        val policy = GuideRefreshPolicy(10_000)
        policy.changed(0)
        assertFalse(policy.takeReady(60_000, false))
        assertTrue(policy.takeOnReturn())
        assertFalse(policy.takeOnReturn())
        assertFalse(policy.takeReady(60_000, true))
    }
    @Test fun returnDuringBatchDoesNotLoseTheFollowingWrite() {
        val policy = GuideRefreshPolicy(10_000)
        policy.changed(0); assertTrue(policy.takeOnReturn())
        policy.changed(5_000); assertTrue(policy.takeReady(15_000, true))
    }
    @Test fun cleanReturnKeepsEligibleCompletedCache() {
        assertFalse(GuideRefreshPolicy(10_000).takeOnReturn())
    }
}
