package tv.own.owntv.core.recording

import org.json.JSONObject
import java.security.MessageDigest
import tv.own.owntv.core.database.entity.RecordingEntity
import tv.own.owntv.core.model.RecordingStatus

/** A paused archive is retained media, never a finalized playable file or an active timer. */
object ArchiveResumePolicy {
    fun canPause(row: RecordingEntity): Boolean = row.status == RecordingStatus.RECORDING &&
        RecordingSchedule.isCatchUp(row) && hasCheckpoint(row.hlsCheckpoint)

    fun canResume(row: RecordingEntity): Boolean = row.archivePaused &&
        row.status == RecordingStatus.PARTIAL && RecordingSchedule.isCatchUp(row) && hasCheckpoint(row.hlsCheckpoint)

    private fun hasCheckpoint(raw: String?): Boolean = raw != null && runCatching {
        val json = JSONObject(raw)
        json.optString("archive").isNotEmpty() && json.optString("timeline").isNotEmpty() && json.optLong("captured") > 0
    }.getOrDefault(false)

    internal fun identity(row: RecordingEntity): String = digest(
        listOf(row.id, row.profileId, row.sourceId, row.programmeStartMs, row.programmeStopMs).joinToString(":"))

    /** Only a finite, unchanged timeline can reuse its old sequence numbers. URLs may be re-signed. */
    internal fun timeline(playlist: HlsMediaPlaylist): String? {
        if (!playlist.endList || playlist.invalid || playlist.isEncrypted || playlist.isMaster || playlist.segments.isEmpty()) return null
        return digest(playlist.segments.joinToString(";") {
            listOf(it.sequence, HlsRecordingPlan.durationMs(it), it.discontinuity, it.gap, it.init != null).joinToString(":")
        })
    }

    internal fun validatedTail(session: HlsCaptureSession, row: RecordingEntity, playlist: HlsMediaPlaylist): HlsMediaPlaylist.Segment? {
        if (session.archiveIdentity != identity(row) || session.archiveTimeline == null || session.archiveTimeline != timeline(playlist)) return null
        val entry = session.entries.lastOrNull() ?: return null
        return playlist.segments.firstOrNull { tailMatches(entry, it) }
    }

    internal fun proofMatches(entry: HlsCaptureSession.Entry, proof: java.io.File): Boolean =
        proof.length() == entry.bytes && HlsCaptureSession.sha256(proof) == entry.hash

    internal fun tailMatches(entry: HlsCaptureSession.Entry, segment: HlsMediaPlaylist.Segment): Boolean =
        entry.sequence == segment.sequence && entry.durationMs == HlsRecordingPlan.durationMs(segment) && !segment.gap

    private fun digest(value: String) = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
}
