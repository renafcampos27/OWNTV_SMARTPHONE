package tv.own.owntv.mobile.ui.screens.live

import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test
import tv.own.owntv.core.customize.SectionCustomizations
import tv.own.owntv.core.database.entity.ChannelEntity

class ChannelVersionPolicyTest {
    private fun row(id: Long, name: String, source: Long = 1) = ChannelEntity(id = id,
        sourceId = source, remoteId = id.toString(), name = name, streamUrl = "https://example.invalid/$id")
    private val automatic = SectionCustomizations(prioritizeChannelVersions = true, groupChannelVersions = true)

    @Test fun hiddenVersionsLeaveBothPriorityAndGroupedRowsButKeepTheirSavedPosition() {
        val rows = listOf(row(1, "SIC Full HD"), row(2, "SIC HD"))
        val settings = automatic.copy(hiddenItems = mapOf("1:1" to "SIC Full HD"),
            channelVersionOrders = mapOf(ChannelVersionPolicy.groupKey(rows.first()) to "[\"1\",\"2\"]"))
        assertEquals(listOf(rows[1]), ChannelVersionPolicy.ordered(rows, settings))
        assertEquals(listOf(rows[1]), ChannelVersionPolicy.grouped(rows, settings))
        assertEquals(rows, ChannelVersionPolicy.ordered(rows, settings.copy(hiddenItems = emptyMap())))
        assertTrue(ChannelVersionPolicy.grouped(rows, settings.copy(hiddenItems = mapOf("1:1" to "", "1:2" to ""))).isEmpty())
    }

    @Test fun userOrderAndCombinedSuffixesAreRespected() {
        val rows = listOf(row(1, "PT: RTP 1 Low"), row(2, "PT: RTP 1"), row(3, "PT: RTP 1 HD"),
            row(4, "PT: RTP 1 hvec"), row(5, "PT: RTP 1 Full HD HEVC"))
        assertEquals(listOf(5L, 4L, 3L, 2L, 1L), ChannelVersionPolicy.ordered(rows, automatic).map { it.id })
        assertEquals(1, rows.map(ChannelVersionPolicy::groupKey).distinct().size)
    }

    @Test fun manualOrderSurvivesNewLocalIdsAndMissingHiddenVersions() {
        val first = row(1, "SIC HD")
        val settings = automatic.copy(channelVersionOrders = mapOf(ChannelVersionPolicy.groupKey(first) to JSONArray(listOf("2", "1")).toString()))
        val replacement = first.copy(id = 999)
        assertEquals(listOf("2", "1"), ChannelVersionPolicy.ordered(listOf(replacement, row(2, "SIC Low")), settings).map { it.remoteId })
        assertEquals(listOf(replacement), ChannelVersionPolicy.ordered(listOf(replacement), settings))
    }

    @Test fun groupsNeverCrossSourcesCountryOrChannelNumber() {
        val rows = listOf(row(1, "PT: RTP 1 HD"), row(2, "PT: RTP 1"), row(3, "PT: RTP 2 HD"),
            row(4, "BR: RTP 1"), row(5, "PT: RTP 1", 2))
        assertEquals(4, ChannelVersionPolicy.grouped(rows, automatic).size)
        assertEquals(rows, ChannelVersionPolicy.grouped(rows, automatic.copy(groupChannelVersions = false)))
    }

    @Test fun disabledPriorityPreservesExistingOrderDespiteSavedManualOrder() {
        val rows = listOf(row(1, "SIC Low"), row(2, "SIC Full HD"))
        val settings = automatic.copy(prioritizeChannelVersions = false,
            channelVersionOrders = mapOf(ChannelVersionPolicy.groupKey(rows.first()) to "[\"2\",\"1\"]"))
        assertEquals(rows, ChannelVersionPolicy.ordered(rows, settings))
    }

    @Test fun manualAssociationGroupsDifferentNamesAndSurvivesNewDatabaseIds() {
        val owner = row(1, "SIC HD")
        val candidate = row(2, "Portugal Entertainment HEVC")
        val group = ChannelVersionPolicy.groupKey(owner)
        val config = automatic.copy(channelVersionGroups = mapOf("1:2" to group),
            channelVersionOrders = mapOf(group to "[\"2\",\"1\"]"))
        assertEquals(group, ChannelVersionPolicy.groupKey(candidate.copy(id = 999), config))
        assertEquals(listOf(candidate), ChannelVersionPolicy.grouped(listOf(owner, candidate), config))
        assertEquals(listOf(owner), ChannelVersionPolicy.grouped(listOf(owner, candidate), config.copy(hiddenItems = mapOf("1:2" to candidate.name))))
    }

    @Test fun invalidManualAssociationCannotCrossPlaylistOrEraseGroupIdentity() {
        val owner = row(1, "SIC HD")
        for (group in listOf("2:sic", "1:", "1:   ")) {
            assertEquals(ChannelVersionPolicy.groupKey(owner), ChannelVersionPolicy.groupKey(owner,
                automatic.copy(channelVersionGroups = mapOf("1:1" to group))))
        }
    }
}
