package tv.own.owntv.core.repository

import tv.own.owntv.core.database.entity.EpgHashProjection

internal sealed interface ProgrammeDecision {
    data object New : ProgrammeDecision
    data class Changed(val id: Long) : ProgrammeDecision
    data object Unchanged : ProgrammeDecision
    data object WriteThrough : ProgrammeDecision
}

/** Lazily snapshots channels, keeping at most [maxEntries] hashes, including this sync's new rows.
 * Oversized channels are written through and never partially used to prune existing programmes. */
internal class ProgrammeHashTracker(
    private val maxEntries: Int,
    private val load: suspend (channel: String, limit: Int) -> List<EpgHashProjection>,
) {
    init { require(maxEntries >= 0) }

    private val channels = HashMap<String, PrimitiveProgrammeHashes>()
    private val untracked = HashSet<String>()
    internal var trackedEntries = 0
        private set
    var overflowed = false
        private set

    suspend fun observe(channel: String, startMs: Long, hash: Int): ProgrammeDecision {
        if (channel in untracked) return ProgrammeDecision.WriteThrough
        val state = channels[channel] ?: run {
            val remaining = maxEntries - trackedEntries
            if (remaining == 0) return skipChannel(channel)
            // One extra row detects overflow without loading the complete oversized channel.
            val rows = load(channel, remaining + 1)
            if (rows.size > remaining) return skipChannel(channel)
            PrimitiveProgrammeHashes().also { snapshot ->
                rows.forEach { snapshot.put(it.startMs, it.id, it.contentHash, seen = false) }
                trackedEntries += snapshot.size
                channels[channel] = snapshot
            }
        }
        val slot = state.find(startMs)
        if (slot >= 0) {
            state.markSeen(slot)
            if (state.hash(slot) == hash) return ProgrammeDecision.Unchanged
            // Last changed duplicate wins, as it did before: seen does not mean unchanged.
            state.updateHash(slot, hash)
            return ProgrammeDecision.Changed(state.id(slot))
        }
        if (trackedEntries == maxEntries) {
            overflowed = true
            return ProgrammeDecision.WriteThrough
        }
        state.put(startMs, 0L, hash, seen = true)
        trackedEntries++
        return ProgrammeDecision.New
    }

    private fun skipChannel(channel: String): ProgrammeDecision {
        overflowed = true
        untracked.add(channel)
        return ProgrammeDecision.WriteThrough
    }

    fun staleTrackedIds(): List<Long> = buildList {
        channels.values.forEach { it.appendStaleIds(this) }
    }
}

/** Open addressing avoids a boxed Long/Pair/Int and a second seen set for every programme.
 * Occupancy is separate from the key, so zero and negative timestamps are valid. */
private class PrimitiveProgrammeHashes {
    private var starts = LongArray(8)
    private var ids = LongArray(8)
    private var hashes = IntArray(8)
    private var flags = ByteArray(8)
    var size = 0
        private set

    fun find(start: Long): Int {
        var slot = bucket(start)
        while (flags[slot].toInt() != 0) {
            if (starts[slot] == start) return slot
            slot = (slot + 1) and (starts.size - 1)
        }
        return -1
    }

    fun put(start: Long, id: Long, hash: Int, seen: Boolean) {
        if ((size + 1) * 4 >= starts.size * 3) grow()
        var slot = bucket(start)
        while (flags[slot].toInt() != 0 && starts[slot] != start) slot = (slot + 1) and (starts.size - 1)
        if (flags[slot].toInt() == 0) size++
        starts[slot] = start
        ids[slot] = id
        hashes[slot] = hash
        flags[slot] = if (seen) 2 else 1
    }

    fun markSeen(slot: Int) { flags[slot] = 2 }
    fun hash(slot: Int): Int = hashes[slot]
    fun id(slot: Int): Long = ids[slot]
    fun updateHash(slot: Int, hash: Int) { hashes[slot] = hash }

    fun appendStaleIds(target: MutableList<Long>) {
        flags.indices.forEach { if (flags[it].toInt() == 1 && ids[it] != 0L) target.add(ids[it]) }
    }

    private fun bucket(start: Long): Int {
        var mixed = start
        mixed = (mixed xor (mixed ushr 33)) * -49064778989728563L
        mixed = (mixed xor (mixed ushr 33)) * -4265267296055464877L
        return (mixed xor (mixed ushr 33)).toInt() and (starts.size - 1)
    }

    private fun grow() {
        val oldStarts = starts
        val oldIds = ids
        val oldHashes = hashes
        val oldFlags = flags
        starts = LongArray(oldStarts.size * 2)
        ids = LongArray(starts.size)
        hashes = IntArray(starts.size)
        flags = ByteArray(starts.size)
        size = 0
        oldFlags.indices.forEach { index ->
            if (oldFlags[index].toInt() != 0) put(oldStarts[index], oldIds[index], oldHashes[index], oldFlags[index].toInt() == 2)
        }
    }
}
