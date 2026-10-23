package tv.own.owntv.mobile.ui.screens.live

import org.junit.Assert.*
import org.junit.Test

class LiveGroupRepresentativesTest {
    @Test fun representativeSurvivesDroppedPageReload() {
        val groups = LiveGroupRepresentatives()
        assertTrue(groups.accept("sic", 1))
        assertFalse(groups.accept("sic", 2))
        assertTrue(groups.accept("rtp", 3))
        assertTrue(groups.accept("sic", 1))
        assertFalse(groups.accept("sic", 2))
    }
    @Test fun newGenerationMayChooseAnotherVisibleRepresentative() {
        assertTrue(LiveGroupRepresentatives().accept("sic", 2))
        assertTrue(LiveGroupRepresentatives().accept("sic", 1))
    }
}
