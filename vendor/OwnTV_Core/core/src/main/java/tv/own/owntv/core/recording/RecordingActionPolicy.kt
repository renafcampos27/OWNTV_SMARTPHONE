package tv.own.owntv.core.recording

import tv.own.owntv.core.database.entity.RecordingEntity
import tv.own.owntv.core.model.RecordingStatus

/** A menu selection names a job, never a path or a global recording state. */
internal fun recordingActionMatches(selected: RecordingEntity, current: RecordingEntity?,
    allowed: Set<RecordingStatus>? = null): Boolean = current != null && selected.id == current.id &&
    selected.profileId == current.profileId && selected.channelId == current.channelId &&
    selected.sourceId == current.sourceId && (allowed == null || current.status in allowed)
