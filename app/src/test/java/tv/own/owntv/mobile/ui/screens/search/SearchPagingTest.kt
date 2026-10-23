package tv.own.owntv.mobile.ui.screens.search

import org.junit.Assert.*
import org.junit.Test
import tv.own.owntv.core.content.SearchResults

class SearchPagingTest {
    private val page = SearchPageState(query = "sic", filter = SearchFilter.PROGRAMMES,
        results = SearchResults(hasMore = true))

    @Test fun programmeOnlyAndHiddenPrefixPagesCanAdvanceBeyondForty() {
        assertEquals(80, SearchPaging.nextLimit(page, "sic", SearchFilter.PROGRAMMES, 40))
        assertEquals(120, SearchPaging.nextLimit(page.copy(limit = 80), "sic", SearchFilter.PROGRAMMES, 80))
    }

    @Test fun oldQueryFilterAndAnAlreadyRequestedPageCannotRequestAgain() {
        assertNull(SearchPaging.nextLimit(page, "rtp", SearchFilter.PROGRAMMES, 40))
        assertNull(SearchPaging.nextLimit(page, "sic", SearchFilter.TV, 40))
        assertNull(SearchPaging.nextLimit(page, "sic", SearchFilter.PROGRAMMES, 80))
    }

    @Test fun loadingFailedAndExhaustedPagesStopFurtherQueries() {
        assertNull(SearchPaging.nextLimit(page.copy(loading = true), "sic", SearchFilter.PROGRAMMES, 40))
        assertNull(SearchPaging.nextLimit(page.copy(failed = true), "sic", SearchFilter.PROGRAMMES, 40))
        assertNull(SearchPaging.nextLimit(page.copy(results = SearchResults()), "sic", SearchFilter.PROGRAMMES, 40))
    }

    @Test fun lastPageAndOverallBudgetRemainFinite() {
        assertEquals(1_000, SearchPaging.nextLimit(page.copy(limit = 980), "sic", SearchFilter.PROGRAMMES, 980))
        assertNull(SearchPaging.nextLimit(page.copy(limit = 1_000), "sic", SearchFilter.PROGRAMMES, 1_000))
    }
}
