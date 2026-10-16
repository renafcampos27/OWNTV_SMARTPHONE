package tv.own.owntv.core.recording

import tv.own.owntv.core.database.entity.RecordingEntity
import tv.own.owntv.core.model.RecordingStatus
import kotlin.math.ceil

data class RecordingSpaceForecast(
    val lowerBytes: Long, val upperBytes: Long, val availableBytes: Long?,
    val measured: Boolean, val reservedBytes: Long,
) {
    val mayExceedSpace: Boolean get() = availableBytes?.let { upperBytes > it } == true
}

/** Advisory only: no stream probe and no admission decision based on uncertain bitrate. */
object RecordingSpaceEstimator {
    private val excluded = setOf(RecordingStatus.FAILED, RecordingStatus.CANCELLED, RecordingStatus.MISSED)
    fun estimate(durationMs: Long, sourceId: Long, channelId: Long, history: List<RecordingEntity>,
        storage: RecordingStorageInfo, excludeId: Long = 0): RecordingSpaceForecast {
        val rates = history.asSequence().filter {
            it.sourceId == sourceId && it.channelId == channelId && it.bytes > 0 &&
                it.capturedDurationMs >= 30_000 && it.status !in excluded
        }.sortedByDescending { it.updatedAt }.take(5)
            .map { it.bytes.toDouble() * 8_000 / it.capturedDurationMs }
            .filter { it.isFinite() && it in 64_000.0..200_000_000.0 }.sorted().toList()
        val rate = if (rates.isEmpty()) null else rates[rates.size / 2]
        fun bytes(ms: Long, bps: Double): Long = ceil(ms.coerceAtLeast(0).toDouble() * bps / 8_000)
            .coerceIn(0.0, Long.MAX_VALUE.toDouble()).toLong()
        val low = bytes(durationMs, rate?.times(0.7) ?: 2_000_000.0)
        val high = bytes(durationMs, rate?.times(1.5) ?: 40_000_000.0)
        var reserved = 0L
        for (row in history) {
            if (row.id == excludeId || row.status != RecordingStatus.SCHEDULED && row.status != RecordingStatus.RECORDING) continue
            val ownRate = if (row.sourceId == sourceId && row.channelId == channelId && rate != null) rate * 1.5 else 40_000_000.0
            val remaining = (bytes(row.stopMs - row.startMs, ownRate) - row.bytes).coerceAtLeast(0)
            reserved = if (Long.MAX_VALUE - reserved < remaining) Long.MAX_VALUE else reserved + remaining
        }
        return RecordingSpaceForecast(low, high,
            if (storage.known) (storage.writableBytes - reserved).coerceAtLeast(0) else null,
            rate != null, reserved)
    }
}
