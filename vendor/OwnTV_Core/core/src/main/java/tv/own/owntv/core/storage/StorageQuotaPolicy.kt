package tv.own.owntv.core.storage

/** GiB values are persisted/UI-labelled consistently; these are product defaults, not Android rules. */
object StorageQuotaPolicy {
    const val GIB = 1024L * 1024 * 1024
    const val DEFAULT_INTERNAL_GIB = 4
    const val DEFAULT_EXTERNAL_GIB = 16
    fun reserve(total: Long, removable: Boolean): Long =
        maxOf(if (removable) GIB else 3 * GIB, total / if (removable) 20 else 10)
}

/** One lock arbitrates all owners; reserved bytes count before a writer touches disk. */
class StorageQuotaLedger {
    private val used = mutableMapOf<String, Long>()
    private val held = mutableSetOf<String>()
    @Synchronized fun reconcile(values: Map<String, Long>) {
        used.keys.filter { it !in held && it !in values }.toList().forEach { used.remove(it) }
        values.forEach { (key, bytes) -> if (key !in held) used[key] = bytes.coerceAtLeast(0) }
    }
    @Synchronized fun acquire(key: String) { check(held.add(key)) }
    @Synchronized fun total(): Long = used.values.fold(0L) { total, bytes ->
        if (Long.MAX_VALUE - total < bytes) Long.MAX_VALUE else total + bytes
    }
    @Synchronized fun grow(key: String, desired: Long, quota: Long): Boolean {
        check(key in held)
        require(desired >= 0)
        val previous = used[key] ?: 0
        if (desired > previous && desired - previous > (quota - total()).coerceAtLeast(0)) return false
        used[key] = desired
        return true
    }
    @Synchronized fun release(key: String, actual: Long) { used[key] = actual.coerceAtLeast(0); held.remove(key) }
}
