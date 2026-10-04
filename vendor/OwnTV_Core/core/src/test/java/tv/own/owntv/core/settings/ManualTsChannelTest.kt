package tv.own.owntv.core.settings

import org.junit.Assert.*
import org.junit.Test
import tv.own.owntv.core.database.entity.ChannelEntity

class ManualTsChannelTest {
    private val channel = ChannelEntity(sourceId = 7, remoteId = "123", name = "SIC HD", streamUrl = "https://example.invalid/private")
    @Test fun `identity survives channel rename and refresh but not source or version changes`() {
        val ref = ManualTsChannel.of(channel)
        assertTrue(ref.matches(channel.copy(id = 99, name = "Renamed")))
        assertFalse(ref.matches(channel.copy(sourceId = 8)))
        assertFalse(ref.matches(channel.copy(remoteId = "124")))
    }
    @Test fun `backup round trip remaps only known source identities and contains no stream URL`() {
        val raw = ManualTsChannel.encode(listOf(ManualTsChannel.of(channel)))
        assertFalse(raw.contains("https://"))
        val restored = ManualTsChannel.remap(ManualTsChannel.decode(raw), mapOf(7L to 42L))
        assertTrue(restored.single().matches(channel.copy(sourceId = 42)))
        assertTrue(ManualTsChannel.remap(ManualTsChannel.decode(raw), emptyMap()).isEmpty())
    }
    @Test fun `invalid settings are ignored and name fallback is restricted to channels without remote id`() {
        assertTrue(ManualTsChannel.decode("broken").isEmpty())
        val ref = ManualTsChannel(7, null, "SIC HD")
        assertTrue(ref.matches(channel.copy(remoteId = null)))
        assertFalse(ref.matches(channel))
    }
}
