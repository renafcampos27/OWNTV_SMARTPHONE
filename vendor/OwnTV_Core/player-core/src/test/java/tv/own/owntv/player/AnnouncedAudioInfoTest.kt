package tv.own.owntv.player

import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.TrackGroup
import androidx.media3.common.Tracks
import org.junit.Assert.*
import org.junit.Test

class AnnouncedAudioInfoTest {
    private fun group(mime: String, supported: Boolean, selected: Boolean) = Tracks.Group(
        TrackGroup(Format.Builder().setSampleMimeType(mime).setChannelCount(2).setSampleRate(48000).build()),
        false, intArrayOf(if (supported) C.FORMAT_HANDLED else C.FORMAT_UNSUPPORTED_TYPE), booleanArrayOf(selected),
    )

    @Test fun `unsupported MP2 stays visible instead of disappearing`() {
        val row = announcedAudioRow(Tracks(listOf(group(MimeTypes.AUDIO_MPEG_L2, false, false))))!!
        val value = row.value as StreamInfoValue.Audio
        assertEquals("MPEG-L2", value.codec)
        assertEquals(false, value.supported)
        assertEquals(false, value.selected)
    }

    @Test fun `selected audio takes precedence over an unsupported alternative`() {
        val row = announcedAudioRow(Tracks(listOf(
            group(MimeTypes.AUDIO_MPEG_L2, false, false), group(MimeTypes.AUDIO_AAC, true, true),
        )))!!
        assertEquals("MP4A-LATM", (row.value as StreamInfoValue.Audio).codec)
        assertEquals(true, row.value.supported)
    }

    @Test fun `no announced audio is not classified as incompatible`() {
        assertNull(announcedAudioRow(Tracks.EMPTY))
    }
}
