package tv.own.owntv.core.recording

import org.junit.Assert.*
import org.junit.Test
import tv.own.owntv.core.database.entity.EpgProgrammeEntity
import tv.own.owntv.core.database.entity.RecordingEntity
import tv.own.owntv.core.model.RecordingStatus

class RecordingReconciliationTest {
    private val minute = 60_000L
    private val now = 1_000_000_000L
    private val start = now + 12 * 3_600_000L
    private fun row() = RecordingEntity(
        id = 77, profileId = 1, sourceId = 4, channelId = 5, channelName = "Canal",
        epgChannelId = "guide", streamUrl = "https://example.test/live.m3u8", title = "Programa - Episode 4",
        programmeStartMs = start, programmeStopMs = start + 30 * minute,
        startMs = start - 5 * minute, stopMs = start + 33 * minute, filePath = "saved-target.ts", ruleId = 8,
    )
    private fun programme(offsetMinutes: Long = 20, source: Long = 4) = EpgProgrammeEntity(
        id = 19, sourceId = source, epgChannelId = "guide", title = "Programa - Episode 4",
        startMs = start + offsetMinutes * minute, stopMs = start + (offsetMinutes + 45) * minute,
    )

    @Test fun confidentMovePreservesIdentityTargetRuleAndBothMargins() {
        val original = row()
        val changed = RecordingReconciliation.move(original, programme(), now)!!
        assertEquals(original.id, changed.id)
        assertEquals(original.ruleId, changed.ruleId)
        assertEquals(original.filePath, changed.filePath)
        assertEquals(5 * minute, changed.programmeStartMs - changed.startMs)
        assertEquals(3 * minute, changed.stopMs - changed.programmeStopMs)
        assertEquals(45 * minute, changed.programmeStopMs - changed.programmeStartMs)
        assertEquals(RecordingStatus.SCHEDULED, changed.status)
    }

    @Test fun completedCancelledRunningAndRecoveryRowsAreNeverMoved() {
        for (status in RecordingStatus.entries.filter { it != RecordingStatus.SCHEDULED }) {
            assertNull(RecordingReconciliation.move(row().copy(status = status), programme(), now))
        }
        assertNull(RecordingReconciliation.move(row().copy(recoveryAttempt = 1), programme(), now))
        assertNull(RecordingReconciliation.move(row().copy(bytes = 1), programme(), now))
        assertNull(RecordingReconciliation.move(row().copy(startedAt = now), programme(), now))
    }

    @Test fun dueTimersArchivesAndUnchangedTimingsAreLeftAlone() {
        assertNull(RecordingReconciliation.move(row().copy(startMs = now), programme(), now))
        assertNull(RecordingReconciliation.move(row().copy(startMs = start + minute), programme(), now))
        assertNull(RecordingReconciliation.move(row(), programme(0).copy(stopMs = row().programmeStopMs), now))
    }

    @Test fun matchingNeedsTheCorrectGuideAndRegisteredSource() {
        assertEquals(programme(), RecordingProgrammeMatcher.find(row(), listOf(programme()), "guide", setOf(4)))
        assertNull(RecordingProgrammeMatcher.find(row(), listOf(programme()), "other", setOf(4)))
        assertNull(RecordingProgrammeMatcher.find(row(), listOf(programme(source = -2)), "guide", setOf(4)))
        assertEquals(-2L, RecordingProgrammeMatcher.find(row(), listOf(programme(source = -2)), "guide", setOf(-2))!!.sourceId)
    }

    @Test fun titleWhitespaceIsHarmlessButAnotherEpisodeNeverMatches() {
        assertNotNull(RecordingProgrammeMatcher.find(row(), listOf(programme().copy(title = "  PROGRAMA  - Episode 4 ")), "guide", setOf(4)))
        assertNull(RecordingProgrammeMatcher.find(row(), listOf(programme().copy(title = "Programa - Episode 5")), "guide", setOf(4)))
        assertNull(RecordingProgrammeMatcher.find(row().copy(title = ""), listOf(programme()), "guide", setOf(4)))
    }

    @Test fun repeatsOrDisagreeingFeedsDoNotSelectAnArbitraryShowing() {
        assertNull(RecordingProgrammeMatcher.find(row(), listOf(programme(20), programme(50)), "guide", setOf(4)))
        assertNull(RecordingProgrammeMatcher.find(row(), listOf(programme(), programme(source = -2).copy(stopMs = start + 80 * minute)), "guide", setOf(4, -2)))
        assertNotNull(RecordingProgrammeMatcher.find(row(), listOf(programme(), programme(source = -2)), "guide", setOf(4, -2)))
        assertNull(RecordingProgrammeMatcher.find(row(), listOf(programme(181)), "guide", setOf(4)))
    }

    @Test fun cancellationHistorySuppressesTheShiftedSeriesShowingWithoutBeingRewritten() {
        val cancelled = row().copy(status = RecordingStatus.CANCELLED)
        val programmes = listOf(programme())
        val aliases = RecordingProgrammeMatcher.knownShowingAliases(listOf(cancelled), programmes, 5, "guide", setOf(4))
        assertEquals(RecordingStatus.CANCELLED, aliases.single().status)
        assertEquals(cancelled.id, aliases.single().id)
        val pending = RecordingRuleMatcher.showingsToSchedule(
            RecordingRuleMatcher.fold(cancelled.title), 5, programmes, listOf(cancelled) + aliases, now,
        )
        assertTrue(pending.isEmpty())
        assertEquals(start, cancelled.programmeStartMs)
    }

    @Test fun reconciledScheduledShowingDoesNotProduceASecondRuleTimer() {
        val moved = RecordingReconciliation.move(row(), programme(), now)!!
        assertTrue(RecordingRuleMatcher.showingsToSchedule(
            RecordingRuleMatcher.fold(moved.title), 5, listOf(programme()), listOf(moved), now,
        ).isEmpty())
    }
}
