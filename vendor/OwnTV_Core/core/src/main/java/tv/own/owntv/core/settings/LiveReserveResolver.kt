package tv.own.owntv.core.settings

import tv.own.owntv.core.database.entity.SourceEntity

/** One interpretation of a playlist override, shared by settings and the playback request. */
object LiveReserveResolver {
    fun sourceOverride(source: SourceEntity?): LiveReserveConfiguration? {
        val mode = source?.liveReserveMode ?: return null
        return LiveReserveConfiguration(
            LiveBuffer.effectiveSeconds(LiveLatency.fromName(mode),
                source.liveReserveCustomSecs.takeIf { it >= LiveBuffer.CUSTOM_MIN } ?: LiveBuffer.CUSTOM_DEFAULT),
            source.liveReserveExtraSecs.takeIf { it >= 0 }?.let(LiveBuffer::clampExtra)
                ?: LiveBuffer.DEFAULT_EXTRA_SECS,
        )
    }

    fun resolve(source: SourceEntity?, global: LiveReserveConfiguration): LiveReserveConfiguration =
        sourceOverride(source) ?: global
}
