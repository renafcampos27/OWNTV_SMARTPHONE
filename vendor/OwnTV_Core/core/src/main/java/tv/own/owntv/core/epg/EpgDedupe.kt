package tv.own.owntv.core.epg

import tv.own.owntv.core.database.entity.EpgProgrammeEntity

/**
 * Collapses the same programme listed more than once on one channel.
 *
 * Guide rows are keyed by `epgChannelId`, and that key is deliberately looked up across **every**
 * playlist and EPG feed rather than only the channel's own source — one playlist's `xmltv.php`
 * routinely covers another's lineup, and narrowing the query loses rows that are really there. The
 * cost of that breadth is this: when two feeds both carry a channel, every programme comes back
 * twice. The table's unique index is `(sourceId, epgChannelId, startMs)`, so it cannot stop them —
 * the two rows have different `sourceId`s, and usually different start times as well, because two
 * feeds rarely agree to the minute.
 *
 * On screen that looked like a guide drawing a block on top of another block (`22:04–00:16` under
 * `22:10–00:15`, the same film), and a Next/Later list that named the same programme twice in a row.
 *
 * **The test is a shared title, nearby starts and substantial overlap.** Overlapping alone is
 * two feeds disagreeing about a schedule, which is not something this can honestly resolve by
 * throwing half of it away; the same title back to back without overlap is a genuine repeat, which
 * channels really do broadcast. A marginal overlap between repeated blocks is not proof of a duplicate.
 */
object EpgDedupe {

    /** Resolve duplicates on read, preferring the channel's own feed; never delete stored feeds. */
    fun collapse(rows: List<EpgProgrammeEntity>, preferredSourceId: Long? = null): List<EpgProgrammeEntity> {
        if (rows.size < 2) return rows
        val sorted = rows.sortedWith(compareBy<EpgProgrammeEntity>({ it.startMs }, { it.stopMs }, { it.sourceId }, { it.id }))
        val kept = ArrayList<EpgProgrammeEntity>(sorted.size)
        val lastByTitle = HashMap<Pair<String, String>, Int>()
        for (row in sorted) {
            val title = row.epgChannelId to normalise(row.title)
            val index = lastByTitle[title]
            val previous = index?.let { kept[it] }
            if (previous != null && isSameProgramme(previous, row)) {
                val priority = compareBy<EpgProgrammeEntity> { if (it.sourceId == preferredSourceId) 0 else 1 }
                    .thenByDescending { it.durationMs }.thenBy { it.startMs }.thenBy { it.sourceId }.thenBy { it.id }
                if (priority.compare(row, previous) < 0) kept[index] = row
            } else {
                lastByTitle[title] = kept.size
                kept += row
            }
        }
        return kept.sortedWith(compareBy({ it.startMs }, { it.stopMs }, { it.sourceId }, { it.id }))
    }

    /** The same broadcast written down twice: one title, one stretch of time. */
    private fun isSameProgramme(a: EpgProgrammeEntity, b: EpgProgrammeEntity): Boolean {
        if (a.epgChannelId != b.epgChannelId || normalise(a.title) != normalise(b.title)) return false
        val shorter = minOf(a.durationMs, b.durationMs)
        if (shorter <= 0L) return false
        val overlap = minOf(a.stopMs, b.stopMs) - maxOf(a.startMs, b.startMs)
        if (overlap <= 0L) return false
        // Scale to short events too: no fixed five-minute minimum that would preserve true duplicates.
        // Nearby starts distinguish repeated news blocks even if their boundaries overlap by seconds.
        val startDistance = kotlin.math.abs(a.startMs - b.startMs)
        if (startDistance > minOf(shorter / 3, 10 * 60_000L) ||
            overlap.toDouble() / shorter < 0.8) return false
        // Generic titles often cover distinct episodes; conflicting non-empty synopsis is not proof
        // of the same listing. A missing synopsis alone must not defeat cross-feed deduplication.
        val aDescription = a.description?.let(::normalise)?.takeIf { it.isNotEmpty() }
        val bDescription = b.description?.let(::normalise)?.takeIf { it.isNotEmpty() }
        if (aDescription != null && bDescription != null && aDescription != bDescription) return false
        return true
    }

    /**
     * Titles as two feeds write them: different case, padding, and runs of whitespace where one has
     * wrapped a line. Nothing cleverer — a feed that renames a programme is not the same listing, and
     * guessing that it is would merge two real programmes into one.
     */
    private fun normalise(title: String): String =
        title.trim().lowercase(java.util.Locale.ROOT).replace(WHITESPACE, " ")

    private val EpgProgrammeEntity.durationMs: Long get() = (stopMs - startMs).coerceAtLeast(0L)

    private val WHITESPACE = Regex("\\s+")
}
