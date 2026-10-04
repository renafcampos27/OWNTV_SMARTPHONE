package tv.own.owntv.core.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class DecoderQueueingTest {
    @Test fun oldOrUnknownSettingUsesMedia3Default() {
        assertEquals(DecoderQueueing.AUTO, DecoderQueueing.fromStored(null))
        assertEquals(DecoderQueueing.AUTO, DecoderQueueing.fromStored("UNKNOWN"))
    }
    @Test fun everyStoredModeRoundTrips() {
        for (mode in DecoderQueueing.entries) assertEquals(mode, DecoderQueueing.fromStored(mode.name))
    }
}
