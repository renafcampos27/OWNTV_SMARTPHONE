package tv.own.owntv.core.recording

import tv.own.owntv.core.database.entity.EpgProgrammeEntity
import tv.own.owntv.core.database.entity.RecordingEntity
import java.text.Normalizer
import java.util.Locale

/** Conservative identity matching for an existing timer, rather than a broad series-title rule. */
object RecordingProgrammeMatcher {
    const val MAX_SHIFT_MS = 3L * 60 * 60 * 1000
    private val whitespace = Regex("\\s+")

    private fun titleKey(title: String): String = whitespace.replace(
        Normalizer.normalize(title, Normalizer.Form.NFC).trim().lowercase(Locale.ROOT), " ",
    )

    /** Ambiguous repeats or disagreeing feeds leave the original timer untouched. */
    fun find(
        recording: RecordingEntity,
        programmes: List<EpgProgrammeEntity>,
        epgKey: String,
        allowedSourceIds: Set<Long>,
    ): EpgProgrammeEntity? {
        val title = titleKey(recording.title)
        if (title.isEmpty() || epgKey.isBlank()) return null
        val candidates = programmes.filter {
            it.sourceId in allowedSourceIds &&
                it.epgChannelId.trim().equals(epgKey.trim(), ignoreCase = true) &&
                titleKey(it.title) == title && it.stopMs > it.startMs &&
                it.startMs in (recording.programmeStartMs - MAX_SHIFT_MS)..(recording.programmeStartMs + MAX_SHIFT_MS)
        }
        // Identical guide copies from two registered feeds describe one showing, not two timers.
        // Different start/stop times remain ambiguous, even when one is closer to the old time.
        return candidates.distinctBy { it.startMs to it.stopMs }.singleOrNull()
    }

    /** Also remember shifted cancelled/completed showings when a series rule walks the new guide. */
    fun knownShowingAliases(
        existing: List<RecordingEntity>,
        programmes: List<EpgProgrammeEntity>,
        channelId: Long,
        epgKey: String,
        allowedSourceIds: Set<Long>,
    ): List<RecordingEntity> = existing.mapNotNull { row ->
        if (row.channelId != channelId || row.recoveryAttempt != 0) return@mapNotNull null
        val programme = find(row, programmes, epgKey, allowedSourceIds) ?: return@mapNotNull null
        row.copy(programmeStartMs = programme.startMs)
    }
}
