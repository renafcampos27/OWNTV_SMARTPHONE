package tv.own.owntv.core.settings

import org.junit.Assert.*
import org.junit.Test
import tv.own.owntv.core.database.entity.SourceEntity
import tv.own.owntv.core.model.SourceType

class LiveReserveResolverTest {
    private val source = SourceEntity(name = "Test", type = SourceType.M3U, url = "https://example.invalid/list")

    @Test fun `inheritance ignores dormant source fields and keeps whole global pair`() {
        val global = LiveReserveConfiguration(18, 4)
        val inherited = source.copy(liveReserveCustomSecs = 5, liveReserveExtraSecs = 8)
        assertNull(LiveReserveResolver.sourceOverride(inherited))
        assertEquals(global, LiveReserveResolver.resolve(inherited, global))
    }

    @Test fun `missing custom value and margin resolve identically for UI and tuning`() {
        val partial = source.copy(liveReserveMode = "CUSTOM")
        assertEquals(LiveReserveConfiguration(8, 2), LiveReserveResolver.sourceOverride(partial))
        assertEquals(LiveReserveConfiguration(8, 2), LiveReserveResolver.resolve(partial, LiveReserveConfiguration(18, 4)))
    }

    @Test fun `explicit balanced remains an override even against custom global`() {
        val balanced = source.copy(liveReserveMode = "BALANCED")
        assertEquals(LiveReserveConfiguration(null, 2), LiveReserveResolver.resolve(balanced, LiveReserveConfiguration(18, 4)))
    }

    @Test fun `explicit ranges and stable preset retain their respective values`() {
        assertEquals(LiveReserveConfiguration(12, 5), LiveReserveResolver.sourceOverride(
            source.copy(liveReserveMode = "CUSTOM", liveReserveCustomSecs = 12, liveReserveExtraSecs = 5)))
        assertEquals(LiveReserveConfiguration(15, 2), LiveReserveResolver.sourceOverride(source.copy(liveReserveMode = "STABLE")))
    }
}
