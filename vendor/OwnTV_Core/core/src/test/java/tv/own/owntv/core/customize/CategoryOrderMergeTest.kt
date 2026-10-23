package tv.own.owntv.core.customize

import org.junit.Assert.assertEquals
import org.junit.Test

class CategoryOrderMergeTest {
    @Test fun `reordering two playlists preserves both orders on return`() {
        val first = mergeCategoryOrder(emptyList(), listOf("1:b", "1:a", "1:c"))
        val second = mergeCategoryOrder(first, listOf("2:y", "2:x"))
        val back = mergeCategoryOrder(second, listOf("1:c", "1:b", "1:a"))
        assertEquals(listOf("1:c", "1:b", "1:a"), back.filter { it.startsWith("1:") })
        assertEquals(listOf("2:y", "2:x"), back.filter { it.startsWith("2:") })
    }

    @Test fun `a shared category does not jump before another playlist's folders`() {
        val merged = mergeCategoryOrder(listOf("1:a", "1:b", "custom:shared"), listOf("custom:shared", "2:y", "2:x"))
        assertEquals(listOf("1:a", "1:b", "custom:shared"), merged.filter { it.startsWith("1:") || it == "custom:shared" })
        assertEquals(listOf("custom:shared", "2:y", "2:x"), merged.filter { it.startsWith("2:") || it == "custom:shared" })
    }

    @Test fun `hidden or filtered categories stay stored while visible keys take their new order`() {
        assertEquals(
            listOf("hidden:a", "1:c", "1:a", "2:x", "hidden:b"),
            mergeCategoryOrder(listOf("hidden:a", "1:a", "2:x", "1:c", "hidden:b"), listOf("1:c", "1:a")),
        )
    }

    @Test fun `empty visible rail retains saved choices and duplicate keys are removed`() {
        assertEquals(listOf("1:a", "2:b"), mergeCategoryOrder(listOf("1:a", "1:a", "2:b"), emptyList()))
        assertEquals(listOf("1:b", "1:a", "2:b"), mergeCategoryOrder(listOf("1:a", "2:b", "1:a"), listOf("1:b", "1:b", "1:a")))
    }

    @Test fun `backup round trip keeps order hidden state and version preferences`() {
        val before = SectionCustomizations(
            categoryOrder = mergeCategoryOrder(listOf("1:b", "1:a"), listOf("2:y", "2:x")),
            hiddenCategories = setOf("1:a"),
            prioritizeChannelVersions = true,
            channelVersionOrders = mapOf("1:rtp" to "1:full,1:hd"),
        )
        assertEquals(before, CustomizationStore.parse(CustomizationStore.serialize(before)))
    }
}
