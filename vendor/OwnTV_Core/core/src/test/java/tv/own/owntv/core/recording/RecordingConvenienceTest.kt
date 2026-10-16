package tv.own.owntv.core.recording

import org.junit.Assert.*
import org.junit.Test
import tv.own.owntv.core.database.entity.RecordingEntity
import tv.own.owntv.core.database.entity.EpgProgrammeEntity
import tv.own.owntv.core.model.RecordingStatus
import java.nio.file.Files

class RecordingConvenienceTest {
    private fun row(id: Long = 1) = RecordingEntity(id = id, profileId = 1, sourceId = 2,
        channelId = 3, channelName = "Canal", streamUrl = "unused", title = "Programa",
        programmeStartMs = 10_000, programmeStopMs = 3_610_000, startMs = 5_000, stopMs = 3_615_000)
    private val storage = RecordingStorageInfo(10_000_000_000, 20_000_000_000, 1_000_000_000,
        quotaBytes = 5_000_000_000, mediaBytes = 2_000_000_000)

    @Test fun measuredRangeUsesMediaDurationAndRejectsOtherChannelHistory() {
        val history = listOf(row().copy(status = RecordingStatus.COMPLETED, bytes = 60_000_000, capturedDurationMs = 60_000),
            row(2).copy(channelId = 99, status = RecordingStatus.COMPLETED, bytes = 900_000_000, capturedDurationMs = 60_000))
        val forecast = RecordingSpaceEstimator.estimate(3_600_000, 2, 3, history, storage)
        assertTrue(forecast.measured); assertEquals(2_520_000_000, forecast.lowerBytes)
        assertEquals(5_400_000_000, forecast.upperBytes); assertEquals(3_000_000_000, forecast.availableBytes)
        assertTrue(forecast.mayExceedSpace)
    }
    @Test fun unknownCapacityRemainsUnknownAndExistingReservationIsExcluded() {
        val pending = row().copy(filePath = "local")
        val unknown = RecordingSpaceEstimator.estimate(60_000, 2, 3, listOf(pending), storage.copy(known = false))
        assertNull(unknown.availableBytes); assertFalse(unknown.mayExceedSpace); assertFalse(unknown.measured)
        val excluded = RecordingSpaceEstimator.estimate(60_000, 2, 3, listOf(pending), storage, excludeId = pending.id)
        assertEquals(0, excluded.reservedBytes)
        assertEquals(3_000_000_000, excluded.availableBytes)
    }
    @Test fun reservationsRespectQuotaWithoutOverflow() {
        val forecast = RecordingSpaceEstimator.estimate(Long.MAX_VALUE, 2, 3,
            listOf(row().copy(stopMs = Long.MAX_VALUE), row(2).copy(stopMs = Long.MAX_VALUE)), storage)
        assertEquals(Long.MAX_VALUE, forecast.reservedBytes); assertEquals(0L, forecast.availableBytes)
        assertTrue(forecast.mayExceedSpace)
    }
    @Test fun chaptersRetainMetadataAndRespectLateStartAndKnownGaps() {
        val capture = row().copy(startedAt = 60_000, capturedDurationMs = 200_000)
        val programmes = listOf(
            EpgProgrammeEntity(sourceId = 2, epgChannelId = "canal", startMs = 10_000, stopMs = 100_000, title = "Primeiro"),
            EpgProgrammeEntity(sourceId = 2, epgChannelId = "canal", startMs = 100_000, stopMs = 200_000, title = "Segundo"),
            EpgProgrammeEntity(sourceId = 2, epgChannelId = "canal", startMs = 300_000, stopMs = 400_000, title = "Fora do vídeo"))
        val snapshot = RecordingChapters.snapshot(capture, programmes)
        val points = RecordingChapters.points(capture, snapshot)
        assertEquals(listOf(0L, 40_000L), points.map { it.positionMs })
        assertEquals(listOf("Primeiro", "Segundo"), points.map { it.title })
        assertTrue(RecordingChapters.points(capture.copy(gapCount = 1), snapshot).isEmpty())
    }
    @Test fun archiveChaptersUseProgrammeTimeInsteadOfDownloadTime() {
        val archive = row().copy(startMs = 9_000_000, stopMs = 12_600_000,
            startedAt = 9_000_500, capturedDurationMs = 3_600_000)
        val chapters = listOf(RecordingChapter("Primeiro", 10_000, 100_000), RecordingChapter("Segundo", 100_000, 200_000))
        assertEquals(listOf(0L, 90_000L), RecordingChapters.points(archive, chapters).map { it.positionMs })
    }

    @Test fun chapterFileSurvivesNewStoreAndRejectsDifferentProfile() {
        val directory = Files.createTempDirectory("chapters-test").toFile()
        try {
            val original = row()
            val entries = listOf(RecordingChapter("Notícias à noite", 10_000, 100_000))
            RecordingChapterStore(directory).save(original, entries)
            val restored = RecordingChapterStore(directory)
            assertEquals(entries, restored.read(original))
            assertTrue(runCatching { restored.read(original.copy(profileId = 99)) }.isFailure)
            val target = directory.listFiles()!!.single()
            target.appendBytes(byteArrayOf(1))
            assertTrue(runCatching { restored.read(original) }.isFailure)
            restored.delete(original); assertTrue(directory.listFiles()!!.isEmpty())
        } finally { directory.deleteRecursively() }
    }
}
