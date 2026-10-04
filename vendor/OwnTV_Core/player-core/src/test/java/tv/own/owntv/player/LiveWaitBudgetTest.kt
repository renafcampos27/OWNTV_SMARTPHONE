package tv.own.owntv.player

import org.junit.Assert.assertEquals
import org.junit.Test

class LiveWaitBudgetTest {
    @Test fun `provider wait is credited immediately and once despite repeated polling`() {
        val budget = LiveWaitBudget(0, 15_000)
        assertEquals(44_000L, budget.remaining(1_000, null, LiveProviderWait(1, 30_000)))
        assertEquals(25_000L, budget.remaining(20_000, null, LiveProviderWait(1, 11_000)))
        assertEquals(0L, budget.remaining(45_000, null, null))
    }

    @Test fun `renewed provider waits have a finite shared ceiling`() {
        val budget = LiveWaitBudget(0, 15_000)
        budget.remaining(0, null, LiveProviderWait(1, 40_000))
        assertEquals(65_000L, budget.remaining(10_000, null, LiveProviderWait(2, 40_000)))
        assertEquals(0L, budget.remaining(75_000, null, LiveProviderWait(3, 60_000)))
    }

    @Test fun `silent recovery gets no additional preparation time`() {
        val budget = LiveWaitBudget(0, 15_500, recovery = true)
        val silent = LiveStartupProgress(1, 0, 0, sourceId = 2, recovering = true)
        assertEquals(0L, budget.remaining(15_500, silent, null))
    }

    @Test fun `a new source making progress can finish beyond the old two second reconnect margin`() {
        val budget = LiveWaitBudget(0, 15_500, recovery = true)
        val progress = LiveStartupProgress(1, 0, 500, loadedBytes = 100, sourceId = 2, recovering = true)
        assertEquals(6_500L, budget.remaining(14_000, progress, null))
        assertEquals(4_500L, budget.remaining(16_000, progress.copy(bufferedMs = 1_000), null))
        assertEquals(0L, budget.remaining(20_500, progress.copy(sourceId = 3, loadedBytes = 200), null))
    }

    @Test fun `natural underrun without a new preparation never earns a recovery allowance`() {
        val budget = LiveWaitBudget(0, 15_500, recovery = true)
        assertEquals(0L, budget.remaining(15_500, LiveStartupProgress(1, 10_000, 10_000), null))
    }

    @Test fun `initial prebuffer still earns only one bounded allowance`() {
        val budget = LiveWaitBudget(0, 12_000)
        val progress = LiveStartupProgress(1, 10_000, 1_000)
        assertEquals(21_000L, budget.remaining(1_000, progress, null))
        assertEquals(0L, budget.remaining(22_000, progress.copy(sourceId = 2, bufferedMs = 2_000), null))
    }
}
