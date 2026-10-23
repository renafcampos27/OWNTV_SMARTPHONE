package tv.own.owntv.player

import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.TrackGroup
import androidx.media3.common.Tracks
import androidx.media3.exoplayer.ExoPlaybackException
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class AudioDecodeRecoveryTest {
    private fun format(mime: String = MimeTypes.AUDIO_AAC, language: String = "pt") =
        Format.Builder().setSampleMimeType(mime).setLanguage(language).build()

    private fun failure(
        format: Format? = format(),
        name: String = "MediaCodecAudioRenderer",
        code: Int = PlaybackException.ERROR_CODE_DECODING_FAILED,
    ) = ExoPlaybackException.createForRenderer(
        IllegalStateException("decoder failure"), name, 1, format, C.FORMAT_HANDLED, null, false, code,
    )

    @Test fun oneSoftwareAttemptSurvivesSameItemRebuildsButNotTheNextSelection() {
        val current = ItemState()
        assertTrue(current.audioDecodeRecovery.trySoftware(format(), false) { true })
        assertTrue(current.audioDecodeRecovery.softwareFallback)
        assertNotEquals(ItemState(), current)
        assertFalse(current.audioDecodeRecovery.trySoftware(format(), false) { true })
        val next = ItemState()
        assertFalse(next.audioDecodeRecovery.softwareFallback)
        assertTrue(next.audioDecodeRecovery.trySoftware(format(), false) { true })
    }

    @Test fun manualSoftwareDoesNotSpendAnAutomaticRetryOnTheSameDecoder() {
        val recovery = AudioDecodeRecovery()
        assertFalse(recovery.trySoftware(format(), true) { error("already software") })
        assertFalse(recovery.softwareFallback)
    }

    @Test fun unsupportedCodecDoesNotSpendTheBudget() {
        val recovery = AudioDecodeRecovery()
        assertFalse(recovery.trySoftware(format(), false) { false })
        assertTrue(recovery.trySoftware(format(MimeTypes.AUDIO_MPEG_L2), false) { true })
    }

    @Test fun encryptedVideoAndMissingFormatsCannotTriggerSoftwareAudio() {
        val recovery = AudioDecodeRecovery()
        val encrypted = format().buildUpon().setCryptoType(C.CRYPTO_TYPE_FRAMEWORK).build()
        for (candidate in listOf(null, encrypted, format(MimeTypes.VIDEO_H264))) {
            assertFalse(recovery.trySoftware(candidate, false) { error("unsupported input") })
        }
        assertFalse(recovery.softwareFallback)
    }

    @Test fun explicitPreferenceChangeCanResetTheAttempt() {
        val recovery = AudioDecodeRecovery()
        recovery.trySoftware(format(), false) { true }
        recovery.reset()
        assertFalse(recovery.softwareFallback)
        assertTrue(recovery.trySoftware(format(), false) { true })
    }

    @Test fun onlyAnIdentifiedAudioDecoderFailureQualifies() {
        val error = failure()
        assertTrue(isAudioDecoderFailure(error))
        assertTrue(isAudioRendererFailure(error))
        assertEquals(MimeTypes.AUDIO_AAC, audioDecoderFailureFormat(error)?.sampleMimeType)
        assertNull(audioDecoderFailureFormat(failure(format(MimeTypes.VIDEO_H264), "MediaCodecVideoRenderer")))
        assertNull(audioDecoderFailureFormat(failure(null)))
    }

    @Test fun networkDrmAndOutputErrorsDoNotReplaceAudioDecoder() {
        val errors = listOf(
            ExoPlaybackException.createForSource(IOException("HTTP 403"), PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS),
            failure(code = PlaybackException.ERROR_CODE_DRM_LICENSE_ACQUISITION_FAILED),
            failure(code = PlaybackException.ERROR_CODE_AUDIO_TRACK_INIT_FAILED),
            failure(code = PlaybackException.ERROR_CODE_AUDIO_TRACK_WRITE_FAILED),
        )
        for (error in errors) {
            assertFalse(isAudioDecoderFailure(error))
            assertNull(audioDecoderFailureFormat(error))
        }
    }

    @Test fun failedFfmpegIsNotTreatedAsANativeDecoderNorAVideoFailure() {
        val error = failure(name = "FfmpegAudioRenderer")
        assertTrue(isAudioRendererFailure(error))
        assertTrue(isAudioDecoderFailure(error))
        assertNull(audioDecoderFailureFormat(error))
    }

    private fun group(vararg formats: Format, selected: Int = -1): Tracks.Group = Tracks.Group(
        TrackGroup(*formats), false, IntArray(formats.size) { C.FORMAT_HANDLED }, BooleanArray(formats.size) { it == selected },
    )

    @Test fun selectedTrackWinsOverDefaultLanguageDuringRecovery() {
        val tracks = Tracks(listOf(group(format(language = "pt")), group(format(MimeTypes.AUDIO_AC3, "en"), selected = 0)))
        assertEquals("en", audioRecoveryFormat(tracks, "pt")?.language)
    }

    @Test fun initialRecoveryUsesPreferredLanguageAndIgnoresVideoGroups() {
        val preferred = format(language = "pt-PT")
        val tracks = Tracks(listOf(group(format(MimeTypes.VIDEO_H264)), group(format(language = "en")), group(preferred)))
        assertSame(preferred, audioRecoveryFormat(tracks, "por"))
        assertNull(audioRecoveryFormat(Tracks.EMPTY, "pt"))
    }
}
