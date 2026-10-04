package tv.own.owntv.core.recording

import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import tv.own.owntv.core.database.entity.RecordingEntity
import tv.own.owntv.core.model.RecordingStatus

class ArchiveResumePolicyTest {
    @get:Rule val temporary = TemporaryFolder()
    private fun row() = RecordingEntity(id = 7, profileId = 1, sourceId = 2, channelId = 3,
        channelName = "SIC", streamUrl = "", title = "Programme", programmeStartMs = 0,
        programmeStopMs = 60_000, startMs = 120_000, stopMs = 240_000, status = RecordingStatus.RECORDING)
    private fun playlist() = HlsMediaPlaylist.parse("""
        #EXTM3U
        #EXT-X-TARGETDURATION:6
        #EXT-X-MEDIA-SEQUENCE:20
        #EXTINF:6,
        a.ts?token=old
        #EXTINF:6,
        b.ts?token=old
        #EXT-X-ENDLIST
    """.trimIndent())
    private fun committed(): HlsCaptureSession {
        val session = HlsCaptureSession(temporary.newFolder())
        session.archiveIdentity = ArchiveResumePolicy.identity(row())
        session.archiveTimeline = ArchiveResumePolicy.timeline(playlist())
        val file = File(session.directory, "segment.download").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        session.commit(playlist().segments.first(), file)
        return session
    }

    @Test fun pauseOnlyOfferedAfterFiniteArchiveHasCommittedMedia() {
        assertFalse(ArchiveResumePolicy.canPause(row()))
        val session = committed()
        assertTrue(ArchiveResumePolicy.canPause(row().copy(hlsCheckpoint = session.checkpoint())))
        assertFalse(ArchiveResumePolicy.canPause(row().copy(programmeStopMs = 180_000, hlsCheckpoint = session.checkpoint())))
        assertFalse(ArchiveResumePolicy.canPause(row().copy(hlsCheckpoint = "broken")))
    }

    @Test fun pauseSurvivesReopeningAndOnlyEligibleRowsCanResume() {
        val original = committed()
        val restored = HlsCaptureSession(original.directory)
        assertEquals(original.archiveIdentity, restored.archiveIdentity)
        assertEquals(original.archiveTimeline, restored.archiveTimeline)
        assertEquals(original.entries, restored.entries)
        val paused = row().copy(archivePaused = true, status = RecordingStatus.PARTIAL,
            hlsCheckpoint = restored.checkpoint(), bytes = 3, filePath = "/capture.ts")
        assertTrue(ArchiveResumePolicy.canResume(paused))
        assertFalse(RecordingIntegrity.canPlay(paused))
        assertFalse(ArchiveResumePolicy.canResume(paused.copy(status = RecordingStatus.SCHEDULED)))
        assertFalse(ArchiveResumePolicy.canResume(paused.copy(archivePaused = false)))
    }

    @Test fun signedUrlRotationAndLocalChannelIdReplacementDoNotChangeContinuity() {
        val session = committed()
        val refreshed = playlist().copy(segments = playlist().segments.map { it.copy(uri = it.uri.replace("old", "fresh")) })
        assertNotNull(ArchiveResumePolicy.validatedTail(session, row().copy(channelId = 900), refreshed))
        assertEquals(ArchiveResumePolicy.timeline(playlist()), ArchiveResumePolicy.timeline(refreshed))
    }

    @Test fun anotherProgrammeProfileOrSourceCannotReuseCapture() {
        val session = committed()
        assertNull(ArchiveResumePolicy.validatedTail(session, row().copy(programmeStartMs = 1), playlist()))
        assertNull(ArchiveResumePolicy.validatedTail(session, row().copy(sourceId = 9), playlist()))
        assertNull(ArchiveResumePolicy.validatedTail(session, row().copy(profileId = 9), playlist()))
    }

    @Test fun resegmentedChangedOrExpiredTimelineIsRefusedWithoutDeletingMedia() {
        val session = committed()
        val changed = playlist().copy(segments = playlist().segments.map { it.copy(sequence = it.sequence + 1) })
        assertNull(ArchiveResumePolicy.validatedTail(session, row(), changed))
        assertNull(ArchiveResumePolicy.validatedTail(session, row(), playlist().copy(segments = playlist().segments.drop(1))))
        assertNull(ArchiveResumePolicy.validatedTail(session, row(), playlist().copy(segments = playlist().segments.map { it.copy(discontinuity = 1) })))
        assertTrue(session.inputs().all { it.isFile })
        assertEquals(6_000L, session.capturedDurationMs)
    }

    @Test fun dynamicEncryptedAndChangedContainerArchivesAreNotResumable() {
        assertNull(ArchiveResumePolicy.timeline(playlist().copy(endList = false)))
        assertNull(ArchiveResumePolicy.timeline(playlist().copy(encryptionMethod = "AES-128")))
        val changed = playlist().copy(segments = playlist().segments.map { it.copy(init = HlsMediaPlaylist.Init("init.mp4")) })
        assertNull(ArchiveResumePolicy.validatedTail(committed(), row(), changed))
    }

    @Test fun interruptedScratchIsDiscardedAndCompleteSegmentIsNeverCommittedTwice() {
        val original = committed()
        File(original.directory, "segment.download").writeBytes(byteArrayOf(9))
        val restored = HlsCaptureSession(original.directory)
        assertFalse(File(restored.directory, "segment.download").exists())
        val duplicate = File(restored.directory, "segment.download").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        assertFalse(restored.commit(playlist().segments.first(), duplicate))
        assertEquals(1, restored.entries.size)
        assertEquals(6_000L, restored.capturedDurationMs)
    }
    @Test fun sameSequenceWithDifferentPayloadIsRejected() {
        val session = committed()
        val proof = File(session.directory, "resume.download").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        assertTrue(ArchiveResumePolicy.proofMatches(session.entries.last(), proof))
        proof.writeBytes(byteArrayOf(3, 2, 1))
        assertFalse(ArchiveResumePolicy.proofMatches(session.entries.last(), proof))
        assertEquals(1, session.entries.size)
        assertTrue(session.inputs().single().readBytes().contentEquals(byteArrayOf(1, 2, 3)))
    }

}
