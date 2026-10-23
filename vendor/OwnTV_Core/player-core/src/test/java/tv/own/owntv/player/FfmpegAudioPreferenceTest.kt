package tv.own.owntv.player

import org.junit.Assert.*
import org.junit.Test

class FfmpegAudioPreferenceTest {
    @Test fun automaticLeavesAdvertisedNativeFormatsToMediaCodec() {
        for (mime in listOf("audio/mp4a-latm", "audio/mpeg-L2", "audio/ac3")) {
            assertFalse(FfmpegAudio.takes(mime, false, { true }, { error("no FFmpeg capability query needed") }))
        }
    }

    @Test fun automaticKeepsFfmpegForFormatsWithoutANativeDecoder() {
        assertTrue(FfmpegAudio.takes("audio/mpeg-L2", false, { false }, { true }))
        assertFalse(FfmpegAudio.takes("audio/unknown", false, { false }, { false }))
    }

    @Test fun explicitSoftwareOrOwnedRescueCannotBeBlockedByANativeDeclaration() {
        for (mime in listOf("audio/mp4a-latm", "audio/mpeg-L2", "audio/ac3")) {
            assertTrue(FfmpegAudio.takes(mime, true, { error("manual choice does not depend on platform advertisement") }, { true }))
        }
    }

    @Test fun missingSoftwareCodecAndNonAudioRemainUnsupported() {
        assertFalse(FfmpegAudio.takes("audio/ac3", true, { true }, { false }))
        assertFalse(FfmpegAudio.takes("video/hevc", true, { error("not audio") }, { error("not audio") }))
    }
}
