package tv.own.owntv.player

import androidx.media3.common.util.UnstableApi
import androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory
import tv.own.owntv.core.settings.ChannelPlaybackOptions

/** Explicit channel exceptions only. Null and false preserve the existing HLS defaults. */
@UnstableApi
internal data class HlsCompatibility(
    val detectAccessUnits: Boolean = false,
    val allowNonIdrKeyframes: Boolean = false,
    val prepareFromSegments: Boolean = false,
) {
    val extractorFlags: Int get() =
        (if (detectAccessUnits) DefaultTsPayloadReaderFactory.FLAG_DETECT_ACCESS_UNITS else 0) or
        (if (allowNonIdrKeyframes) DefaultTsPayloadReaderFactory.FLAG_ALLOW_NON_IDR_KEYFRAMES else 0)

    companion object {
        fun from(options: ChannelPlaybackOptions?) = HlsCompatibility(
            options?.hlsDetectAccessUnits == true,
            options?.hlsAllowNonIdrKeyframes == true,
            options?.hlsPrepareFromSegments == true,
        )
    }
}
