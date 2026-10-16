package tv.own.owntv.mobile.ui.screens.recordings

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import tv.own.owntv.core.database.entity.RecordingEntity
import tv.own.owntv.core.recording.RecordingIntegrity
import tv.own.owntv.core.storage.MediaAvailability

/** Subscription and screen visibility both own the probe; leaving cancels any read or timer. */
@OptIn(ExperimentalCoroutinesApi::class)
internal fun recordingAvailability(
    rows: Flow<List<RecordingEntity>>,
    active: Flow<Boolean>,
    inspect: suspend (List<RecordingEntity>) -> Map<Long, MediaAvailability>,
    pause: suspend () -> Unit = { delay(5_000L) },
): Flow<Map<Long, MediaAvailability>> = combine(
    rows.map { list -> list.filter(RecordingIntegrity::canPlay) }
        .distinctUntilChangedBy { list -> list.map { RecordingFileKey(it.id, it.profileId, it.filePath) }.toSet() },
    active,
) { list, visible -> list to visible }
    .distinctUntilChanged()
    .flatMapLatest { (list, visible) ->
        if (!visible || list.isEmpty()) flowOf(emptyMap())
        else flow {
            while (true) {
                emit(inspect(list))
                pause()
            }
        }
    }.distinctUntilChanged()

/** Progress, recording titles and sorting do not change physical availability. */
private data class RecordingFileKey(val id: Long, val profileId: Long, val path: String?)
