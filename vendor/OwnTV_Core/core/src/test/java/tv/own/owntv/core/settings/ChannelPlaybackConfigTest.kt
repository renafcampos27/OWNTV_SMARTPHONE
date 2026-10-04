package tv.own.owntv.core.settings

import org.junit.Assert.*
import org.junit.Test
import tv.own.owntv.core.player.EnginePreference

class ChannelPlaybackConfigTest {
    private val ref = ManualTsChannel(7, "100", "Odisseia HD")
    @Test fun `round trip preserves explicit zero false and every override`() {
        val options = ChannelPlaybackOptions(EnginePreference.EXO_ONLY, ChannelStreamFormat.HLS, 12, 3, 0, 10, false, 0)
        val row = ChannelPlaybackConfig(ref, options)
        assertEquals(listOf(row), ChannelPlaybackConfig.decode(ChannelPlaybackConfig.encode(listOf(row))))
        assertFalse(options.isDefault)
        assertEquals(emptyList<ChannelPlaybackConfig>(), ChannelPlaybackConfig.decode(ChannelPlaybackConfig.encode(listOf(row.copy(options = ChannelPlaybackOptions())))))
    }
    @Test fun `invalid backup values cannot create an invalid buffer configuration`() {
        val bounded = ChannelPlaybackOptions(reserveSecs = -100, extraSecs = 999, prerollSecs = 999, latencySecs = -3, audioDelayMs = 999999).normalized()
        assertEquals(ChannelPlaybackOptions(reserveSecs = 1, extraSecs = 10, prerollSecs = 10, latencySecs = 1, audioDelayMs = 5000), bounded)
        val load = LiveBuffer.loadControlFor(bounded.reserveSecs, bounded.prerollSecs!!, bounded.extraSecs!!)
        assertTrue(load.minBufferMs >= load.bufferForPlaybackMs)
        assertTrue(load.maxBufferMs >= load.minBufferMs)
        assertTrue(ChannelPlaybackConfig.decode("broken").isEmpty())
    }
    @Test fun `legacy TS choices migrate without overriding newer channel options`() {
        val migrated = ChannelPlaybackConfig.includeLegacy(emptyList(), listOf(ref)).single()
        assertEquals(ChannelStreamFormat.TS, migrated.options.format)
        val newer = ChannelPlaybackConfig(ref, ChannelPlaybackOptions(format = ChannelStreamFormat.HLS))
        assertEquals(listOf(newer), ChannelPlaybackConfig.includeLegacy(listOf(newer), listOf(ref)))
    }
    @Test fun `restoration remaps source identity and ignores unknown sources`() {
        val row = ChannelPlaybackConfig(ref, ChannelPlaybackOptions(reserveSecs = 15))
        val restored = ChannelPlaybackConfig.remap(listOf(row), mapOf(7L to 42L)).single()
        assertEquals(42L, restored.channel.sourceId)
        assertEquals(row.options, restored.options)
        assertTrue(ChannelPlaybackConfig.remap(listOf(row), emptyMap()).isEmpty())
    }
    @Test fun `only an explicit channel mpv choice bypasses the inherited strict HLS engine`() {
        val defaults = ChannelPlaybackOptions(engine = EnginePreference.MPV_ONLY)
        assertTrue(defaults.hlsOnly(true))
        assertEquals(EnginePreference.MPV_ONLY, defaults.effectiveEngine(EnginePreference.MPV_FIRST, defaults.hlsOnly(true), false))
        assertEquals(EnginePreference.EXO_ONLY, ChannelPlaybackOptions().effectiveEngine(EnginePreference.MPV_ONLY, true, false))
        assertEquals(EnginePreference.EXO_ONLY, defaults.copy(engine = EnginePreference.MPV_FIRST).effectiveEngine(EnginePreference.MPV_ONLY, true, false))
        val automatic = defaults.copy(format = ChannelStreamFormat.AUTO)
        assertFalse(automatic.hlsOnly(true))
        assertEquals(EnginePreference.MPV_ONLY, automatic.effectiveEngine(EnginePreference.EXO_ONLY, automatic.hlsOnly(true), false))
        val ts = defaults.copy(format = ChannelStreamFormat.TS)
        assertFalse(ts.hlsOnly(true))
        assertEquals(EnginePreference.MPV_ONLY, ts.effectiveEngine(EnginePreference.EXO_FIRST, false, true))
    }
    @Test fun `HLS compatibility options preserve explicit false and default older backups to null`() {
        val options = ChannelPlaybackOptions(hlsDetectAccessUnits = true, hlsAllowNonIdrKeyframes = false, hlsPrepareFromSegments = true)
        val row = ChannelPlaybackConfig(ref, options)
        assertEquals(listOf(row), ChannelPlaybackConfig.decode(ChannelPlaybackConfig.encode(listOf(row))))
        val old = ChannelPlaybackConfig.decode("""[{"channel":{"sourceId":7,"remoteId":"100","name":"Odisseia HD"},"options":{"reserveSecs":12}}]""").single()
        assertNull(old.options.hlsDetectAccessUnits)
        assertNull(old.options.hlsAllowNonIdrKeyframes)
        assertNull(old.options.hlsPrepareFromSegments)
    }

}
