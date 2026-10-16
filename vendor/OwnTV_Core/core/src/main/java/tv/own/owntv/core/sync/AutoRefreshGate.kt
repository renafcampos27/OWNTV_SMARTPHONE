package tv.own.owntv.core.sync

import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import tv.own.owntv.core.live.WatchSession
import tv.own.owntv.core.live.OpenStreamRegistry
import tv.own.owntv.core.database.dao.RecordingDao

/** Automatic housekeeping yields to playback and recording; manual work remains explicit. */
class AutoRefreshGate(
    private val watching: WatchSession,
    private val streams: OpenStreamRegistry,
    private val recordings: RecordingDao,
) {
    suspend fun awaitIdle(): Boolean = withTimeoutOrNull(120_000L) {
        combine(watching.watching, streams.claims, recordings.observeHasRunning()) { watched, claims, running ->
            watched.isEmpty() && claims.isEmpty() && !running
        }.first { it }
        true
    } ?: false
}
