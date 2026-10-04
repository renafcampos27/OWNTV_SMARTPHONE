package tv.own.owntv.player

import androidx.media3.common.Format
import androidx.media3.exoplayer.ExoPlayer

/** Announced audio remains visible even when no renderer can select it. */
fun announcedAudioRow(tracks: androidx.media3.common.Tracks): StreamInfoRow? {
    val groups = tracks.groups.filter { it.type == androidx.media3.common.C.TRACK_TYPE_AUDIO }
    val group = groups.firstOrNull { it.isSelected } ?: groups.firstOrNull() ?: return null
    val index = (0 until group.length).firstOrNull { group.isTrackSelected(it) } ?: 0
    val format = group.getTrackFormat(index)
    return StreamInfoRow(StreamInfoLabel.AUDIO, StreamInfoValue.Audio(
        codec = format.sampleMimeType?.substringAfterLast('/')?.uppercase(),
        channelCount = format.channelCount.takeIf { it > 0 },
        sampleRateHz = format.sampleRate.takeIf { it > 0 },
        supported = group.isTrackSupported(index), selected = group.isTrackSelected(index),
    ))
}

/** Codec/rendition bitrate, independently of bursty HLS network transfers. */
fun bitrateRow(f: Format, throughputTracker: ThroughputTracker): StreamInfoRow =
    StreamInfoRow(StreamInfoLabel.BITRATE, StreamInfoValue.Bitrate(f.bitrate.takeIf { it > 0 }?.toLong() ?: 0L))

/** Network delivery rate is a separate statistic; zero between HLS requests is normal. */
fun networkRateRow(tracker: ThroughputTracker): StreamInfoRow? =
    if (tracker.hasMeasured) StreamInfoRow(StreamInfoLabel.DOWNLOAD_RATE, StreamInfoValue.Bitrate(tracker.bitsPerSecond)) else null

/** Buffered duration + dropped frames since [dropsBaseline]. We can't reset ExoPlayer's own drop
 *  counter, so callers snapshot it per item and we subtract instead. */
fun bufferRow(p: ExoPlayer, dropsBaseline: Int): StreamInfoRow? {
    val drops = p.videoDecoderCounters?.let { it.ensureUpdated(); (it.droppedBufferCount - dropsBaseline).coerceAtLeast(0).toLong() }
    val buffered = p.totalBufferedDuration.takeIf { it > 0 }
    return if (buffered != null || drops != null) {
        StreamInfoRow(StreamInfoLabel.BUFFER, StreamInfoValue.Buffer(bufferedMs = buffered, droppedFrames = drops))
    } else null
}

/** Snapshot of the current drop count, for [bufferRow]'s baseline. */
fun currentDroppedFrames(p: ExoPlayer?): Int {
    val counters = p?.videoDecoderCounters ?: return 0
    counters.ensureUpdated()
    return counters.droppedBufferCount
}
