package tv.own.owntv.player

import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector

/**
 * The one place every in-app ExoPlayer's renderers are configured.
 *
 * Three engines build an ExoPlayer — [LivePreviewEngine] (Live TV's default engine),
 * [ExoSubtitleEngine] (VOD / image-subtitle handoff / mpv fallback) and [HeroPreviewEngine] (the
 * home hero). Each used to configure its own, so a decode-path setting could land on one and not the
 * others: the async-queueing workaround below was live-only, and decoder fallback was on none of
 * them. Anything about *how the device decodes* belongs here so it cannot go missing on one engine
 * again. Per-engine concerns (data source, load control, track selector, listeners) stay at the
 * call site, where they legitimately differ.
 */
@UnstableApi
fun ownTVRenderers(
    context: Context,
    /** Pin the audio sink to stereo PCM — "Stereo only", or a session latch tripped by any engine. */
    forceStereo: Boolean,
    /** Software decoders first — "Hardware decoding = Off", or a rescue retry after a blank picture. */
    softwareFirst: Boolean = false,
    softwareAudio: Boolean = false,
    audioDelay: AudioDelayClock? = null,
    queueing: tv.own.owntv.core.settings.DecoderQueueing = tv.own.owntv.core.settings.DecoderQueueing.AUTO,
): DefaultRenderersFactory =
    OwnTVRenderersFactory(context, forceStereo = forceStereo, audioDelay = audioDelay, preferFfmpegAudio = softwareAudio)
        .apply {
            when (queueing) {
                tv.own.owntv.core.settings.DecoderQueueing.AUTO -> {} // Media3's device/API default.
                tv.own.owntv.core.settings.DecoderQueueing.ASYNCHRONOUS -> forceEnableMediaCodecAsynchronousQueueing()
                tv.own.owntv.core.settings.DecoderQueueing.SYNCHRONOUS -> forceDisableMediaCodecAsynchronousQueueing()
            }
            LiveDiagnosticsLog.event("decoder_queueing mode=$queueing api=${android.os.Build.VERSION.SDK_INT} softwareFirst=$softwareFirst softwareAudio=$softwareAudio")
        }
        // If the chosen decoder fails to initialise or throws while configuring, try the next one the
        // device offers (usually the software decoder) instead of surfacing the error. A free rung on
        // the rescue ladder: without it one bad vendor decoder ends playback outright.
        .setEnableDecoderFallback(true)
        .apply {
            if (softwareFirst || softwareAudio) {
                // Hardware stays in the list as a backstop — Media3 walks the decoder list in order
                // and falls through on failure, so this can only add a route.
                setMediaCodecSelector { mime, secure, tunneling ->
                    val decoders = MediaCodecSelector.DEFAULT.getDecoderInfos(mime, secure, tunneling)
                    decoders.sortedBy {
                        decoderPriority(mime, it.hardwareAccelerated, it.softwareOnly, softwareFirst, softwareAudio)
                    }
                }
            }
        }

/** Audio preference never changes video ordering; secure decoder filtering remains Media3's. */
internal fun preferSoftwareDecoder(mime: String, softwareFirst: Boolean, softwareAudio: Boolean): Boolean =
    softwareFirst || (softwareAudio && androidx.media3.common.MimeTypes.isAudio(mime))

internal fun decoderPriority(mime: String, hardware: Boolean, software: Boolean, softwareFirst: Boolean, softwareAudio: Boolean): Int =
    when {
        softwareAudio && androidx.media3.common.MimeTypes.isAudio(mime) -> if (software) 0 else 1
        preferSoftwareDecoder(mime, softwareFirst, softwareAudio) -> if (hardware) 1 else 0
        else -> 0 // Preserve platform ordering when no override applies.
    }
