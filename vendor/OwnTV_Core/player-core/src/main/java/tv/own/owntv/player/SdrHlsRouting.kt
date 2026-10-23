package tv.own.owntv.player

import androidx.media3.common.MediaItem
import androidx.media3.common.C
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory

/** Optional routing; side-loaded subtitles deliberately retain the default merge path. */
@androidx.media3.common.util.UnstableApi
internal class SdrHlsRouting(
    private val main: DefaultMediaSourceFactory,
    private val sdrHls: androidx.media3.exoplayer.hls.HlsMediaSource.Factory,
    private val preferSdr: () -> Boolean,
) : androidx.media3.exoplayer.source.MediaSource.Factory {
    override fun setDrmSessionManagerProvider(
        provider: androidx.media3.exoplayer.drm.DrmSessionManagerProvider,
    ) = apply { main.setDrmSessionManagerProvider(provider); sdrHls.setDrmSessionManagerProvider(provider) }

    override fun setLoadErrorHandlingPolicy(
        policy: androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy,
    ) = apply { main.setLoadErrorHandlingPolicy(policy); sdrHls.setLoadErrorHandlingPolicy(policy) }

    override fun getSupportedTypes(): IntArray = main.supportedTypes

    override fun createMediaSource(item: MediaItem): androidx.media3.exoplayer.source.MediaSource {
        val local = item.localConfiguration
        val hls = local != null && androidx.media3.common.util.Util.inferContentTypeForUriAndMimeType(
            local.uri, local.mimeType,
        ) == C.CONTENT_TYPE_HLS
        return if (hls && preferSdr() && local.subtitleConfigurations.isEmpty()) sdrHls.createMediaSource(item)
        else main.createMediaSource(item)
    }
}

