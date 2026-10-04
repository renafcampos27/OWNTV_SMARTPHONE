package tv.own.owntv.core.nav

import org.junit.Assert.*
import org.junit.Test

class LiveCatalogNavigationTest {
    @Test fun staticNavigationKeepsRecordingsWithoutCatalogues() {
        assertTrue(MainSection.DOWNLOADS in MainSection.allBrowse)
        assertTrue(MainSection.LIVE_TV in MainSection.allBrowse)
        assertTrue(MainSection.EPG in MainSection.allBrowse)
        assertFalse(MainSection.MOVIES in MainSection.allBrowse)
        assertFalse(MainSection.SERIES in MainSection.allBrowse)
    }

    @Test fun cachedCataloguesCannotRestoreRemovedDestinations() {
        val visible = MainSection.dynamicVisible(hasLive = true, hasMovies = true, hasSeries = true)
        assertEquals(setOf(MainSection.HOME, MainSection.LIVE_TV, MainSection.DOWNLOADS, MainSection.EPG), visible)
    }

    @Test fun recordingsRemainReachableWithoutAnOnlinePlaylist() {
        assertEquals(setOf(MainSection.HOME, MainSection.DOWNLOADS), MainSection.dynamicVisible(false, false, false))
    }
}
