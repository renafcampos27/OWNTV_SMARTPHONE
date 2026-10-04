package tv.own.owntv.core.customize

import org.junit.Assert.*
import org.junit.Test

class ChannelVersionCustomizationTest {
    @Test fun defaultsAreOffAndNewValuesSurviveSerializationWithHiddenItems() {
        assertFalse(CustomizationStore.parse("{}").groupChannelVersions)
        assertFalse(CustomizationStore.parse("{}").prioritizeChannelVersions)
        val expected = SectionCustomizations(groupChannelVersions = true, prioritizeChannelVersions = true,
            channelVersionOrders = mapOf("1:pt:rtp 1" to "[\"20\",\"10\"]"), hiddenItems = mapOf("1:20" to "RTP 1 HD"))
        assertEquals(expected, CustomizationStore.parse(CustomizationStore.serialize(expected)))
        assertFalse(expected.isEmpty)
    }

    @Test fun savedOrderAloneIsNotDiscardedWhenBothOptionsAreDisabled() {
        val expected = SectionCustomizations(channelVersionOrders = mapOf("2:sic" to "[\"b\",\"a\"]"))
        assertFalse(expected.isEmpty)
        assertEquals(expected, CustomizationStore.parse(CustomizationStore.serialize(expected)))
    }

    @Test fun manualGroupsAloneArePersistedWithoutEnablingGroupingOrPriority() {
        val expected = SectionCustomizations(channelVersionGroups = mapOf("2:123" to "2:sic"))
        assertFalse(expected.isEmpty)
        assertEquals(expected, CustomizationStore.parse(CustomizationStore.serialize(expected)))
    }
}
