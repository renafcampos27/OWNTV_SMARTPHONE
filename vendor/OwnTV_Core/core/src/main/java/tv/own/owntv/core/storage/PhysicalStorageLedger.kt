package tv.own.owntv.core.storage

/** Shared physical-space admission: quota ownership remains the separate StorageQuotaLedger. */
internal class PhysicalStorageLedger {
    private var free = 0L
    private var observedAt = Long.MIN_VALUE
    private var writtenSinceObservation = 0L
    private var committedTotal = 0L
    private val pending = mutableMapOf<String, Long>()
    private val scratch = mutableMapOf<String, Long>()

    @Synchronized fun needsObservation(now: Long): Boolean =
        observedAt == Long.MIN_VALUE || now - observedAt >= 500_000_000L

    /** Capture before querying the volume; commits can run while that blocking query is in flight. */
    @Synchronized fun observationTicket(): Long = committedTotal

    @Synchronized fun observe(bytesFree: Long, now: Long) = observe(bytesFree, now, committedTotal)

    @Synchronized fun observe(bytesFree: Long, now: Long, ticket: Long) {
        free = bytesFree.coerceAtLeast(0)
        observedAt = now
        // An old free-space read must not erase commits published after its capture began.
        // At the unreachable counter ceiling, refuse growth rather than wrap and admit space.
        writtenSinceObservation = if (committedTotal == Long.MAX_VALUE || ticket !in 0..committedTotal)
            Long.MAX_VALUE else committedTotal - ticket
    }

    @Synchronized fun reserve(owner: String, increase: Long, extra: Long, floor: Long): Boolean {
        require(increase >= 0 && extra >= 0)
        // Saturating sums avoid admission on overflow from a malformed expected size.
        fun sum(values: Collection<Long>) = values.fold(0L) { total, value ->
            if (value > Long.MAX_VALUE - total) Long.MAX_VALUE else total + value
        }
        val others = sum(pending.filterKeys { it != owner }.values)
        val otherScratch = sum(scratch.filterKeys { it != owner }.values)
        var available = (free - minOf(free, floor)).coerceAtLeast(0)
        for (bytes in listOf(writtenSinceObservation, others, otherScratch, increase, extra)) {
            if (bytes > available) return false
            available -= bytes
        }
        pending[owner] = increase
        scratch[owner] = extra
        return true
    }

    @Synchronized fun commit(owner: String, increase: Long) {
        val delta = increase.coerceAtLeast(0)
        committedTotal = if (delta > Long.MAX_VALUE - committedTotal) Long.MAX_VALUE
            else committedTotal + delta
        writtenSinceObservation = if (delta > Long.MAX_VALUE - writtenSinceObservation) Long.MAX_VALUE
            else writtenSinceObservation + delta
        pending[owner] = ((pending[owner] ?: 0) - delta).coerceAtLeast(0)
    }

    @Synchronized fun release(owner: String) {
        pending.remove(owner)
        scratch.remove(owner)
    }
}
