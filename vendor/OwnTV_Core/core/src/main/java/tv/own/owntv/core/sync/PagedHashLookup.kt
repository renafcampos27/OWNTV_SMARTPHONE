package tv.own.owntv.core.sync

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import tv.own.owntv.core.database.entity.ContentHashProjection

/** Retains the authoritative lookup, but at most one bounded projection page while building it. */
internal suspend fun loadPagedHashLookup(
    load: suspend (Long, Int) -> List<ContentHashProjection>,
    pageSize: Int = 400,
): Map<String, StoredRow> {
    require(pageSize > 0)
    val result = HashMap<String, StoredRow>()
    var afterId = Long.MIN_VALUE
    while (true) {
        currentCoroutineContext().ensureActive()
        val page = load(afterId, pageSize)
        if (page.isEmpty()) break
        require(page.size <= pageSize)
        for (row in page) {
            require(row.id > afterId)
            afterId = row.id
            result[row.remoteId] = StoredRow(row.id, row.contentHash, row.sortOrder)
        }
        if (page.size < pageSize) break
    }
    return result
}
