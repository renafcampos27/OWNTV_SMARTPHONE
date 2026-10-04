package tv.own.owntv.player

import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.common.Timeline
import androidx.media3.exoplayer.analytics.AnalyticsListener
import org.junit.Assert.*
import org.junit.Test

class AudioRecoveryTest {
    private val event = AnalyticsListener.EventTime(0, Timeline.EMPTY, 0, null, 0, Timeline.EMPTY, 0, null, 0, 0)
    @Test fun actualSinkFailureStillTriggersRecoveryWithoutPassthrough() {
        val watchdog = AudioWatchdog(canRecoverUnderrun = { false })
        watchdog.onAudioSinkError(event, IllegalStateException("sink failed"))
        assertNotNull(watchdog.poll(true))
        assertNull(watchdog.poll(true))
    }
    @Test fun decodedAudioUnderrunsDoNotTriggerStereoRecovery() {
        val watchdog = AudioWatchdog()
        repeat(8) { watchdog.onAudioUnderrun(event, 1024, 100, 50) }
        assertNull(watchdog.poll(true))
    }
    @Test fun starvingMediaDoesNotTriggerPassthroughRecovery() {
        val watchdog = AudioWatchdog(canRecoverUnderrun = { false })
        watchdog.onAudioTrackInitialized(event, config(C.ENCODING_AC3))
        watchdog.onAudioPositionAdvancing(event, 0)
        repeat(8) { watchdog.onAudioUnderrun(event, 1024, 100, 50) }
        assertNull(watchdog.poll(true))
    }
    @Test fun repeatedPassthroughUnderrunsWithDataTriggerOnlyOnce() {
        val watchdog = AudioWatchdog(canRecoverUnderrun = { true })
        watchdog.onAudioTrackInitialized(event, config(C.ENCODING_AC3))
        watchdog.onAudioPositionAdvancing(event, 0)
        repeat(4) { watchdog.onAudioUnderrun(event, 1024, 100, 50) }
        assertNotNull(watchdog.poll(true))
        repeat(4) { watchdog.onAudioUnderrun(event, 1024, 100, 50) }
        assertNull(watchdog.poll(true))
    }

    private fun config(encoding: Int) = AudioSink.AudioTrackConfig(encoding, 48_000, 12, false, false, 4096)
    private val format = Format.Builder().setSampleMimeType("audio/mp4a-latm").setSampleRate(48_000).setChannelCount(2).build()

    @Test fun reusedOutputDoesNotWaitForAnotherPositionAdvance() {
        var now = 1L
        val watchdog = AudioWatchdog(nowMs = { now })
        watchdog.onAudioInputFormatChanged(event, format, null)
        watchdog.onAudioTrackInitialized(event, config(C.ENCODING_PCM_16BIT))
        watchdog.onAudioPositionAdvancing(event, 0)
        watchdog.onAudioInputFormatChanged(event, format.buildUpon().setAverageBitrate(256_000).build(), null)
        repeat(10) { now += 1000; assertNull(watchdog.poll(true)) }
        assertTrue(watchdog.hasAdvanced)
        assertFalse(watchdog.passthrough)
    }

    @Test fun actualNewOutputWithoutAdvanceStillTriggersNoSound() {
        var now = 1L
        val watchdog = AudioWatchdog(nowMs = { now })
        watchdog.onAudioTrackInitialized(event, config(C.ENCODING_PCM_16BIT))
        watchdog.onAudioPositionAdvancing(event, 0)
        watchdog.onAudioTrackInitialized(event, config(C.ENCODING_AC3))
        assertFalse(watchdog.hasAdvanced)
        assertTrue(watchdog.passthrough)
        assertNull(watchdog.poll(true))
        repeat(6) { now += 1000; if (it < 5) assertNull(watchdog.poll(true)) else assertNotNull(watchdog.poll(true)) }
        assertNull(watchdog.poll(true))
    }

    @Test fun missingDecoderCallbackDoesNotMisclassifyPcmAsPassthrough() {
        val watchdog = AudioWatchdog(canRecoverUnderrun = { true })
        watchdog.onAudioTrackInitialized(event, config(C.ENCODING_PCM_16BIT))
        watchdog.onAudioPositionAdvancing(event, 0)
        repeat(8) { watchdog.onAudioUnderrun(event, 4096, 80, 100) }
        assertFalse(watchdog.passthrough)
        assertNull(watchdog.poll(true))
    }

    @Test fun isolatedUnderrunsMinutesApartDoNotRestartHealthyOutput() {
        var now = 1L
        val watchdog = AudioWatchdog(canRecoverUnderrun = { true }, nowMs = { now })
        watchdog.onAudioTrackInitialized(event, config(C.ENCODING_AC3))
        watchdog.onAudioPositionAdvancing(event, 0)
        repeat(12) {
            now += 120_000
            watchdog.onAudioUnderrun(event, 4096, 80, 200)
            assertNull(watchdog.poll(true))
        }
    }

    @Test fun delayedReleaseOfEqualOldConfigurationDoesNotDisarmReplacement() {
        val watchdog = AudioWatchdog()
        watchdog.onAudioTrackInitialized(event, config(C.ENCODING_PCM_16BIT))
        watchdog.onAudioTrackInitialized(event, config(C.ENCODING_PCM_16BIT))
        watchdog.onAudioPositionAdvancing(event, 0)
        watchdog.onAudioTrackReleased(event, config(C.ENCODING_PCM_16BIT))
        assertTrue(watchdog.hasAdvanced)
        watchdog.onAudioTrackReleased(event, config(C.ENCODING_PCM_16BIT))
        assertFalse(watchdog.hasAdvanced)
    }

    @Test fun rejectedOldSourceCannotRaiseAnAudioFailure() {
        val watchdog = AudioWatchdog(accepts = { false }, canRecoverUnderrun = { true })
        watchdog.onAudioTrackInitialized(event, config(C.ENCODING_AC3))
        watchdog.onAudioPositionAdvancing(event, 0)
        watchdog.onAudioSinkError(event, IllegalStateException("old source"))
        repeat(8) { watchdog.onAudioUnderrun(event, 4096, 80, 100) }
        assertFalse(watchdog.hasAdvanced)
        assertNull(watchdog.poll(true))
    }

    @Test fun resetDoesNotCarryPlaybackEvidenceIntoANewTune() {
        val watchdog = AudioWatchdog()
        watchdog.onAudioTrackInitialized(event, config(C.ENCODING_AC3))
        watchdog.onAudioPositionAdvancing(event, 0)
        watchdog.reset()
        assertFalse(watchdog.hasAdvanced)
        assertFalse(watchdog.passthrough)
        assertNull(watchdog.audioFormat)
    }
}
