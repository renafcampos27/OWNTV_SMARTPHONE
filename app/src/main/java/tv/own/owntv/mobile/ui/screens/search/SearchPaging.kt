package tv.own.owntv.mobile.ui.screens.search

internal object SearchPaging {
    const val PAGE = 40
    const val MAX = 1_000

    fun nextLimit(page: SearchPageState, query: String, filter: SearchFilter, requestedLimit: Int): Int? {
        if (page.loading || page.failed || page.query != query.trim() || page.filter != filter ||
            page.limit != requestedLimit || !page.results.hasMore || requestedLimit >= MAX) return null
        return (requestedLimit + PAGE).coerceAtMost(MAX)
    }
}
