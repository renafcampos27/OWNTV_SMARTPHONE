package tv.own.owntv.core.sync.work

/** Atomic handoff between a queue drain and its last WorkManager completion window. */
internal class DrainWakeupGate {
    enum class Enqueue { NONE, KEEP, SUCCESSOR }
    private enum class Phase { UNKNOWN, ACTIVE, CLOSING }
    private var phase = Phase.UNKNOWN
    private var owner: String? = null
    private var revision = 0L
    private var successor = false

    @Synchronized fun needsWorkSnapshot(): Boolean = phase == Phase.UNKNOWN && !successor
    @Synchronized fun request(unfinishedWork: Boolean): Enqueue {
        revision++
        if (phase == Phase.ACTIVE) return Enqueue.NONE
        if (successor) return Enqueue.NONE
        successor = true
        return if (phase == Phase.CLOSING || unfinishedWork) Enqueue.SUCCESSOR else Enqueue.KEEP
    }
    @Synchronized fun begin(id: String): Long {
        owner = id
        phase = Phase.ACTIVE
        successor = false
        return revision
    }
    @Synchronized fun currentRevision(): Long = revision
    @Synchronized fun close(id: String, expected: Long): Boolean {
        if (owner != id) return true // A replacement worker owns the drain now.
        if (revision != expected) return false
        phase = Phase.CLOSING
        return true
    }
    @Synchronized fun failed(id: String) {
        if (owner == id && phase == Phase.ACTIVE) {
            phase = Phase.UNKNOWN
            successor = false
        }
    }
    @Synchronized fun enqueueFailed() {
        successor = false
        if (phase != Phase.ACTIVE) phase = Phase.UNKNOWN
    }
}
