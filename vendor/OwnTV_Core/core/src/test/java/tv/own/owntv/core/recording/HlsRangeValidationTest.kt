package tv.own.owntv.core.recording

import org.junit.Assert.*
import org.junit.Test

class HlsRangeValidationTest {
    private val range = HlsMediaPlaylist.ByteRange(188, 188)
    @Test fun onlyTheRequested206IntervalIsAccepted() {
        assertTrue(HlsRangeValidation.accepts(206, "bytes 188-375/564", range))
        assertTrue(HlsRangeValidation.accepts(206, "bytes 188-375/*", range))
        listOf(null, "bytes 0-187/564", "bytes 188-374/564", "bytes 188-375/375", "bytes 188-375/564 junk", "bytes 188-375/").forEach {
            assertFalse(HlsRangeValidation.accepts(206, it, range))
        }
        assertFalse(HlsRangeValidation.accepts(200, "bytes 188-375/564", range))
        assertFalse(HlsRangeValidation.accepts(206, "bytes 0-0/*", HlsMediaPlaylist.ByteRange(0, 0)))
    }
}
