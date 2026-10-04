package tv.own.owntv.core.database.entity

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.util.Objects

class EpgProgrammeHashTest {
    private val base = EpgProgrammeEntity(sourceId = 1, epgChannelId = "ch", startMs = 1000, stopMs = 2000, title = "Programme", description = "Description")
    @Test fun noDetailsKeepsLegacyHash() {
        assertEquals(Objects.hash(base.title, base.description, base.stopMs), base.computeContentHash())
    }
    @Test fun changedDetailsAreNotSkippedAsUnchangedProgramme() {
        val old = base.computeContentHash()
        for (row in listOf(base.copy(categories = "News"), base.copy(year = 2026), base.copy(rating = "12"), base.copy(lengthMin = 60), base.copy(episode = "S1E2"))) {
            assertNotEquals(old, row.computeContentHash())
        }
    }
    @Test fun changingOnlyDatabaseIdDoesNotRewriteProgramme() {
        assertEquals(base.computeContentHash(), base.copy(id = 50).computeContentHash())
    }
}
