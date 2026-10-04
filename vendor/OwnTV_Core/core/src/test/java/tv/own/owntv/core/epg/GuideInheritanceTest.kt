package tv.own.owntv.core.epg

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import tv.own.owntv.core.customize.CustomizeKeys
import tv.own.owntv.core.customize.SectionCustomizations
import tv.own.owntv.core.database.entity.ChannelEntity

class GuideInheritanceTest {
    private val channel = ChannelEntity(id = 8, sourceId = 2, remoteId = "123", name = "SIC HD", streamUrl = "https://example.invalid/live/123", epgChannelId = "sic.hd", catchup = true)
    @Test fun ownGuideWinsAndReappearsWithoutRemovingAssociation() = runBlocking {
        assertEquals("own", GuideInheritance.choose("own", "shared") { it == "own" })
        assertEquals("shared", GuideInheritance.choose("own", "shared") { it == "shared" })
        assertEquals("own", GuideInheritance.choose("own", "shared") { true })
    }
    @Test fun missingAlternateDoesNotErasePrimaryIdentity() = runBlocking {
        assertEquals("own", GuideInheritance.choose("own", "shared") { false })
        assertNull(GuideInheritance.choose(null, "shared") { false })
        assertEquals("shared", GuideInheritance.choose(null, "shared") { true })
    }
    @Test fun unconfiguredChannelDoesNotAddDatabaseProbes() = runBlocking {
        assertEquals("own", GuideInheritance.choose("own", null) { error("Unexpected probe") })
        assertEquals("own", GuideInheritance.choose("own", "own") { error("Unexpected probe") })
    }
    @Test fun approvedGuideCannotLeakToAnotherAccountWithSameRemoteId() {
        val custom = SectionCustomizations(epgFallbacks = mapOf(CustomizeKeys.channel(channel) to "sic"))
        assertEquals("sic", GuideInheritance.fallback(channel, custom))
        assertNull(GuideInheritance.fallback(channel.copy(sourceId = 3), custom))
        assertEquals(8L, channel.id)
        assertEquals(2L, channel.sourceId)
        assertEquals("123", channel.remoteId)
        assertEquals("https://example.invalid/live/123", channel.streamUrl)
    }
    @Test fun manualPrimaryAndDestinationShiftArePreserved() {
        val key = CustomizeKeys.channel(channel)
        val custom = SectionCustomizations(epgMatches = mapOf(key to "Own.Guide"), epgFallbacks = mapOf(key to " Shared.Guide "), epgShifts = mapOf(key to "60"))
        assertEquals("own.guide", GuideInheritance.primary(channel, custom))
        assertEquals("shared.guide", GuideInheritance.fallback(channel, custom))
        assertEquals(60, EpgShift.minutesFor(custom, channel, 120))
        assertEquals(3_600_000L, EpgShift.toStored(7_200_000L, 60))
    }
    @Test fun removingAssociationRestoresOrdinaryLookup() {
        val custom = SectionCustomizations(epgFallbacks = mapOf(CustomizeKeys.channel(channel) to "sic"))
        assertEquals("sic.hd", GuideInheritance.primary(channel, custom.copy(epgFallbacks = emptyMap())))
        assertNull(GuideInheritance.fallback(channel, custom.copy(epgFallbacks = emptyMap())))
        assertFalse(custom.isEmpty)
    }
}
