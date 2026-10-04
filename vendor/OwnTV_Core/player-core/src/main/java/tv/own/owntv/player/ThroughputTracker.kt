package tv.own.owntv.player

import android.os.SystemClock
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener

/**
 * Live network throughput (bits/sec): bytes since [bitsPerSecond] was last read, divided by time since
 * then. Samples at most once per second; simultaneous overlays share the same sample. Starts disabled — call [setEnabled] to activate.
 */
class ThroughputTracker(private val clockMs: () -> Long = { SystemClock.elapsedRealtime() }) : TransferListener {
    private var pendingBytes = 0L
    private var lastReadMs = -1L
    private var lastRate = 0L
    @Volatile private var enabled = false
    @Volatile private var everTransferred = false

    /** True once any byte has been transferred while enabled — distinguishes "never measured" from
     *  "measured, currently 0". */
    val hasMeasured: Boolean
        get() = everTransferred

    val bitsPerSecond: Long
        get() = readAndReset()

    override fun onTransferInitializing(source: DataSource, dataSpec: DataSpec, isNetwork: Boolean) {}
    override fun onTransferStart(source: DataSource, dataSpec: DataSpec, isNetwork: Boolean) {}
    override fun onTransferEnd(source: DataSource, dataSpec: DataSpec, isNetwork: Boolean) {}

    override fun onBytesTransferred(source: DataSource, dataSpec: DataSpec, isNetwork: Boolean, bytesTransferred: Int) {
        if (!enabled || !isNetwork || bytesTransferred <= 0) return
        everTransferred = true
        synchronized(this) { pendingBytes += bytesTransferred }
    }

    @Synchronized
    private fun readAndReset(): Long {
        val now = clockMs()
        if (lastReadMs < 0L) {
            lastReadMs = now
            return 0L
        }
        val elapsedMs = now - lastReadMs
        if (elapsedMs < 1_000L) return lastRate // Multiple overlays share one sampled value.
        val bps = if (elapsedMs > 0) pendingBytes * 8_000 / elapsedMs else 0L
        pendingBytes = 0L
        lastReadMs = now
        lastRate = bps
        return bps
    }

    /** Enabling starts fresh rather than counting bytes from whenever tracking was last on. */
    fun setEnabled(enabled: Boolean) {
        this.enabled = enabled
        if (enabled) reset()
    }

    @Synchronized
    fun reset() {
        pendingBytes = 0L
        lastReadMs = clockMs()
        lastRate = 0L
        everTransferred = false
    }
}
