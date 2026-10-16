package tv.own.owntv.core.sync

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import tv.own.owntv.core.database.entity.ContentHashProjection

class PagedHashLookupTest {
    @Test fun pagesPreserveIdsHashesAndOrderWithoutOneFullProjectionList() = runBlocking {
        val source = (1L..1_003L).map { ContentHashProjection("channel-$it", it * 3, it.toInt(), (1_003 - it).toInt()) }
        var largest = 0
        var calls = 0
        val actual = loadPagedHashLookup({ after, limit ->
            calls++
            source.filter { it.id > after }.take(limit).also { largest = maxOf(largest, it.size) }
        }, 100)
        assertEquals(source.associate { it.remoteId to StoredRow(it.id, it.contentHash, it.sortOrder) }, actual)
        assertEquals(11, calls); assertEquals(100, largest)
    }

    @Test fun emptySourceAndDuplicateRemoteIdsKeepTheLastStoredRow() = runBlocking {
        assertTrue(loadPagedHashLookup({ _, _ -> emptyList() }).isEmpty())
        val source = listOf(ContentHashProjection("same", 1, 10, 2), ContentHashProjection("same", 3, 30, 4))
        val actual = loadPagedHashLookup({ after, limit -> source.filter { it.id > after }.take(limit) }, 1)
        assertEquals(StoredRow(3, 30, 4), actual["same"])
    }

    @Test fun nonAdvancingPageIsRejectedInsteadOfLoopingForever() = runBlocking {
        assertTrue(runCatching { loadPagedHashLookup({ _, _ -> listOf(ContentHashProjection("a", 1, 0, 0)) }, 1) }.isFailure)
    }
}
