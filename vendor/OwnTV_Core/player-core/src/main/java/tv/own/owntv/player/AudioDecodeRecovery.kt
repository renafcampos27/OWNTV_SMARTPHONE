package tv.own.owntv.player

import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Tracks
import androidx.media3.exoplayer.ExoPlaybackException

/** One audio-only software rescue per user selection; rebuilds/reconnects keep the same budget. */
internal data class AudioDecodeRecovery(private var attempted: Boolean = false) {
    val softwareFallback: Boolean get() = attempted

    fun reset() { attempted = false }

    fun trySoftware(format: Format?, softwarePreferred: Boolean, supports: (String) -> Boolean): Boolean {
        val mime = format?.sampleMimeType ?: return false
        if (softwareFallback || softwarePreferred || format.cryptoType != C.CRYPTO_TYPE_NONE ||
            !MimeTypes.isAudio(mime) || !supports(mime)) return false
        attempted = true
        return true
    }
}

/** Network, DRM, sink and video failures are not evidence that an audio decoder should be replaced. */
internal fun audioDecoderFailureFormat(error: PlaybackException): Format? {
    val renderer = error as? ExoPlaybackException ?: return null
    if (!isAudioDecoderFailure(error) ||
        renderer.rendererName?.contains("ffmpeg", ignoreCase = true) == true) return null
    return renderer.rendererFormat?.takeIf { MimeTypes.isAudio(it.sampleMimeType) }
}

internal fun isAudioRendererFailure(error: PlaybackException): Boolean =
    (error as? ExoPlaybackException)?.rendererFormat?.let { MimeTypes.isAudio(it.sampleMimeType) } == true

internal fun isAudioDecoderFailure(error: PlaybackException): Boolean =
    (error as? ExoPlaybackException)?.type == ExoPlaybackException.TYPE_RENDERER &&
        error.errorCode in AUDIO_DECODE_ERRORS && isAudioRendererFailure(error)

/** Preserve a selected track first, then the configured language; never select a random other language. */
internal fun audioRecoveryFormat(tracks: Tracks, preferredLanguage: String): Format? {
    val audio = tracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }
    audio.forEach { group ->
        for (i in 0 until group.length) if (group.isTrackSelected(i)) return group.getTrackFormat(i)
    }
    val formats = audio.flatMap { group -> (0 until group.length).map(group::getTrackFormat) }
    return formats.firstOrNull { preferredLanguage.isNotBlank() && explicitTrackLanguageMatches(preferredLanguage, it.language) }
        ?: formats.firstOrNull()
}

private val AUDIO_DECODE_ERRORS = setOf(
    PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
    PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED,
    PlaybackException.ERROR_CODE_DECODING_FAILED,
    PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES,
    PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
)
